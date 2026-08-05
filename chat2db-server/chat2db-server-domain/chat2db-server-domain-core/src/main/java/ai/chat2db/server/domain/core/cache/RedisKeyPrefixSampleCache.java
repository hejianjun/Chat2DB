package ai.chat2db.server.domain.core.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import ai.chat2db.server.domain.api.model.RedisKeyPrefixSample;
import ai.chat2db.spi.redis.RedisKeyInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Redis key 前缀样例的持久化缓存。
 */
@Component
public class RedisKeyPrefixSampleCache {

    @Autowired
    private LuceneIndexManagerFactory managerFactory;

    private final Map<SampleCacheKey, CompletableFuture<List<RedisKeyInfo>>> inFlightLoads =
            new ConcurrentHashMap<>();

    public List<RedisKeyInfo> get(Long dataSourceId, String databaseName, String prefix, boolean refresh,
                                  Supplier<List<RedisKeyInfo>> loader) {
        SampleCacheKey key = buildKey(dataSourceId, databaseName, prefix);
        if (!refresh) {
            List<RedisKeyInfo> cached = find(key);
            if (cached != null) {
                return cached;
            }
        }

        CompletableFuture<List<RedisKeyInfo>> loading = new CompletableFuture<>();
        CompletableFuture<List<RedisKeyInfo>> existing = inFlightLoads.putIfAbsent(key, loading);
        if (existing != null) {
            return join(existing);
        }

        try {
            if (!refresh) {
                List<RedisKeyInfo> cached = find(key);
                if (cached != null) {
                    loading.complete(cached);
                    return cached;
                }
            }
            List<RedisKeyInfo> samples = List.copyOf(loader.get());
            save(key, samples);
            loading.complete(samples);
            return samples;
        } catch (RuntimeException e) {
            loading.completeExceptionally(e);
            throw e;
        } finally {
            inFlightLoads.remove(key, loading);
        }
    }

    public void invalidateDatabase(Long dataSourceId, String databaseName) {
        RedisKeyPrefixSample query = RedisKeyPrefixSample.builder()
                .dataSourceId(dataSourceId)
                .databaseName(normalizeDatabaseName(databaseName))
                .build();
        managerFactory.<RedisKeyPrefixSample>getManager(dataSourceId).deleteByDatabaseAndSchema(query);
    }

    /**
     * 批量读取当前库已有样例缓存中的类型，不触发 Redis 扫描。
     */
    public Map<String, String> findSampleTypes(Long dataSourceId, String databaseName, Set<String> prefixes) {
        if (prefixes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        findAll(dataSourceId, normalizeDatabaseName(databaseName)).stream()
                .filter(sample -> prefixes.contains(sample.getPrefix()))
                .forEach(sample -> {
                    TreeSet<String> types = StringUtils.isBlank(sample.getPrefix()) || sample.getSamples() == null
                            ? new TreeSet<>() : sample.getSamples().stream()
                            .map(RedisKeyInfo::getType)
                            .filter(StringUtils::isNotBlank)
                            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
                    if (!types.isEmpty()) {
                        result.put(sample.getPrefix(), String.join(",", types));
                    }
                });
        return result;
    }

    protected List<RedisKeyPrefixSample> findAll(Long dataSourceId, String databaseName) {
        RedisKeyPrefixSample query = RedisKeyPrefixSample.builder()
                .dataSourceId(dataSourceId)
                .databaseName(databaseName)
                .build();
        return managerFactory.<RedisKeyPrefixSample>getManager(dataSourceId).searchAll(query, "");
    }

    protected List<RedisKeyInfo> find(SampleCacheKey key) {
        RedisKeyPrefixSample query = RedisKeyPrefixSample.builder()
                .dataSourceId(key.dataSourceId())
                .databaseName(key.databaseName())
                .prefixHash(key.prefixHash())
                .build();
        return managerFactory.<RedisKeyPrefixSample>getManager(key.dataSourceId()).searchAll(query, "").stream()
                .filter(sample -> key.prefix().equals(sample.getPrefix()))
                .map(RedisKeyPrefixSample::getSamples)
                .findFirst()
                .orElse(null);
    }

    protected void save(SampleCacheKey key, List<RedisKeyInfo> samples) {
        RedisKeyPrefixSample document = RedisKeyPrefixSample.builder()
                .name(key.prefixHash())
                .dataSourceId(key.dataSourceId())
                .databaseName(key.databaseName())
                .prefixHash(key.prefixHash())
                .prefix(key.prefix())
                .samples(samples)
                .build();
        managerFactory.<RedisKeyPrefixSample>getManager(key.dataSourceId()).updateDocument(document);
    }

    private List<RedisKeyInfo> join(CompletableFuture<List<RedisKeyInfo>> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private SampleCacheKey buildKey(Long dataSourceId, String databaseName, String prefix) {
        return new SampleCacheKey(dataSourceId, normalizeDatabaseName(databaseName), prefix, sha256(prefix));
    }

    private String normalizeDatabaseName(String databaseName) {
        return StringUtils.defaultString(databaseName);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    protected record SampleCacheKey(Long dataSourceId, String databaseName, String prefix, String prefixHash) {
    }
}
