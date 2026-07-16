package ai.chat2db.server.web.api.config;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.ClientHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Function;

/**
 * Logs the exact HTTP payload exchanged with an LLM endpoint.
 * <p>
 * The connector observes data buffers without consuming them, so streaming SSE responses continue to work normally.
 * Enable its DEBUG logger only while diagnosing model-side prompt cache behavior because prompts may contain user data
 * and database schemas.
 */
public class LlmHttpLoggingConnector implements ClientHttpConnector {

    private static final Logger log = LoggerFactory.getLogger(LlmHttpLoggingConnector.class);
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(),
            "x-api-key",
            "api-key");

    private final ClientHttpConnector delegate;

    public LlmHttpLoggingConnector(ClientHttpConnector delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method,
                                            URI uri,
                                            Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
        if (!log.isDebugEnabled()) {
            return delegate.connect(method, uri, requestCallback);
        }
        ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
        return delegate.connect(method, uri, request -> {
            ClientHttpRequest loggingRequest = new ClientHttpRequestDecorator(request) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    return super.writeWith(Flux.from(body).doOnNext(buffer -> copy(buffer, requestBody)))
                            .doOnSuccess(ignored -> logRequest(method, uri, getHeaders(), requestBody));
                }

                @Override
                public Mono<Void> writeAndFlushWith(
                        Publisher<? extends Publisher<? extends DataBuffer>> body) {
                    Flux<Publisher<? extends DataBuffer>> loggingBody = Flux.from(body)
                            .map(publisher -> Flux.from(publisher)
                                    .doOnNext(buffer -> copy(buffer, requestBody)));
                    return super.writeAndFlushWith(loggingBody)
                            .doOnSuccess(ignored -> logRequest(method, uri, getHeaders(), requestBody));
                }
            };
            return requestCallback.apply(loggingRequest);
        }).map(response -> decorateResponse(method, uri, response));
    }

    private ClientHttpResponse decorateResponse(HttpMethod method, URI uri, ClientHttpResponse response) {
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        return new ClientHttpResponseDecorator(response) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody()
                        .doOnNext(buffer -> copy(buffer, responseBody))
                        .doFinally(signal -> log.debug(
                                "[LLM HTTP Response] {} {}\nstatus={}\nheaders={}\nbody={}",
                                method,
                                uri,
                                getStatusCode(),
                                sanitizeHeaders(getHeaders()),
                                responseBody.toString(StandardCharsets.UTF_8)));
            }
        };
    }

    private void logRequest(HttpMethod method,
                            URI uri,
                            HttpHeaders headers,
                            ByteArrayOutputStream body) {
        log.debug("[LLM HTTP Request] {} {}\nheaders={}\nbody={}",
                method,
                uri,
                sanitizeHeaders(headers),
                body.toString(StandardCharsets.UTF_8));
    }

    private HttpHeaders sanitizeHeaders(HttpHeaders source) {
        HttpHeaders sanitized = new HttpHeaders();
        source.forEach((name, values) -> {
            if (SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                sanitized.add(name, "***");
            } else {
                sanitized.addAll(name, values);
            }
        });
        return sanitized;
    }

    private void copy(DataBuffer dataBuffer, ByteArrayOutputStream target) {
        ByteBuffer byteBuffer = dataBuffer.asByteBuffer().asReadOnlyBuffer();
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        target.writeBytes(bytes);
    }
}
