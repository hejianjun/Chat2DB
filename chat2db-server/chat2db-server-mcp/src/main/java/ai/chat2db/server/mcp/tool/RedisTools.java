package ai.chat2db.server.mcp.tool;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.chat2db.server.domain.api.model.RedisKeyPrefix;
import ai.chat2db.server.domain.api.param.RedisKeyPrefixQueryParam;
import ai.chat2db.server.domain.api.service.RedisKeyPrefixService;
import ai.chat2db.server.mcp.config.McpProperties;
import ai.chat2db.server.mcp.config.McpServerConfigService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.PreparedRedisChangeStore;
import ai.chat2db.server.mcp.support.PreparedRedisChangeStore.PreparedRedisChange;
import ai.chat2db.server.mcp.support.SqlGuard;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.redis.RedisKeyBrowser;
import ai.chat2db.spi.redis.RedisKeyInfo;
import ai.chat2db.spi.redis.RedisKeyMutation;
import ai.chat2db.spi.redis.RedisKeyScanResult;
import ai.chat2db.spi.sql.Chat2DBContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/** MCP tools for bounded Redis browsing and guarded structured changes. */
@Service
public class RedisTools {

    private final ConnectionScope connectionScope;
    private final RedisKeyPrefixService prefixService;
    private final McpServerConfigService configService;
    private final PreparedRedisChangeStore changeStore;
    private final SqlGuard sqlGuard;
    private final ObjectMapper objectMapper;

    public RedisTools(ConnectionScope connectionScope, RedisKeyPrefixService prefixService,
                      McpServerConfigService configService, PreparedRedisChangeStore changeStore,
                      SqlGuard sqlGuard, ObjectMapper objectMapper) {
        this.connectionScope = connectionScope;
        this.prefixService = prefixService;
        this.configService = configService;
        this.changeStore = changeStore;
        this.sqlGuard = sqlGuard;
        this.objectMapper = objectMapper;
    }

    @McpTool(description = "Search Redis keys by exact name or Redis glob pattern. Values are omitted.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String searchRedisKeys(
        @McpToolParam(description = "Datasource resource URI") String datasourceUri,
        @McpToolParam(description = "Redis database number/name") String databaseName,
        @McpToolParam(description = "Exact key or Redis glob", required = false) String pattern,
        @McpToolParam(description = "SCAN cursor, default 0", required = false) String cursor,
        @McpToolParam(description = "Maximum keys", required = false) Integer limit) {
        return connectionScope.run(datasourceUri, databaseName, null, dataSource -> {
            RedisKeyBrowser browser = browser();
            McpProperties.Redis config = configService.getEffectiveRedis();
            int normalized = normalize(limit, config.getDefaultKeyLimit(), config.getMaxKeyLimit());
            List<Map<String, Object>> items = new ArrayList<>();
            RedisKeyScanResult result = browser.streamKeys(databaseName, pattern, cursor, normalized, batch ->
                    batch.forEach(key -> items.add(keyMetadata(key)))).join();
            return json(Map.of("items", items, "nextCursor", result.getCursor(),
                    "hasMore", result.getHasMore(), "total", result.getTotal()));
        });
    }

    @McpTool(description = "Read one bounded page of a Redis STRING, HASH, LIST, SET or ZSET value.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String getRedisKey(
        @McpToolParam(description = "Datasource resource URI") String datasourceUri,
        @McpToolParam(description = "Redis database number/name") String databaseName,
        @McpToolParam(description = "Redis key") String key,
        @McpToolParam(description = "Opaque value cursor, default 0", required = false) String cursor,
        @McpToolParam(description = "Maximum value elements", required = false) Integer limit) {
        return connectionScope.run(datasourceUri, databaseName, null, dataSource -> {
            McpProperties.Redis config = configService.getEffectiveRedis();
            int normalized = normalize(limit, config.getDefaultValuePageSize(), config.getMaxValuePageSize());
            return json(browser().queryKeyPage(databaseName, key, cursor, normalized,
                    config.getMaxStringPageBytes()));
        });
    }

    @McpTool(description = "Search cached Redis key prefixes and their comments.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String searchRedisKeyPrefixes(
        @McpToolParam(description = "Datasource resource URI") String datasourceUri,
        @McpToolParam(description = "Redis database number/name") String databaseName,
        @McpToolParam(description = "Prefix or comment keyword", required = false) String keyword,
        @McpToolParam(description = "Sort field: name or keyCount", required = false) String sortField,
        @McpToolParam(description = "Sort order: ascend or descend", required = false) String sortOrder,
        @McpToolParam(description = "Maximum prefixes", required = false) Integer limit) {
        return connectionScope.run(datasourceUri, databaseName, null, dataSource -> {
            int normalized = normalize(limit, 100, configService.getEffectiveRedis().getMaxKeyLimit());
            List<RedisKeyPrefix> result = prefixService.search(RedisKeyPrefixQueryParam.builder()
                    .dataSourceId(dataSource.getDataSourceId()).databaseName(databaseName).searchKey(keyword)
                    .sortField(sortField).sortOrder(sortOrder).pageSize(normalized).build());
            return json(result);
        });
    }

    @McpTool(description = "Get Redis prefix-cache refresh status.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String getRedisKeyPrefixStatus(
        @McpToolParam(description = "Datasource resource URI") String datasourceUri,
        @McpToolParam(description = "Redis database number/name") String databaseName) {
        return connectionScope.run(datasourceUri, databaseName, null, dataSource ->
                json(prefixService.getStatus(dataSource.getDataSourceId(), databaseName)));
    }

    @McpTool(description = "Refresh the local Redis prefix cache asynchronously. Does not modify Redis.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public String refreshRedisKeyPrefixes(
        @McpToolParam(description = "Datasource resource URI") String datasourceUri,
        @McpToolParam(description = "Redis database number/name") String databaseName) {
        return connectionScope.run(datasourceUri, databaseName, null, dataSource ->
                json(Map.of("statusKey", prefixService.refresh(dataSource.getDataSourceId(), databaseName,
                        Chat2DBContext.getConnectInfo()))));
    }

    @McpTool(description = "Create or update a local comment for a Redis key prefix. Does not modify Redis.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public String updateRedisKeyPrefixComment(
        @McpToolParam(description = "Datasource resource URI") String datasourceUri,
        @McpToolParam(description = "Redis database number/name") String databaseName,
        @McpToolParam(description = "Redis key prefix") String prefix,
        @McpToolParam(description = "Comment text") String comment) {
        return connectionScope.run(datasourceUri, databaseName, null, dataSource -> json(Map.of("sequence",
                prefixService.updateComment(dataSource.getDataSourceId(), databaseName, prefix, comment))));
    }

    @McpTool(description = "Delete one local Redis prefix metadata entry. Does not delete Redis keys.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public String deleteRedisKeyPrefixMetadata(
        @McpToolParam(description = "Datasource resource URI") String datasourceUri,
        @McpToolParam(description = "Redis database number/name") String databaseName,
        @McpToolParam(description = "Redis key prefix") String prefix) {
        return connectionScope.run(datasourceUri, databaseName, null, dataSource -> {
            prefixService.delete(dataSource.getDataSourceId(), databaseName, prefix);
            return json(Map.of("deleted", true));
        });
    }

    @McpTool(description = "DESTRUCTIVE REDIS PREPARE: Prepare one structured Redis key change without executing it.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String prepareRedisKeyChange(
        @McpToolParam(description = "Datasource resource URI") String datasourceUri,
        @McpToolParam(description = "Redis database number/name") String databaseName,
        @McpToolParam(description = "CREATE, REPLACE, PATCH_HASH, PATCH_LIST, PATCH_SET, PATCH_ZSET, RENAME, SET_TTL or DELETE") String operation,
        @McpToolParam(description = "Redis key") String key,
        @McpToolParam(description = "STRING, HASH, LIST, SET or ZSET", required = false) String keyType,
        @McpToolParam(description = "Complete value for CREATE or REPLACE", required = false) Object value,
        @McpToolParam(description = "New key name for RENAME", required = false) String newKey,
        @McpToolParam(description = "HASH fields to upsert", required = false) Map<String, String> hashUpserts,
        @McpToolParam(description = "HASH fields to delete", required = false) List<String> hashDeletes,
        @McpToolParam(description = "LIST existing indexes to replace", required = false) Map<Long, String> listUpdates,
        @McpToolParam(description = "LIST values to append to the tail", required = false) List<String> listAppends,
        @McpToolParam(description = "SET members to add", required = false) List<String> setAdds,
        @McpToolParam(description = "SET members to remove", required = false) List<String> setRemoves,
        @McpToolParam(description = "ZSET member-to-score upserts", required = false) Map<String, Double> zsetUpserts,
        @McpToolParam(description = "ZSET members to remove", required = false) List<String> zsetRemoves,
        @McpToolParam(description = "TTL seconds: null keep current, -1 persist, positive expire", required = false) Long ttlSeconds) {
        return connectionScope.run(datasourceUri, databaseName, null, dataSource -> {
            sqlGuard.requireWriteAllowed(dataSource.getDataSourceId());
            RedisKeyBrowser browser = browser();
            String normalizedOperation = StringUtils.upperCase(StringUtils.trim(operation), Locale.ROOT);
            validateChange(normalizedOperation, key, keyType, value, newKey, ttlSeconds, hashUpserts, hashDeletes,
                    listUpdates, listAppends, setAdds, setRemoves, zsetUpserts, zsetRemoves);
            String fingerprint = browser.fingerprintKey(databaseName, key);
            if ("CREATE".equals(normalizedOperation) && fingerprint != null) {
                throw new IllegalArgumentException("Redis key already exists: " + key);
            }
            if (!"CREATE".equals(normalizedOperation) && fingerprint == null) {
                throw new IllegalArgumentException("Redis key does not exist: " + key);
            }
            PreparedRedisChange change = changeStore.prepare(PreparedRedisChange.builder()
                    .operation(normalizedOperation).datasourceUri(datasourceUri).databaseName(databaseName)
                    .key(key).newKey(newKey).keyType(normalizeType(keyType)).value(value)
                    .hashUpserts(hashUpserts).hashDeletes(hashDeletes).listUpdates(listUpdates)
                    .listAppends(listAppends).setAdds(setAdds).setRemoves(setRemoves)
                    .zsetUpserts(zsetUpserts).zsetRemoves(zsetRemoves).ttlSeconds(ttlSeconds)
                    .fingerprint(fingerprint).target(dataSource.getSummary() + "/" + databaseName + "/" + key)
                    .build());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("operationId", change.getOperationId());
            response.put("operationType", change.getOperation());
            response.put("riskLevel", "DELETE".equals(change.getOperation()) ? "CRITICAL" : "HIGH");
            response.put("target", change.getTarget());
            response.put("digest", "sha256:" + change.getDigest());
            response.put("expiresAtEpochSecond", change.getExpiresAt());
            response.put("confirmationText", change.getConfirmationText());
            return json(response);
        });
    }

    @McpTool(description = "DESTRUCTIVE REDIS EXECUTE: Execute a prepared Redis change using its exact confirmation text.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String executePreparedRedisKeyChange(
        @McpToolParam(description = "operationId returned by prepareRedisKeyChange") String operationId,
        @McpToolParam(description = "Exact confirmationText returned by prepareRedisKeyChange") String confirmationText) {
        PreparedRedisChange change = changeStore.consume(operationId, confirmationText);
        return connectionScope.run(change.getDatasourceUri(), change.getDatabaseName(), null, dataSource -> {
            sqlGuard.requireWriteAllowed(dataSource.getDataSourceId());
            RedisKeyBrowser browser = browser();
            String currentFingerprint = browser.fingerprintKey(change.getDatabaseName(), change.getKey());
            if (!StringUtils.equals(change.getFingerprint(), currentFingerprint)) {
                throw new IllegalStateException("Redis key changed after prepare. Prepare the operation again.");
            }
            execute(browser, change);
            return json(Map.of("success", true, "operation", change.getOperation(), "key", change.getKey()));
        });
    }

    private void execute(RedisKeyBrowser browser, PreparedRedisChange change) {
        switch (change.getOperation()) {
            case "CREATE" -> browser.createKey(change.getDatabaseName(), change.getKey(), change.getKeyType(),
                    change.getValue(), createTtl(change.getTtlSeconds()));
            case "REPLACE" -> {
                Long ttl = change.getTtlSeconds();
                if (ttl == null) {
                    ttl = browser.queryKeyPage(change.getDatabaseName(), change.getKey(), "0", 1,
                            configService.getEffectiveRedis().getMaxStringPageBytes()).getTtl();
                }
                browser.updateKey(change.getDatabaseName(), change.getKey(), change.getKey(), change.getKeyType(),
                        change.getValue(), ttl);
            }
            case "PATCH_HASH", "PATCH_LIST", "PATCH_SET", "PATCH_ZSET" -> browser.partialUpdateKey(change.getDatabaseName(),
                    RedisKeyMutation.builder().keyName(change.getKey()).keyType(change.getKeyType())
                            .hashUpserts(change.getHashUpserts()).hashDeletes(change.getHashDeletes())
                            .listUpdates(change.getListUpdates()).listAppends(change.getListAppends())
                            .setAdds(change.getSetAdds()).setRemoves(change.getSetRemoves())
                            .zsetUpserts(change.getZsetUpserts()).zsetRemoves(change.getZsetRemoves())
                            .ttl(change.getTtlSeconds()).build());
            case "RENAME" -> browser.updateKeyMetadata(change.getDatabaseName(), change.getKey(),
                    change.getNewKey(), null);
            case "SET_TTL" -> browser.updateKeyMetadata(change.getDatabaseName(), change.getKey(), null,
                    change.getTtlSeconds());
            case "DELETE" -> browser.deleteKey(change.getDatabaseName(), change.getKey());
            default -> throw new IllegalStateException("Unsupported Redis operation: " + change.getOperation());
        }
    }

    private void validateChange(String operation, String key, String keyType, Object value, String newKey, Long ttl,
                                Map<String, String> hashUpserts, List<String> hashDeletes,
                                Map<Long, String> listUpdates, List<String> listAppends, List<String> setAdds,
                                List<String> setRemoves, Map<String, Double> zsetUpserts, List<String> zsetRemoves) {
        if (!List.of("CREATE", "REPLACE", "PATCH_HASH", "PATCH_LIST", "PATCH_SET", "PATCH_ZSET", "RENAME",
                "SET_TTL", "DELETE").contains(operation)) {
            throw new IllegalArgumentException("Unsupported Redis operation: " + operation);
        }
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("Redis key must not be blank.");
        }
        if (("CREATE".equals(operation) || "REPLACE".equals(operation)) && (StringUtils.isBlank(keyType) || value == null)) {
            throw new IllegalArgumentException("keyType and value are required for CREATE and REPLACE.");
        }
        if ("RENAME".equals(operation) && StringUtils.isBlank(newKey)) {
            throw new IllegalArgumentException("newKey is required for RENAME.");
        }
        if (ttl != null && ttl == 0) {
            throw new IllegalArgumentException("ttlSeconds must be -1 or positive.");
        }
        if ("SET_TTL".equals(operation) && ttl == null) {
            throw new IllegalArgumentException("ttlSeconds is required for SET_TTL.");
        }
        String type = normalizeType(keyType);
        if (operation.startsWith("PATCH_") && !operation.substring(6).equals(StringUtils.upperCase(type))) {
            throw new IllegalArgumentException("PATCH operation and keyType do not match.");
        }
        if ("PATCH_HASH".equals(operation) && empty(hashUpserts) && empty(hashDeletes)) {
            throw new IllegalArgumentException("PATCH_HASH requires field changes.");
        }
        if ("PATCH_LIST".equals(operation) && empty(listUpdates) && empty(listAppends)) {
            throw new IllegalArgumentException("PATCH_LIST requires index updates or appended values.");
        }
        if ("PATCH_LIST".equals(operation) && listUpdates != null && listUpdates.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getKey() < 0 || entry.getValue() == null)) {
            throw new IllegalArgumentException("PATCH_LIST contains an invalid index or value.");
        }
        if ("PATCH_LIST".equals(operation) && listAppends != null && listAppends.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("PATCH_LIST append values must not be null.");
        }
        if ("PATCH_SET".equals(operation) && empty(setAdds) && empty(setRemoves)) {
            throw new IllegalArgumentException("PATCH_SET requires member changes.");
        }
        if ("PATCH_ZSET".equals(operation) && empty(zsetUpserts) && empty(zsetRemoves)) {
            throw new IllegalArgumentException("PATCH_ZSET requires member changes.");
        }
        McpProperties.Redis config = configService.getEffectiveRedis();
        if (value instanceof Collection<?> collection && collection.size() > config.getMaxFullWriteElements()) {
            throw new IllegalArgumentException("Redis full write exceeds maxFullWriteElements.");
        }
        if (listAppends != null && listAppends.size() > config.getMaxFullWriteElements()) {
            throw new IllegalArgumentException("Redis LIST append exceeds maxFullWriteElements.");
        }
        if (String.valueOf(value).getBytes(StandardCharsets.UTF_8).length > config.getMaxWriteBytes()) {
            throw new IllegalArgumentException("Redis write exceeds maxWriteBytes.");
        }
        if ((String.valueOf(listUpdates) + String.valueOf(listAppends)).getBytes(StandardCharsets.UTF_8).length
                > config.getMaxWriteBytes()) {
            throw new IllegalArgumentException("Redis LIST patch exceeds maxWriteBytes.");
        }
    }

    private RedisKeyBrowser browser() {
        MetaData metadata = Chat2DBContext.getMetaData();
        if (metadata instanceof RedisKeyBrowser browser) {
            return browser;
        }
        throw new IllegalArgumentException("Datasource is not Redis.");
    }

    private int normalize(Integer value, int defaultValue, int maximum) {
        return Math.min(value == null || value <= 0 ? defaultValue : value, maximum);
    }

    private Map<String, Object> keyMetadata(RedisKeyInfo key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", key.getName());
        result.put("type", key.getType());
        result.put("ttl", key.getTtl());
        result.put("size", key.getSize());
        return result;
    }

    private boolean empty(Map<?, ?> value) {
        return value == null || value.isEmpty();
    }

    private boolean empty(List<?> value) {
        return value == null || value.isEmpty();
    }

    private String normalizeType(String value) {
        return StringUtils.lowerCase(StringUtils.trimToEmpty(value), Locale.ROOT);
    }

    private Long createTtl(Long ttl) {
        return ttl == null ? -1L : ttl;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Redis MCP result.", e);
        }
    }
}
