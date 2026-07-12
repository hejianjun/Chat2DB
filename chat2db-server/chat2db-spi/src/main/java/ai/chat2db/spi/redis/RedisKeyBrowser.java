package ai.chat2db.spi.redis;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface RedisKeyBrowser {

    CompletableFuture<RedisKeyScanResult> streamKeys(String databaseName, String searchKey, String cursor, int count,
                                                     Consumer<List<RedisKeyInfo>> batchConsumer);

    default CompletableFuture<RedisKeyScanResult> streamKeyNames(String databaseName, String searchKey, String cursor,
                                                                 int count, Consumer<List<String>> batchConsumer) {
        return streamKeys(databaseName, searchKey, cursor, count,
                batch -> batchConsumer.accept(batch.stream().map(RedisKeyInfo::getName).toList()));
    }

    RedisKeyInfo queryKey(String databaseName, String keyName);

    /**
     * Samples at most one key for each Redis type under a key prefix.
     */
    default CompletableFuture<List<RedisKeyInfo>> sampleKeys(String databaseName, String prefix, int maxCount) {
        Map<String, RedisKeyInfo> samples = new LinkedHashMap<>();
        String pattern = prefix + "*";
        return streamKeys(databaseName, pattern, "0", maxCount, batch -> {
            synchronized (samples) {
                batch.forEach(key -> samples.putIfAbsent(key.getType(), key));
            }
        }).thenApply(ignored -> {
            synchronized (samples) {
                return List.copyOf(samples.values());
            }
        });
    }

    /**
     * Streams key names with their Redis types, without requiring values for callers that only need type metadata.
     */
    default CompletableFuture<RedisKeyScanResult> streamKeyTypes(String databaseName, String searchKey, String cursor,
                                                                 int count, Consumer<List<RedisKeyInfo>> batchConsumer) {
        return streamKeys(databaseName, searchKey, cursor, count, batchConsumer);
    }

    void createKey(String databaseName, String keyName, String keyType, Object value, Long ttl);

    void updateKey(String databaseName, String originalKey, String updateKey, String keyType, Object value, Long ttl);

    void deleteKey(String databaseName, String keyName);

    void partialUpdateKey(String databaseName, String keyName, String keyType,
                          Map<String, String> addedFields, List<String> removedFields, Long ttl);
}
