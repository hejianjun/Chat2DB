package ai.chat2db.spi.redis;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * One bounded page of a Redis key value.
 */
@Data
@Builder
public class RedisKeyValuePage {

    private String name;

    private String type;

    private Long ttl;

    private Long size;

    private List<Map<String, Object>> items;

    private String nextCursor;

    private Boolean hasMore;

    private Long total;

    private Boolean fullyLoaded;
}
