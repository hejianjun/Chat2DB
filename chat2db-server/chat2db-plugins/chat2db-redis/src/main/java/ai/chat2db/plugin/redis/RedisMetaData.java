package ai.chat2db.plugin.redis;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.redis.RedisKeyBrowser;
import ai.chat2db.spi.redis.RedisKeyInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import com.google.common.collect.Lists;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
public class RedisMetaData extends DefaultMetaService implements MetaData, RedisKeyBrowser {

    private static final long SCAN_COUNT = 1000L;
    private static final int VALUE_PREVIEW_LIMIT = 5;
    private static final int VALUE_TEXT_LIMIT = 200;
    private static final CommandExecutor COMMAND_EXECUTOR = new RedisCommandExecutor();

    @Override
    public List<Database> databases(Connection connection) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisCommands<String, String> commands = context.connection().sync();
            Map<String, String> config = commands.configGet("databases");
            String count = config.get("databases");
            List<Database> databases = new ArrayList<>();
            if (StringUtils.isNotBlank(count)) {
                for (int i = 0; i < Integer.parseInt(count); i++) {
                    databases.add(Database.builder().name(String.valueOf(i)).build());
                }
            }
            return databases;
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


    @Override
    public void streamKeys(String databaseName, String searchKey, int count, int batchSize,
                           Consumer<List<RedisKeyInfo>> batchConsumer) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            selectDatabase(commands, databaseName).join();
            String pattern = StringUtils.isBlank(searchKey) ? null : "*" + searchKey + "*";
            scanKeyInfo(commands, pattern, count <= 0 ? SCAN_COUNT : count,
                    batchSize <= 0 ? 200 : batchSize, batchConsumer);
        }
    }

    @Override
    public RedisKeyInfo queryKey(String databaseName, String keyName) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisCommands<String, String> commands = context.connection().sync();
            selectDatabase(commands, databaseName);
            return buildKeyInfo(commands, keyName);
        }
    }

    private void scanKeyInfo(RedisAsyncCommands<String, String> commands, String pattern, long count, int batchSize,
                             Consumer<List<RedisKeyInfo>> batchConsumer) {
        List<CompletableFuture<RedisKeyInfo>> batch = new ArrayList<>(batchSize);
        long emitted = 0;
        ScanArgs scanArgs = new ScanArgs().limit(SCAN_COUNT);
        if (StringUtils.isNotBlank(pattern)) {
            scanArgs.match(pattern);
        }
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> result = commands.scan(cursor, scanArgs).toCompletableFuture().join();
            for (String key : result.getKeys()) {
                batch.add(buildKeyInfo(commands, key));
                emitted++;
                if (batch.size() >= batchSize) {
                    batchConsumer.accept(resolveBatch(batch));
                    batch.clear();
                }
                if (emitted >= count) {
                    if (!batch.isEmpty()) {
                        batchConsumer.accept(resolveBatch(batch));
                    }
                    return;
                }
            }
            cursor = ScanCursor.of(result.getCursor());
            cursor.setFinished(result.isFinished());
        } while (!cursor.isFinished());
        if (!batch.isEmpty()) {
            batchConsumer.accept(resolveBatch(batch));
        }
    }

    private RedisKeyInfo buildKeyInfo(RedisCommands<String, String> commands, String key) {
        String type = getType(commands, key);
        return RedisKeyInfo.builder()
                .name(key)
                .type(type)
                .value(previewValue(commands, key, type))
                .ttl(getTtl(commands, key))
                .size(getSize(commands, key))
                .build();
    }

    private CompletableFuture<RedisKeyInfo> buildKeyInfo(RedisAsyncCommands<String, String> commands, String key) {
        return commands.type(key).toCompletableFuture()
                .thenCompose(type -> {
                    CompletableFuture<Object> value = previewValue(commands, key, type);
                    CompletableFuture<Long> ttl = commands.ttl(key).toCompletableFuture()
                            .exceptionally(e -> null);
                    CompletableFuture<Long> size = commands.memoryUsage(key).toCompletableFuture()
                            .exceptionally(e -> null);
                    return CompletableFuture.allOf(value, ttl, size)
                            .thenApply(ignored -> RedisKeyInfo.builder()
                                    .name(key)
                                    .type(type)
                                    .value(value.join())
                                    .ttl(ttl.join())
                                    .size(size.join())
                                    .build());
                })
                .exceptionally(e -> RedisKeyInfo.builder()
                        .name(key)
                        .type("unknown")
                        .value("")
                        .build());
    }

    private CompletableFuture<Object> previewValue(RedisAsyncCommands<String, String> commands, String key,
                                                   String type) {
        try {
            return switch (StringUtils.defaultString(type).toLowerCase()) {
                case "string" -> commands.get(key).toCompletableFuture().thenApply(this::abbreviate);
                case "hash" -> commands.hgetall(key).toCompletableFuture().thenApply(this::previewMap);
                case "list" -> commands.lrange(key, 0, VALUE_PREVIEW_LIMIT - 1).toCompletableFuture()
                        .thenApply(this::previewList);
                case "set" -> commands.srandmember(key, VALUE_PREVIEW_LIMIT).toCompletableFuture()
                        .thenApply(this::previewList);
                case "zset" -> commands.zrange(key, 0, VALUE_PREVIEW_LIMIT - 1).toCompletableFuture()
                        .thenApply(this::previewList);
                default -> CompletableFuture.completedFuture("");
            };
        } catch (Exception e) {
            return CompletableFuture.completedFuture("");
        }
    }

    private List<RedisKeyInfo> resolveBatch(List<CompletableFuture<RedisKeyInfo>> batch) {
        CompletableFuture.allOf(batch.toArray(new CompletableFuture[0])).join();
        return batch.stream().map(CompletableFuture::join).toList();
    }

    private Object previewValue(RedisCommands<String, String> commands, String key, String type) {
        try {
            return switch (StringUtils.defaultString(type).toLowerCase()) {
                case "string" -> abbreviate(commands.get(key));
                case "hash" -> previewMap(commands.hgetall(key));
                case "list" -> previewList(commands.lrange(key, 0, VALUE_PREVIEW_LIMIT - 1));
                case "set" -> previewList(commands.srandmember(key, VALUE_PREVIEW_LIMIT));
                case "zset" -> previewList(commands.zrange(key, 0, VALUE_PREVIEW_LIMIT - 1));
                default -> "";
            };
        } catch (Exception e) {
            log.warn("Redis key value preview failed, key={}", key, e);
            return "";
        }
    }

    private Long getTtl(RedisCommands<String, String> commands, String key) {
        try {
            return commands.ttl(key);
        } catch (Exception e) {
            log.warn("Redis key ttl failed, key={}", key, e);
            return null;
        }
    }

    private Long getSize(RedisCommands<String, String> commands, String key) {
        try {
            return commands.memoryUsage(key);
        } catch (Exception e) {
            return null;
        }
    }

    private String previewMap(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner(", ", "[", values.size() > VALUE_PREVIEW_LIMIT ? ", ...]" : "]");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (index++ >= VALUE_PREVIEW_LIMIT) {
                break;
            }
            joiner.add(entry.getKey() + ":" + abbreviate(entry.getValue()));
        }
        return joiner.toString();
    }

    private String previewList(List<String> values) {
        StringJoiner joiner = new StringJoiner(", ", "[", values.size() > VALUE_PREVIEW_LIMIT ? ", ...]" : "]");
        int limit = Math.min(values.size(), VALUE_PREVIEW_LIMIT);
        for (int i = 0; i < limit; i++) {
            joiner.add(abbreviate(values.get(i)));
        }
        return joiner.toString();
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= VALUE_TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, VALUE_TEXT_LIMIT) + "...";
    }

    private void selectDatabase(RedisCommands<String, String> commands, String databaseName) {
        if (StringUtils.isNotBlank(databaseName)) {
            commands.select(Integer.parseInt(databaseName));
        }
    }

    private CompletableFuture<Void> selectDatabase(RedisAsyncCommands<String, String> commands, String databaseName) {
        if (StringUtils.isBlank(databaseName)) {
            return CompletableFuture.completedFuture(null);
        }
        return commands.select(Integer.parseInt(databaseName)).toCompletableFuture().thenApply(ignored -> null);
    }

    private String getType(RedisCommands<String, String> commands, String tableName) {
        try {
            String type = commands.type(tableName);
            if (StringUtils.isNotBlank(type)) {
                return type;
            }
        } catch (Exception e) {
            log.error("type获取失败", e);
        }
        return "string";
    }
}
