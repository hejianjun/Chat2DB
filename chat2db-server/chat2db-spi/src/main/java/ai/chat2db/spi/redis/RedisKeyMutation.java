package ai.chat2db.spi.redis;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Structured partial mutation for hash, list, set and sorted-set keys.
 */
@Data
@Builder
public class RedisKeyMutation {

    private String keyName;

    private String updateKey;

    private String keyType;

    private Map<String, String> hashUpserts;

    private List<String> hashDeletes;

    /** Existing LIST indexes to replace with LSET. */
    private Map<Long, String> listUpdates;

    /** Values to append to the LIST tail with RPUSH. */
    private List<String> listAppends;

    private List<String> setAdds;

    private List<String> setRemoves;

    private Map<String, Double> zsetUpserts;

    private List<String> zsetRemoves;

    /** Null keeps the current TTL, -1 persists, positive values expire in seconds. */
    private Long ttl;
}
