package ai.chat2db.server.web.api.config;

import ai.chat2db.server.tools.common.util.LogUtils;
import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Hooks;

@Component
public class ReactorContextConfig {

    @PostConstruct
    public void init() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                LogUtils.TRACE_ID,
                () -> MDC.get(LogUtils.TRACE_ID),
                traceId -> {
                    MDC.put(LogUtils.TRACE_ID, traceId);
                    LogUtils.setTraceId(traceId);
                },
                () -> {
                    MDC.remove(LogUtils.TRACE_ID);
                    LogUtils.removeTraceId();
                });
        Hooks.enableAutomaticContextPropagation();
    }
}
