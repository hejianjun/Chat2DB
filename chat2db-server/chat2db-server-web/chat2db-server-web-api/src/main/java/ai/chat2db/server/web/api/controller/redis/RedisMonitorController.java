package ai.chat2db.server.web.api.controller.redis;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.redis.RedisCommandMonitor;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@RequestMapping("/api/redis/monitor")
@RestController
@ConnectionInfoAspect
public class RedisMonitorController {

    private static final long STREAM_TIMEOUT = 24 * 60 * 60 * 1000L;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(DataSourceBaseRequest request) throws IOException {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT);
        AtomicBoolean running = new AtomicBoolean(true);
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();

        emitter.onCompletion(() -> running.set(false));
        emitter.onTimeout(() -> running.set(false));
        emitter.onError(error -> running.set(false));
        emitter.send(SseEmitter.event()
                .name("connect")
                .data(LocalDateTime.now().toString())
                .reconnectTime(3000));

        CompletableFuture.runAsync(() -> {
            try {
                Chat2DBContext.putContext(connectInfo);
                getRedisCommandMonitor().monitor(request.getDatabaseName(), line -> {
                    if (running.get()) {
                        sendEvent(emitter, "command", Map.of("line", line));
                    }
                }, running::get);
                sendEvent(emitter, "done", Map.of("time", LocalDateTime.now().toString()));
                emitter.complete();
            } catch (Exception e) {
                if (running.get()) {
                    sendEvent(emitter, "redis_error", Map.of("message", e.getMessage()));
                    emitter.completeWithError(e);
                }
            } finally {
                running.set(false);
                Chat2DBContext.removeContext();
            }
        });
        return emitter;
    }

    private RedisCommandMonitor getRedisCommandMonitor() {
        MetaData metaData = Chat2DBContext.getMetaData();
        if (metaData instanceof RedisCommandMonitor monitor) {
            return monitor;
        }
        throw new BusinessException("当前数据源不是 Redis");
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            throw new BusinessException("Redis monitor stream send failed", null, e);
        }
    }
}
