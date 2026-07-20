package ai.chat2db.spi.redis;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface RedisKeyBrowser {

    CompletableFuture<RedisKeyScanResult> streamKeys(String databaseName, String searchKey, String cursor, int count,
                                                     Consumer<List<RedisKeyInfo>> batchConsumer);

    RedisKeyInfo queryKey(String databaseName, String keyName);

    RedisKeyValuePage queryKeyPage(String databaseName, String keyName, String cursor, int count,
                                   int maxStringBytes);

    /**
     * Samples at most one key for each Redis type under a key prefix.
     */
    CompletableFuture<List<RedisKeyInfo>> sampleKeys(String databaseName, String prefix, int maxCount);

    /**
     * Streams every key name for prefix-cache aggregation without reading key values or types.
     */
    CompletableFuture<RedisKeyScanResult> streamPrefixKeys(String databaseName,
                                                            Consumer<List<RedisKeyInfo>> batchConsumer);

    void createKey(String databaseName, String keyName, String keyType, Object value, Long ttl);

    void updateKey(String databaseName, String originalKey, String updateKey, String keyType, Object value, Long ttl);

    void deleteKey(String databaseName, String keyName);

    void partialUpdateKey(String databaseName, RedisKeyMutation mutation);

    void updateKeyMetadata(String databaseName, String keyName, String updateKey, Long ttl);

    /** Returns a SHA-256 fingerprint of TYPE + DUMP, or null when the key does not exist. */
    String fingerprintKey(String databaseName, String keyName);
}
