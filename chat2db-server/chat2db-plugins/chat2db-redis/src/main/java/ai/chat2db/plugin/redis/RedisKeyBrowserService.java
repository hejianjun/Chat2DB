package ai.chat2db.plugin.redis;

import ai.chat2db.spi.redis.RedisKeyBrowser;
import ai.chat2db.spi.redis.RedisKeyInfo;
import ai.chat2db.spi.redis.RedisKeyScanResult;
import ai.chat2db.spi.sql.Chat2DBContext;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static ai.chat2db.plugin.redis.RedisConnectionHelper.*;

/**
 * Redis 键浏览与 CRUD 服务。
 * <p>
 * 负责：
 * - 流式扫描 Redis 键并按批次返回
 * - 查询、创建、更新、删除 Redis 键
 * - 键值的预览和完整读取
 * - 按类型写入值
 */
@Slf4j
class RedisKeyBrowserService implements RedisKeyBrowser {

    private static final Set<String> SAMPLE_KEY_TYPES = Set.of("string", "hash", "list", "set", "zset");


    @Override
    public CompletableFuture<RedisKeyScanResult> streamKeys(String databaseName, String searchKey, String cursor,
                                                            int count,
                                                            Consumer<List<RedisKeyInfo>> batchConsumer) {
        try {
            RedisConnectionProvider.RedisConnectionContext context =
                    RedisConnectionProvider.open(Chat2DBContext.getConnectInfo());
            RedisAsyncCommands<String, String> commands = context.connection().async();
            AtomicBoolean closed = new AtomicBoolean(false);
            CompletableFuture<RedisKeyScanResult> operation = selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> {
                        String pattern = StringUtils.trimToNull(searchKey);
                        if (isInitialCursor(cursor) && isExactKeyPattern(pattern)) {
                            return queryExactKeyAsync(commands, pattern, batchConsumer);
                        }
                        // count < 0 means fetch all keys for the Redis data page.
                        long limit = count < 0 ? Long.MAX_VALUE : count == 0 ? KEY_SCAN_BATCH_SIZE : count;
                        return scanKeyInfoAsync(commands, pattern, cursor, limit, batchConsumer);
                    });
            CompletableFuture<RedisKeyScanResult> result = new CompletableFuture<>();
            operation.whenComplete((scanResult, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    result.complete(scanResult);
                }
                closeContextAsync(context, closed);
            });
            result.whenComplete((ignored, throwable) -> {
                if (result.isCancelled()) {
                    operation.cancel(true);
                    closeContextAsync(context, closed);
                }
            });
            return result;
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<RedisKeyScanResult> streamKeyNames(String databaseName, String searchKey, String cursor,
                                                                int count, Consumer<List<String>> batchConsumer) {
        try {
            RedisConnectionProvider.RedisConnectionContext context =
                    RedisConnectionProvider.open(Chat2DBContext.getConnectInfo());
            RedisAsyncCommands<String, String> commands = context.connection().async();
            AtomicBoolean closed = new AtomicBoolean(false);
            CompletableFuture<RedisKeyScanResult> operation = selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> {
                        String pattern = StringUtils.trimToNull(searchKey);
                        long limit = count < 0 ? Long.MAX_VALUE : count == 0 ? KEY_SCAN_BATCH_SIZE : count;
                        return scanKeyNamesAsync(commands, pattern, cursor, limit, batchConsumer);
                    });
            CompletableFuture<RedisKeyScanResult> result = new CompletableFuture<>();
            operation.whenComplete((scanResult, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    result.complete(scanResult);
                }
                closeContextAsync(context, closed);
            });
            result.whenComplete((ignored, throwable) -> {
                if (result.isCancelled()) {
                    operation.cancel(true);
                    closeContextAsync(context, closed);
                }
            });
            return result;
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public RedisKeyInfo queryKey(String databaseName, String keyName) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            selectDatabase(commands, databaseName).join();
            return buildKeyInfo(commands, keyName, true).join();
        }
    }

    @Override
    public void createKey(String databaseName, String keyName, String keyType, Object value, Long ttl) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            if (StringUtils.isBlank(keyName)) {
                throw new IllegalArgumentException("Redis key 不能为空");
            }
            selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> commands.exists(keyName).toCompletableFuture())
                    .thenApply(exists -> {
                        if (exists > 0) {
                            throw new IllegalArgumentException("Redis key 已存在: " + keyName);
                        }
                        return null;
                    })
                    .thenCompose(ignored -> writeValue(commands, keyName, keyType, value))
                    .thenCompose(ignored -> commands.exists(keyName).toCompletableFuture())
                    .thenApply(exists -> {
                        if (exists == 0) {
                            throw new IllegalArgumentException("Redis key value 不能为空");
                        }
                        return null;
                    })
                    .thenCompose(ignored -> applyTtl(commands, keyName, ttl))
                    .join();
        }
    }

    @Override
    public void updateKey(String databaseName, String originalKey, String updateKey, String keyType, Object value,
                          Long ttl) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            String targetKey = StringUtils.defaultIfBlank(updateKey, originalKey);
            selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> {
                        if (!StringUtils.equals(originalKey, targetKey)) {
                            return commands.rename(originalKey, targetKey).toCompletableFuture();
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .thenCompose(ignored -> commands.del(targetKey).toCompletableFuture())
                    .thenCompose(ignored -> writeValue(commands, targetKey, keyType, value))
                    .thenCompose(ignored -> applyTtl(commands, targetKey, ttl))
                    .join();
        }
    }

    @Override
    public void partialUpdateKey(String databaseName, String keyName, String keyType,
                                  Map<String, String> addedFields, List<String> removedFields, Long ttl) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> {
                        if (addedFields != null && !addedFields.isEmpty()) {
                            return commands.hset(keyName, addedFields).toCompletableFuture();
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .thenCompose(ignored -> {
                        if (removedFields != null && !removedFields.isEmpty()) {
                            return commands.hdel(keyName, removedFields.toArray(new String[0])).toCompletableFuture();
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .thenCompose(ignored -> applyTtl(commands, keyName, ttl))
                    .join();
        }
    }

    @Override
    public void deleteKey(String databaseName, String keyName) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> {
                        if (StringUtils.isNotBlank(keyName)) {
                            return commands.del(keyName).toCompletableFuture();
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .join();
        }
    }

    // ======================== SCAN 相关私有方法 ========================

    private CompletableFuture<RedisKeyScanResult> scanKeyInfoAsync(RedisAsyncCommands<String, String> commands,
                                                                   String pattern, String cursor, long count,
                                                                   Consumer<List<RedisKeyInfo>> batchConsumer) {
        RedisKeyInfoBatchEmitter emitter = new RedisKeyInfoBatchEmitter(batchConsumer);
        return scanKeyInfoAsync(commands, pattern, buildScanCursor(cursor), count, 0, KEY_SCAN_BATCH_SIZE, emitter);
    }

    @Override
    public CompletableFuture<RedisKeyScanResult> streamKeyTypes(String databaseName, String searchKey, String cursor,
                                                                int count, Consumer<List<RedisKeyInfo>> batchConsumer) {
        try {
            RedisConnectionProvider.RedisConnectionContext context =
                    RedisConnectionProvider.open(Chat2DBContext.getConnectInfo());
            RedisAsyncCommands<String, String> commands = context.connection().async();
            AtomicBoolean closed = new AtomicBoolean(false);
            CompletableFuture<RedisKeyScanResult> operation = selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> {
                        String pattern = StringUtils.trimToNull(searchKey);
                        long limit = count < 0 ? Long.MAX_VALUE : count == 0 ? KEY_SCAN_BATCH_SIZE : count;
                        return scanKeyTypesAsync(commands, pattern, buildScanCursor(cursor), limit, 0, batchConsumer);
                    });
            CompletableFuture<RedisKeyScanResult> result = new CompletableFuture<>();
            operation.whenComplete((scanResult, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    result.complete(scanResult);
                }
                closeContextAsync(context, closed);
            });
            result.whenComplete((ignored, throwable) -> {
                if (result.isCancelled()) {
                    operation.cancel(true);
                    closeContextAsync(context, closed);
                }
            });
            return result;
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<List<RedisKeyInfo>> sampleKeys(String databaseName, String prefix, int maxCount) {
        try {
            RedisConnectionProvider.RedisConnectionContext context =
                    RedisConnectionProvider.open(Chat2DBContext.getConnectInfo());
            RedisAsyncCommands<String, String> commands = context.connection().async();
            AtomicBoolean closed = new AtomicBoolean(false);
            Map<String, RedisKeyInfo> samples = new LinkedHashMap<>();
            int limit = Math.max(1, maxCount);
            CompletableFuture<List<RedisKeyInfo>> operation = selectDatabase(commands, databaseName)
                    .thenCompose(ignored -> sampleKeyInfoAsync(commands, escapeScanPattern(prefix) + "*",
                            ScanCursor.INITIAL, limit, 0, samples));
            operation.whenComplete((ignored, throwable) -> closeContextAsync(context, closed));
            return operation;
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<RedisKeyScanResult> scanKeyInfoAsync(RedisAsyncCommands<String, String> commands,
                                                                   String pattern, ScanCursor scanCursor, long count,
                                                                   long emitted, int scanCount,
                                                                   RedisKeyInfoBatchEmitter emitter) {
        if (emitted >= count) {
            return emitter.finish().thenApply(ignored -> buildScanResult(scanCursor, emitted));
        }
        ScanArgs scanArgs = buildScanArgs(pattern, scanCount);
        return commands.scan(scanCursor, scanArgs).toCompletableFuture()
                .thenCompose(result -> {
                    List<String> keys = result.getKeys();
                    long nextEmitted = emitted + keys.size();
                    int nextScanCount = nextScanCount(scanCount, keys.isEmpty());
                    ScanCursor nextCursor = ScanCursor.of(result.getCursor());
                    nextCursor.setFinished(result.isFinished());
                    boolean shouldStop = result.isFinished() || nextEmitted >= count;
                    return emitKeyInfoAsync(commands, keys, emitter)
                            .thenCompose(ignored -> {
                                if (shouldStop) {
                                    return emitter.finish()
                                            .thenApply(unused -> buildScanResult(nextCursor, nextEmitted));
                                }
                                return scanKeyInfoAsync(commands, pattern, nextCursor, count, nextEmitted,
                                        nextScanCount, emitter);
                            });
                });
    }

    private CompletableFuture<List<RedisKeyInfo>> sampleKeyInfoAsync(RedisAsyncCommands<String, String> commands,
                                                                       String pattern, ScanCursor cursor, int limit,
                                                                       int scanned,
                                                                       Map<String, RedisKeyInfo> samples) {
        if (scanned >= limit || samples.keySet().containsAll(SAMPLE_KEY_TYPES)) {
            return CompletableFuture.completedFuture(List.copyOf(samples.values()));
        }
        int remaining = limit - scanned;
        ScanArgs scanArgs = buildScanArgs(pattern, Math.min(KEY_SCAN_BATCH_SIZE, remaining));
        return commands.scan(cursor, scanArgs).toCompletableFuture().thenCompose(result -> {
            List<String> keys = result.getKeys().stream().limit(remaining).toList();
            int nextScanned = scanned + keys.size();
            ScanCursor nextCursor = ScanCursor.of(result.getCursor());
            nextCursor.setFinished(result.isFinished());
            return buildKeyInfoBatch(commands, keys, false).thenCompose(futures ->
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
                        futures.forEach(future -> {
                            RedisKeyInfo key = future.join();
                            samples.putIfAbsent(key.getType(), key);
                        });
                        if (nextCursor.isFinished() || nextScanned >= limit
                                || samples.keySet().containsAll(SAMPLE_KEY_TYPES)) {
                            return CompletableFuture.completedFuture(List.copyOf(samples.values()));
                        }
                        return sampleKeyInfoAsync(commands, pattern, nextCursor, limit, nextScanned, samples);
                    }));
        });
    }

    private CompletableFuture<RedisKeyScanResult> scanKeyNamesAsync(RedisAsyncCommands<String, String> commands,
                                                                    String pattern, String cursor, long count,
                                                                    Consumer<List<String>> batchConsumer) {
        return scanKeyNamesAsync(commands, pattern, buildScanCursor(cursor), count, 0, KEY_SCAN_BATCH_SIZE,
                batchConsumer);
    }

    private CompletableFuture<RedisKeyScanResult> scanKeyTypesAsync(RedisAsyncCommands<String, String> commands,
                                                                    String pattern, ScanCursor scanCursor, long count,
                                                                    long emitted,
                                                                    Consumer<List<RedisKeyInfo>> batchConsumer) {
        if (emitted >= count) {
            return CompletableFuture.completedFuture(buildScanResult(scanCursor, emitted));
        }
        ScanArgs scanArgs = buildScanArgs(pattern, KEY_SCAN_BATCH_SIZE);
        return commands.scan(scanCursor, scanArgs).toCompletableFuture().thenCompose(result -> {
            List<String> keys = result.getKeys();
            long nextEmitted = emitted + keys.size();
            ScanCursor nextCursor = ScanCursor.of(result.getCursor());
            nextCursor.setFinished(result.isFinished());
            List<CompletableFuture<RedisKeyInfo>> typeFutures = keys.stream()
                    .map(key -> getTypeAsync(commands, key)
                            .thenApply(type -> RedisKeyInfo.builder().name(key).type(type).build()))
                    .toList();
            return CompletableFuture.allOf(typeFutures.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
                if (!typeFutures.isEmpty()) {
                    batchConsumer.accept(typeFutures.stream().map(CompletableFuture::join).toList());
                }
                if (nextCursor.isFinished() || nextEmitted >= count) {
                    return CompletableFuture.completedFuture(buildScanResult(nextCursor, nextEmitted));
                }
                return scanKeyTypesAsync(commands, pattern, nextCursor, count, nextEmitted, batchConsumer);
            });
        });
    }

    private CompletableFuture<RedisKeyScanResult> scanKeyNamesAsync(RedisAsyncCommands<String, String> commands,
                                                                    String pattern, ScanCursor scanCursor, long count,
                                                                    long emitted, int scanCount,
                                                                    Consumer<List<String>> batchConsumer) {
        if (emitted >= count) {
            return CompletableFuture.completedFuture(buildScanResult(scanCursor, emitted));
        }
        ScanArgs scanArgs = buildScanArgs(pattern, scanCount);
        return commands.scan(scanCursor, scanArgs).toCompletableFuture()
                .thenCompose(result -> {
                    List<String> keys = result.getKeys();
                    if (!keys.isEmpty()) {
                        batchConsumer.accept(keys);
                    }
                    long nextEmitted = emitted + keys.size();
                    ScanCursor nextCursor = ScanCursor.of(result.getCursor());
                    nextCursor.setFinished(result.isFinished());
                    if (result.isFinished() || nextEmitted >= count) {
                        return CompletableFuture.completedFuture(buildScanResult(nextCursor, nextEmitted));
                    }
                    return scanKeyNamesAsync(commands, pattern, nextCursor, count, nextEmitted,
                            nextScanCount(scanCount, keys.isEmpty()), batchConsumer);
                });
    }

    private ScanArgs buildScanArgs(String pattern, int scanCount) {
        ScanArgs scanArgs = new ScanArgs().limit(scanCount);
        if (StringUtils.isNotBlank(pattern)) {
            scanArgs.match(pattern);
        }
        return scanArgs;
    }

    private int nextScanCount(int currentScanCount, boolean emptyBatch) {
        if (!emptyBatch) {
            return KEY_SCAN_BATCH_SIZE;
        }
        return Math.min(currentScanCount * 2, KEY_SCAN_MAX_BATCH_SIZE);
    }

    private CompletableFuture<RedisKeyScanResult> queryExactKeyAsync(RedisAsyncCommands<String, String> commands,
                                                                     String key,
                                                                     Consumer<List<RedisKeyInfo>> batchConsumer) {
        RedisKeyInfoBatchEmitter emitter = new RedisKeyInfoBatchEmitter(batchConsumer);
        return commands.exists(key).toCompletableFuture()
                .thenCompose(exists -> {
                    if (exists <= 0) {
                        return CompletableFuture.completedFuture(0);
                    }
                    return buildKeyInfo(commands, key, false)
                            .thenCompose(emitter::accept)
                            .thenApply(ignored -> 1);
                })
                .thenCompose(total -> emitter.finish()
                        .thenApply(ignored -> RedisKeyScanResult.builder()
                                .cursor("0")
                                .hasMore(false)
                                .total(total)
                                .build()));
    }

    private CompletableFuture<Void> emitKeyInfoAsync(RedisAsyncCommands<String, String> commands, List<String> keys,
                                                     RedisKeyInfoBatchEmitter emitter) {
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return buildKeyInfoBatch(commands, keys, false)
                .thenCompose(futures -> CompletableFuture.allOf(futures.stream()
                        .map(future -> future.thenCompose(emitter::accept))
                        .toArray(CompletableFuture[]::new)));
    }

    private boolean isExactKeyPattern(String pattern) {
        return StringUtils.isNotBlank(pattern)
                && !StringUtils.containsAny(pattern, '*', '?', '[', ']');
    }

    private String escapeScanPattern(String prefix) {
        String value = StringUtils.defaultString(prefix);
        return value.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("?", "\\?")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    // ======================== 键信息构建 ========================

    /**
     * 构建键信息。
     *
     * @param fullValue true 读取完整值（详情场景），false 读取预览摘要（列表场景）
     */
    private CompletableFuture<RedisKeyInfo> buildKeyInfo(RedisAsyncCommands<String, String> commands, String key,
                                                         boolean fullValue) {
        return getTypeAsync(commands, key).thenCompose(type -> buildKeyInfo(commands, key, type, fullValue));
    }

    /**
     * 批量构建键信息：先并发获取本批 key 的类型，再按类型并发读取预览、TTL 和大小。
     */
    private CompletableFuture<List<CompletableFuture<RedisKeyInfo>>> buildKeyInfoBatch(
            RedisAsyncCommands<String, String> commands, List<String> keys, boolean fullValue) {
        List<CompletableFuture<String>> typeFutures = new ArrayList<>(keys.size());
        keys.forEach(key -> typeFutures.add(getTypeAsync(commands, key)));
        return CompletableFuture.allOf(typeFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<CompletableFuture<RedisKeyInfo>> keyInfoFutures = new ArrayList<>(keys.size());
                    for (int i = 0; i < keys.size(); i++) {
                        keyInfoFutures.add(buildKeyInfo(commands, keys.get(i), typeFutures.get(i).join(), fullValue));
                    }
                    return keyInfoFutures;
                });
    }

    private CompletableFuture<RedisKeyInfo> buildKeyInfo(RedisAsyncCommands<String, String> commands, String key,
                                                         String type, boolean fullValue) {
        CompletableFuture<Object> value = fullValue ? readValue(commands, key, type)
                : previewValue(commands, key, type);
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
                        .build())
                .exceptionally(e -> RedisKeyInfo.builder()
                        .name(key)
                        .type("unknown")
                        .value("")
                        .build());
    }

    // ======================== 值读取/预览 ========================

    /**
     * 预览键值内容，仅返回简短摘要用于界面展示。
     */
    private CompletableFuture<Object> previewValue(RedisAsyncCommands<String, String> commands, String key,
                                                   String type) {
        try {
            return switch (StringUtils.defaultString(type).toLowerCase()) {
                case "string" -> commands.getrange(key, 0, VALUE_TEXT_LIMIT).toCompletableFuture()
                        .thenApply(v -> abbreviate(v, VALUE_TEXT_LIMIT));
                case "hash" -> commands.hscan(key, ScanCursor.INITIAL,
                                new ScanArgs().limit(VALUE_PREVIEW_LIMIT + 1)).toCompletableFuture()
                        .thenApply(cursor -> previewMap(cursor.getMap()));
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

    /**
     * 根据键类型异步读取完整值。
     */
    private CompletableFuture<Object> readValue(RedisAsyncCommands<String, String> commands, String key, String type) {
        try {
            return switch (StringUtils.defaultString(type).toLowerCase()) {
                case "string" -> commands.get(key).toCompletableFuture().thenApply(v -> v);
                case "hash" -> commands.hgetall(key).toCompletableFuture().thenApply(v -> v);
                case "list" -> commands.lrange(key, 0, -1).toCompletableFuture().thenApply(v -> v);
                case "set" -> commands.smembers(key).toCompletableFuture().thenApply(v -> v);
                case "zset" -> commands.zrange(key, 0, -1).toCompletableFuture().thenApply(v -> v);
                default -> CompletableFuture.completedFuture("");
            };
        } catch (Exception e) {
            log.warn("Redis key value read failed, key={}", key, e);
            return CompletableFuture.completedFuture("");
        }
    }

    // ======================== 值写入 ========================

    /**
     * 根据键类型异步写入值。
     */
    private CompletableFuture<Void> writeValue(RedisAsyncCommands<String, String> commands, String key, String keyType,
                                               Object value) {
        return switch (StringUtils.defaultString(keyType).toLowerCase()) {
            case "string" -> commands.set(key, value == null ? "" : String.valueOf(value))
                    .toCompletableFuture().thenApply(ignored -> null);
            case "hash" -> {
                Map<String, String> map = toStringMap(value);
                if (!map.isEmpty()) {
                    yield commands.hset(key, map).toCompletableFuture().thenApply(ignored -> null);
                }
                yield CompletableFuture.completedFuture(null);
            }
            case "list" -> {
                List<String> values = toStringList(value);
                if (!values.isEmpty()) {
                    yield commands.rpush(key, values.toArray(new String[0]))
                            .toCompletableFuture().thenApply(ignored -> null);
                }
                yield CompletableFuture.completedFuture(null);
            }
            case "set" -> {
                List<String> values = toStringList(value);
                if (!values.isEmpty()) {
                    yield commands.sadd(key, values.toArray(new String[0]))
                            .toCompletableFuture().thenApply(ignored -> null);
                }
                yield CompletableFuture.completedFuture(null);
            }
            case "zset" -> {
                List<String> values = toStringList(value);
                yield addZsetMembers(commands, key, values, 0);
            }
            default -> throw new IllegalArgumentException("暂不支持编辑 Redis 类型: " + keyType);
        };
    }

    /**
     * 按顺序异步添加有序集合成员，保证添加顺序。
     */
    private CompletableFuture<Void> addZsetMembers(RedisAsyncCommands<String, String> commands, String key,
                                                   List<String> values, int index) {
        if (index >= values.size()) {
            return CompletableFuture.completedFuture(null);
        }
        return commands.zadd(key, index, values.get(index)).toCompletableFuture()
                .thenCompose(ignored -> addZsetMembers(commands, key, values, index + 1));
    }

    /**
     * 异步将 TTL 应用到指定键，如 ttl 为 null 或负值则取消过期时间。
     */
    private CompletableFuture<Void> applyTtl(RedisAsyncCommands<String, String> commands, String key, Long ttl) {
        if (ttl == null || ttl < 0) {
            return commands.persist(key).toCompletableFuture().thenApply(ignored -> null);
        }
        if (ttl > 0) {
            return commands.expire(key, ttl).toCompletableFuture().thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    // ======================== 类型转换工具 ========================

    private Map<String, String> toStringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) {
            return result;
        }
        map.forEach((field, fieldValue) -> {
            if (field != null) {
                result.put(String.valueOf(field), fieldValue == null ? "" : String.valueOf(fieldValue));
            }
        });
        return result;
    }

    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> item == null ? "" : String.valueOf(item)).toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(String.valueOf(value));
    }

    // ======================== 预览格式化 ========================

    private String previewMap(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner(", ", "[", values.size() > VALUE_PREVIEW_LIMIT ? ", ...]" : "]");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (index++ >= VALUE_PREVIEW_LIMIT) {
                break;
            }
            joiner.add(entry.getKey() + ":" + abbreviate(entry.getValue(), VALUE_TEXT_LIMIT));
        }
        return joiner.toString();
    }

    private String previewList(List<String> values) {
        StringJoiner joiner = new StringJoiner(", ", "[", values.size() > VALUE_PREVIEW_LIMIT ? ", ...]" : "]");
        int limit = Math.min(values.size(), VALUE_PREVIEW_LIMIT);
        for (int i = 0; i < limit; i++) {
            joiner.add(abbreviate(values.get(i), VALUE_TEXT_LIMIT));
        }
        return joiner.toString();
    }

    // ======================== 批量发射器 ========================

    private static class RedisKeyInfoBatchEmitter {

        private final Object lock = new Object();
        private final Consumer<List<RedisKeyInfo>> batchConsumer;
        private final List<RedisKeyInfo> buffer = new ArrayList<>(KEY_INFO_EMIT_BATCH_SIZE);
        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

        private RedisKeyInfoBatchEmitter(Consumer<List<RedisKeyInfo>> batchConsumer) {
            this.batchConsumer = batchConsumer;
        }

        private CompletableFuture<Void> accept(RedisKeyInfo keyInfo) {
            synchronized (lock) {
                buffer.add(keyInfo);
                if (buffer.size() < KEY_INFO_EMIT_BATCH_SIZE) {
                    return tail;
                }
                return flushLocked();
            }
        }

        private CompletableFuture<Void> finish() {
            synchronized (lock) {
                return flushLocked();
            }
        }

        private CompletableFuture<Void> flushLocked() {
            if (buffer.isEmpty()) {
                return tail;
            }
            List<RedisKeyInfo> batch = List.copyOf(buffer);
            buffer.clear();
            tail = tail.thenRunAsync(() -> batchConsumer.accept(batch));
            return tail;
        }
    }
}
