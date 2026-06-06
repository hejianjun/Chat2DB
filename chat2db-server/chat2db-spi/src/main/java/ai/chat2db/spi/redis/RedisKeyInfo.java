package ai.chat2db.spi.redis;

import lombok.Builder;
import lombok.Data;

/**
 * Redis键信息
 *
 * @author chat2db
 */
@Data
@Builder
public class RedisKeyInfo {

    /**
     * 键名称
     */
    private String name;

    /**
     * 键值
     */
    private Object value;

    /**
     * 数据类型（如 string、hash、list、set、zset）
     */
    private String type;

    /**
     * 过期时间（单位：秒），-1 表示永不过期，-2 表示已过期
     */
    private Long ttl;

    /**
     * 键值大小（单位：字节）
     */
    private Long size;
}
