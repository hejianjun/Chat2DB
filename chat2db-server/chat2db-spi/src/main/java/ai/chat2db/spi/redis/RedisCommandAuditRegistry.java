package ai.chat2db.spi.redis;

/**
 * Global registry for Redis command audit events.
 */
public final class RedisCommandAuditRegistry {

    private static final RedisCommandAuditSink NOOP = event -> {
    };

    private static volatile RedisCommandAuditSink sink = NOOP;

    private RedisCommandAuditRegistry() {
    }

    public static void register(RedisCommandAuditSink auditSink) {
        sink = auditSink == null ? NOOP : auditSink;
    }

    public static void publish(RedisCommandAuditEvent event) {
        sink.accept(event);
    }
}
