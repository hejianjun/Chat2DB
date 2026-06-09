package ai.chat2db.spi.redis;

import lombok.Builder;
import lombok.Data;

/**
 * Redis command audit event emitted from the Redis client layer.
 */
@Data
@Builder
public class RedisCommandAuditEvent {

    private Long dataSourceId;

    private Long userId;

    private String databaseName;

    private String type;

    private String command;

    private String status;

    private Long useTime;

    private Long operationRows;

    private String extendInfo;
}
