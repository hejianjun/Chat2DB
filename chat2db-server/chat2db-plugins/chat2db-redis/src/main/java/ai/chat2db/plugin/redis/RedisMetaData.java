package ai.chat2db.plugin.redis;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.redis.RedisCommandMonitor;
import ai.chat2db.spi.redis.RedisKeyBrowser;
import ai.chat2db.spi.redis.RedisKeyInfo;
import ai.chat2db.spi.redis.RedisKeyScanResult;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static ai.chat2db.plugin.redis.RedisConnectionHelper.selectDatabase;

/**
 * Redis 元数据门面类。
 * <p>
 * 整合元数据基础能力（继承自 {@link DefaultMetaService}）、
 * 键浏览服务（{@link RedisKeyBrowserService}）和命令监控服务（{@link RedisCommandMonitorService}），
 * 对外提供统一的 {@link MetaData}、{@link RedisKeyBrowser}、{@link RedisCommandMonitor} 接口。
 */
@Slf4j
public class RedisMetaData extends DefaultMetaService implements MetaData, RedisKeyBrowser, RedisCommandMonitor {

    private static final CommandExecutor COMMAND_EXECUTOR = new RedisCommandExecutor();
    private final RedisKeyBrowserService keyBrowser = new RedisKeyBrowserService();
    private final RedisCommandMonitorService commandMonitor = new RedisCommandMonitorService();

    // ======================== MetaData 基础方法 ========================

    @Override
    public List<Database> databases(Connection connection) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            var commands = context.connection().async();
            Map<String, String> config = commands.configGet("databases").toCompletableFuture().join();
            String count = config.get("databases");
            List<Database> databases = new ArrayList<>();
            if (StringUtils.isNotBlank(count)) {
                for (int i = 0; i < Integer.parseInt(count); i++) {
                    String databaseName = String.valueOf(i);
                    databases.add(Database.builder()
                            .name(databaseName)
                            .count(queryKeyCount(commands, databaseName))
                            .build());
                }
            }
            return databases;
        }
    }

    private Integer queryKeyCount(io.lettuce.core.api.async.RedisAsyncCommands<String, String> commands,
                                  String databaseName) {
        try {
            Long keyCount = selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> commands.dbsize().toCompletableFuture())
                    .join();
            return Math.toIntExact(Math.min(keyCount, Integer.MAX_VALUE));
        } catch (Exception e) {
            log.warn("Query redis database key count failed, databaseName={}", databaseName, e);
            return null;
        }
    }

    @Override
    @SneakyThrows
    public List<Schema> schemas(Connection connection, String databaseName) {
        return List.of();
    }

    @Override
    @SneakyThrows
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        return List.of();
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return COMMAND_EXECUTOR;
    }

    // ======================== RedisKeyBrowser 委托 ========================

    @Override
    public CompletableFuture<RedisKeyScanResult> streamKeys(String databaseName, String searchKey, String cursor,
                                                            int count,
                                                            Consumer<List<RedisKeyInfo>> batchConsumer) {
        return keyBrowser.streamKeys(databaseName, searchKey, cursor, count, batchConsumer);
    }

    @Override
    public RedisKeyInfo queryKey(String databaseName, String keyName) {
        return keyBrowser.queryKey(databaseName, keyName);
    }

    @Override
    public CompletableFuture<List<RedisKeyInfo>> sampleKeys(String databaseName, String prefix, int maxCount) {
        return keyBrowser.sampleKeys(databaseName, prefix, maxCount);
    }

    @Override
    public CompletableFuture<RedisKeyScanResult> streamPrefixKeys(String databaseName,
                                                                    Consumer<List<RedisKeyInfo>> batchConsumer) {
        return keyBrowser.streamPrefixKeys(databaseName, batchConsumer);
    }

    @Override
    public void createKey(String databaseName, String keyName, String keyType, Object value, Long ttl) {
        keyBrowser.createKey(databaseName, keyName, keyType, value, ttl);
    }

    @Override
    public void updateKey(String databaseName, String originalKey, String updateKey, String keyType, Object value,
                          Long ttl) {
        keyBrowser.updateKey(databaseName, originalKey, updateKey, keyType, value, ttl);
    }

    @Override
    public void partialUpdateKey(String databaseName, String keyName, String keyType,
                                  Map<String, String> addedFields, List<String> removedFields, Long ttl) {
        keyBrowser.partialUpdateKey(databaseName, keyName, keyType, addedFields, removedFields, ttl);
    }

    @Override
    public void deleteKey(String databaseName, String keyName) {
        keyBrowser.deleteKey(databaseName, keyName);
    }

    // ======================== RedisCommandMonitor 委托 ========================

    @Override
    public void monitor(String databaseName, Consumer<String> lineConsumer, BooleanSupplier running) {
        commandMonitor.monitor(databaseName, lineConsumer, running);
    }
}
