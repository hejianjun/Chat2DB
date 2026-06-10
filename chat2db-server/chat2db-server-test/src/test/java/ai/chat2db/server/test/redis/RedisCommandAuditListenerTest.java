package ai.chat2db.server.test.redis;

import ai.chat2db.plugin.redis.RedisCommandAuditListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RedisCommandAuditListenerTest {

    @Test
    void shouldAuditWriteCommand() {
        Assertions.assertTrue(RedisCommandAuditListener.shouldAudit("SET"));
        Assertions.assertTrue(RedisCommandAuditListener.shouldAudit("del"));
        Assertions.assertTrue(RedisCommandAuditListener.shouldAudit("HSET"));
        Assertions.assertTrue(RedisCommandAuditListener.shouldAudit("EVAL"));
        Assertions.assertTrue(RedisCommandAuditListener.shouldAudit("evalsha"));
        Assertions.assertTrue(RedisCommandAuditListener.shouldAudit("SCRIPT"));
    }

    @Test
    void shouldSkipReadAndInternalCommand() {
        Assertions.assertFalse(RedisCommandAuditListener.shouldAudit("SCAN"));
        Assertions.assertFalse(RedisCommandAuditListener.shouldAudit("GET"));
        Assertions.assertFalse(RedisCommandAuditListener.shouldAudit("TYPE"));
        Assertions.assertFalse(RedisCommandAuditListener.shouldAudit("TTL"));
        Assertions.assertFalse(RedisCommandAuditListener.shouldAudit("SELECT"));
        Assertions.assertFalse(RedisCommandAuditListener.shouldAudit("EXISTS"));
    }
}
