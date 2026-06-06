package ai.chat2db.spi.redis;

import java.util.List;
import java.util.function.Consumer;

public interface RedisKeyBrowser {

    void streamKeys(String databaseName, String searchKey, int count, int batchSize,
                    Consumer<List<RedisKeyInfo>> batchConsumer);

    RedisKeyInfo queryKey(String databaseName, String keyName);

    void updateKey(String databaseName, String originalKey, String updateKey, String keyType, Object value, Long ttl);
}
