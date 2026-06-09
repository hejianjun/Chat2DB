package ai.chat2db.spi.redis;

/**
 * Sink used by Redis plugins to publish command audit events without depending on server services.
 */
@FunctionalInterface
public interface RedisCommandAuditSink {

    void accept(RedisCommandAuditEvent event);
}
