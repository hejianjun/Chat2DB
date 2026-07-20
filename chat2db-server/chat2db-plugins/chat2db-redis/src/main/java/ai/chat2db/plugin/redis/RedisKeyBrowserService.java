package ai.chat2db.plugin.redis;

import ai.chat2db.spi.redis.RedisKeyBrowser;
import ai.chat2db.spi.redis.RedisKeyInfo;
import ai.chat2db.spi.redis.RedisKeyMutation;
import ai.chat2db.spi.redis.RedisKeyScanResult;
import ai.chat2db.spi.redis.RedisKeyValuePage;
import ai.chat2db.spi.sql.Chat2DBContext;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import java.util.function.Function;
import java.util.function.Predicate;

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
        return withConnection(databaseName, commands -> {
            String pattern = StringUtils.trimToNull(searchKey);
            if (isInitialCursor(cursor) && isExactKeyPattern(pattern)) {
                return queryExactKeyAsync(commands, pattern, batchConsumer);
            }
            RedisKeyInfoBatchEmitter emitter = new RedisKeyInfoBatchEmitter(batchConsumer);
            return scanAsync(commands, new ScanRequest(pattern, cursor, normalizeLimit(count)),
                    keys -> emitKeyInfoAsync(commands, keys, emitter).thenApply(ignored -> List.<Void>of()),
                    ignored -> {}, progress -> false)
                    .thenCompose(result -> emitter.finish().thenApply(ignored -> result));
        });
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

    @Override
    public CompletableFuture<RedisKeyScanResult> streamPrefixKeys(String databaseName,
                                                                    Consumer<List<RedisKeyInfo>> batchConsumer) {
        return withConnection(databaseName, commands -> scanAsync(commands,
                new ScanRequest(null, "0", Long.MAX_VALUE),
                keys -> CompletableFuture.completedFuture(keys.stream()
                        .map(key -> RedisKeyInfo.builder().name(key).build()).toList()),
                batchConsumer, progress -> false));
    }

    @Override
    public CompletableFuture<List<RedisKeyInfo>> sampleKeys(String databaseName, String prefix, int maxCount) {
        Map<String, RedisKeyInfo> samples = new LinkedHashMap<>();
        return withConnection(databaseName, commands -> scanAsync(commands,
                new ScanRequest(escapeScanPattern(prefix) + "*", "0", Math.max(1, maxCount)),
                keys -> buildTypeInfoBatch(commands, keys), batch -> batch.forEach(key -> samples.putIfAbsent(key.getType(), key)),
                progress -> samples.keySet().containsAll(SAMPLE_KEY_TYPES))
                .thenCompose(ignored -> buildSampleKeyInfoAsync(commands, samples)));
    }

    private <T> CompletableFuture<RedisKeyScanResult> scanAsync(RedisAsyncCommands<String, String> commands,
                                                                  ScanRequest request,
                                                                  Function<List<String>, CompletableFuture<List<T>>> resolver,
                                                                  Consumer<List<T>> batchConsumer,
                                                                  Predicate<ScanProgress<T>> stopCondition) {
        return scanAsync(commands, request, buildScanCursor(request.cursor()), 0, KEY_SCAN_BATCH_SIZE,
                resolver, batchConsumer, stopCondition);
    }

    private <T> CompletableFuture<RedisKeyScanResult> scanAsync(RedisAsyncCommands<String, String> commands,
                                                                  ScanRequest request, ScanCursor cursor, long emitted,
                                                                  int scanCount,
                                                                  Function<List<String>, CompletableFuture<List<T>>> resolver,
                                                                  Consumer<List<T>> batchConsumer,
                                                                  Predicate<ScanProgress<T>> stopCondition) {
        if (emitted >= request.limit()) {
            return CompletableFuture.completedFuture(buildScanResult(cursor, emitted));
        }
        return commands.scan(cursor, buildScanArgs(request.pattern(), scanCount)).toCompletableFuture()
                .thenCompose(result -> {
                    long remaining = request.limit() - emitted;
                    List<String> keys = result.getKeys().stream().limit(remaining).toList();
                    long nextEmitted = emitted + keys.size();
                    ScanCursor nextCursor = ScanCursor.of(result.getCursor());
                    nextCursor.setFinished(result.isFinished());
                    return resolver.apply(keys).thenCompose(batch -> {
                        if (!batch.isEmpty()) {
                            batchConsumer.accept(batch);
                        }
                        ScanProgress<T> progress = new ScanProgress<>(nextEmitted, batch);
                        if (nextCursor.isFinished() || nextEmitted >= request.limit() || stopCondition.test(progress)) {
                            return CompletableFuture.completedFuture(buildScanResult(nextCursor, nextEmitted));
                        }
                        return scanAsync(commands, request, nextCursor, nextEmitted,
                                nextScanCount(scanCount, keys.isEmpty()), resolver, batchConsumer, stopCondition);
                    });
                });
    }

    private CompletableFuture<List<RedisKeyInfo>> buildTypeInfoBatch(RedisAsyncCommands<String, String> commands,
                                                                       List<String> keys) {
        List<CompletableFuture<RedisKeyInfo>> futures = keys.stream()
                .map(key -> getTypeAsync(commands, key)
                        .thenApply(type -> RedisKeyInfo.builder().name(key).type(type).build()))
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    private CompletableFuture<List<RedisKeyInfo>> buildSampleKeyInfoAsync(RedisAsyncCommands<String, String> commands,
                                                                            Map<String, RedisKeyInfo> samples) {
        List<CompletableFuture<RedisKeyInfo>> futures = samples.values().stream()
                .map(sample -> buildKeyInfo(commands, sample.getName(), sample.getType(), false))
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    private <T> CompletableFuture<T> withConnection(String databaseName,
                                                     Function<RedisAsyncCommands<String, String>, CompletableFuture<T>> action) {
        try {
            RedisConnectionProvider.RedisConnectionContext context =
                    RedisConnectionProvider.open(Chat2DBContext.getConnectInfo());
            AtomicBoolean closed = new AtomicBoolean(false);
            CompletableFuture<T> operation = selectDatabase(context.connection().async(), databaseName)
                    .thenCompose(ignored -> action.apply(context.connection().async()));
            CompletableFuture<T> result = new CompletableFuture<>();
            operation.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    result.complete(value);
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
    public void partialUpdateKey(String databaseName, RedisKeyMutation mutation) {
        if (mutation == null || StringUtils.isBlank(mutation.getKeyName())) {
            throw new IllegalArgumentException("Redis key 不能为空");
        }
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            selectDatabase(commands, databaseName).join();
            String actualType = getTypeAsync(commands, mutation.getKeyName()).join();
            String requestedType = StringUtils.lowerCase(StringUtils.trimToEmpty(mutation.getKeyType()));
            if ("none".equals(actualType)) {
                throw new IllegalArgumentException("Redis key 不存在: " + mutation.getKeyName());
            }
            if (!StringUtils.equals(actualType, requestedType)) {
                throw new IllegalArgumentException("Redis key 类型已变化: " + actualType);
            }
            validatePartialMutation(requestedType, mutation);
            String targetKey = StringUtils.defaultIfBlank(mutation.getUpdateKey(), mutation.getKeyName());
            if (!StringUtils.equals(targetKey, mutation.getKeyName())) {
                if (commands.exists(targetKey).toCompletableFuture().join() > 0) {
                    throw new IllegalArgumentException("Redis key 已存在: " + targetKey);
                }
            }
            commands.multi().toCompletableFuture().join();
            if (!StringUtils.equals(targetKey, mutation.getKeyName())) {
                commands.rename(mutation.getKeyName(), targetKey);
            }
            queuePartialMutation(commands, targetKey, requestedType, mutation);
            queueOptionalTtl(commands, targetKey, mutation.getTtl());
            commands.exec().toCompletableFuture().join();
        }
    }

    private void validatePartialMutation(String type, RedisKeyMutation mutation) {
        switch (type) {
            case "hash" -> {
                requireAbsent(mutation.getSetAdds(), mutation.getSetRemoves(), mutation.getZsetUpserts(),
                        mutation.getZsetRemoves());
                requireDisjoint(mutation.getHashUpserts() == null ? Set.of() : mutation.getHashUpserts().keySet(),
                        mutation.getHashDeletes());
            }
            case "set" -> {
                requireAbsent(mutation.getHashUpserts(), mutation.getHashDeletes(), mutation.getZsetUpserts(),
                        mutation.getZsetRemoves());
                requireDisjoint(mutation.getSetAdds(), mutation.getSetRemoves());
            }
            case "zset" -> {
                requireAbsent(mutation.getHashUpserts(), mutation.getHashDeletes(), mutation.getSetAdds(),
                        mutation.getSetRemoves());
                requireDisjoint(mutation.getZsetUpserts() == null ? Set.of() : mutation.getZsetUpserts().keySet(),
                        mutation.getZsetRemoves());
            }
            default -> throw new IllegalArgumentException("暂不支持部分编辑 Redis 类型: " + type);
        }
    }

    private void requireAbsent(Object... values) {
        for (Object value : values) {
            if (value instanceof Collection<?> collection && !collection.isEmpty()
                    || value instanceof Map<?, ?> map && !map.isEmpty()) {
                throw new IllegalArgumentException("Redis 部分更新包含与 key 类型不匹配的字段");
            }
        }
    }

    private void requireDisjoint(Collection<String> additions, Collection<String> removals) {
        if (additions == null || removals == null) {
            return;
        }
        if (additions.stream().anyMatch(removals::contains)) {
            throw new IllegalArgumentException("Redis 元素不能同时新增和删除");
        }
    }

    private void queuePartialMutation(RedisAsyncCommands<String, String> commands, String key, String type,
                                      RedisKeyMutation mutation) {
        switch (type) {
            case "hash" -> {
                if (mutation.getHashUpserts() != null && !mutation.getHashUpserts().isEmpty()) {
                    commands.hset(key, mutation.getHashUpserts());
                }
                if (mutation.getHashDeletes() != null && !mutation.getHashDeletes().isEmpty()) {
                    commands.hdel(key, mutation.getHashDeletes().toArray(String[]::new));
                }
            }
            case "set" -> {
                if (mutation.getSetAdds() != null && !mutation.getSetAdds().isEmpty()) {
                    commands.sadd(key, mutation.getSetAdds().toArray(String[]::new));
                }
                if (mutation.getSetRemoves() != null && !mutation.getSetRemoves().isEmpty()) {
                    commands.srem(key, mutation.getSetRemoves().toArray(String[]::new));
                }
            }
            case "zset" -> {
                if (mutation.getZsetUpserts() != null) {
                    mutation.getZsetUpserts().forEach((member, score) -> commands.zadd(key, score, member));
                }
                if (mutation.getZsetRemoves() != null && !mutation.getZsetRemoves().isEmpty()) {
                    commands.zrem(key, mutation.getZsetRemoves().toArray(String[]::new));
                }
            }
            default -> throw new IllegalArgumentException("暂不支持部分编辑 Redis 类型: " + type);
        }
    }

    private void queueOptionalTtl(RedisAsyncCommands<String, String> commands, String key, Long ttl) {
        if (ttl == null) {
            return;
        }
        if (ttl == 0) {
            throw new IllegalArgumentException("Redis TTL 不能为 0");
        }
        if (ttl < 0) {
            commands.persist(key);
        } else {
            commands.expire(key, ttl);
        }
    }

    @Override
    public String fingerprintKey(String databaseName, String keyName) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            selectDatabase(commands, databaseName).join();
            String type = getTypeAsync(commands, keyName).join();
            if ("none".equals(type)) {
                return null;
            }
            byte[] dump = commands.dump(keyName).toCompletableFuture().join();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(type.getBytes(StandardCharsets.UTF_8));
            if (dump != null) {
                digest.update(dump);
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Redis key fingerprint failed", e);
        }
    }

    @Override
    public void updateKeyMetadata(String databaseName, String keyName, String updateKey, Long ttl) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            selectDatabase(commands, databaseName).join();
            if (commands.exists(keyName).toCompletableFuture().join() == 0) {
                throw new IllegalArgumentException("Redis key 不存在: " + keyName);
            }
            String targetKey = StringUtils.defaultIfBlank(updateKey, keyName);
            if (!StringUtils.equals(keyName, targetKey)) {
                if (commands.exists(targetKey).toCompletableFuture().join() > 0) {
                    throw new IllegalArgumentException("Redis key 已存在: " + targetKey);
                }
                commands.rename(keyName, targetKey).toCompletableFuture().join();
            }
            applyOptionalTtl(commands, targetKey, ttl).join();
        }
    }

    @Override
    public RedisKeyValuePage queryKeyPage(String databaseName, String keyName, String cursor, int count,
                                          int maxStringBytes) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisAsyncCommands<String, String> commands = context.connection().async();
            selectDatabase(commands, databaseName).join();
            String type = getTypeAsync(commands, keyName).join();
            if ("none".equals(type)) {
                throw new IllegalArgumentException("Redis key 不存在: " + keyName);
            }
            PageData page = readPage(commands, keyName, type, cursor, Math.max(1, count), maxStringBytes);
            Long ttl = commands.ttl(keyName).toCompletableFuture().join();
            Long size = commands.memoryUsage(keyName).toCompletableFuture().exceptionally(e -> null).join();
            return RedisKeyValuePage.builder()
                    .name(keyName).type(type).ttl(ttl).size(size).items(page.items())
                    .nextCursor(page.nextCursor()).hasMore(page.hasMore()).total(page.total())
                    .fullyLoaded(!page.hasMore()).build();
        }
    }

    private long normalizeLimit(int count) {
        return count < 0 ? Long.MAX_VALUE : count == 0 ? KEY_SCAN_BATCH_SIZE : count;
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

    private record ScanRequest(String pattern, String cursor, long limit) {
    }

    private record ScanProgress<T>(long emitted, List<T> batch) {
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

    private PageData readPage(RedisAsyncCommands<String, String> commands, String key, String type, String cursor,
                              int count, int maxStringBytes) {
        return switch (StringUtils.lowerCase(type)) {
            case "string" -> readStringPage(commands, key, cursor, maxStringBytes);
            case "hash" -> {
                var result = commands.hscan(key, buildScanCursor(cursor), new ScanArgs().limit(count))
                        .toCompletableFuture().join();
                List<Map<String, Object>> items = result.getMap().entrySet().stream()
                        .map(entry -> item("field", entry.getKey(), "value", entry.getValue())).toList();
                yield new PageData(items, result.getCursor(), !result.isFinished(),
                        commands.hlen(key).toCompletableFuture().join());
            }
            case "set" -> {
                var result = commands.sscan(key, buildScanCursor(cursor), new ScanArgs().limit(count))
                        .toCompletableFuture().join();
                List<Map<String, Object>> items = result.getValues().stream()
                        .map(value -> item("value", value)).toList();
                yield new PageData(items, result.getCursor(), !result.isFinished(),
                        commands.scard(key).toCompletableFuture().join());
            }
            case "list" -> {
                long offset = parseOffset(cursor);
                long total = commands.llen(key).toCompletableFuture().join();
                List<String> values = commands.lrange(key, offset, offset + count - 1).toCompletableFuture().join();
                List<Map<String, Object>> items = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    items.add(item("index", offset + i, "value", values.get(i)));
                }
                long next = offset + values.size();
                yield new PageData(items, Long.toString(next), next < total, total);
            }
            case "zset" -> {
                long offset = parseOffset(cursor);
                long total = commands.zcard(key).toCompletableFuture().join();
                List<ScoredValue<String>> values = commands.zrangeWithScores(key, offset, offset + count - 1)
                        .toCompletableFuture().join();
                List<Map<String, Object>> items = values.stream()
                        .map(value -> item("member", value.getValue(), "score", value.getScore())).toList();
                long next = offset + values.size();
                yield new PageData(items, Long.toString(next), next < total, total);
            }
            default -> new PageData(List.of(), "0", false, 0L);
        };
    }

    private PageData readStringPage(RedisAsyncCommands<String, String> commands, String key, String cursor,
                                    int maxStringBytes) {
        String value = StringUtils.defaultString(commands.get(key).toCompletableFuture().join());
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int start = (int) Math.min(parseOffset(cursor), bytes.length);
        int end = Math.min(bytes.length, start + Math.max(1, maxStringBytes));
        while (end < bytes.length && end > start && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        String slice = new String(bytes, start, end - start, StandardCharsets.UTF_8);
        return new PageData(List.of(item("value", slice)), Integer.toString(end), end < bytes.length,
                (long) bytes.length);
    }

    private CompletableFuture<Void> applyPartialMutation(RedisAsyncCommands<String, String> commands, String key,
                                                          String type, RedisKeyMutation mutation) {
        CompletableFuture<?> operation = switch (type) {
            case "hash" -> applyHashMutation(commands, key, mutation);
            case "set" -> applySetMutation(commands, key, mutation);
            case "zset" -> applyZsetMutation(commands, key, mutation);
            default -> throw new IllegalArgumentException("暂不支持部分编辑 Redis 类型: " + type);
        };
        return operation.thenApply(ignored -> null);
    }

    private CompletableFuture<?> applyHashMutation(RedisAsyncCommands<String, String> commands, String key,
                                                    RedisKeyMutation mutation) {
        CompletableFuture<?> future = mutation.getHashUpserts() == null || mutation.getHashUpserts().isEmpty()
                ? CompletableFuture.completedFuture(null)
                : commands.hset(key, mutation.getHashUpserts()).toCompletableFuture();
        return future.thenCompose(ignored -> mutation.getHashDeletes() == null || mutation.getHashDeletes().isEmpty()
                ? CompletableFuture.completedFuture(null)
                : commands.hdel(key, mutation.getHashDeletes().toArray(String[]::new)).toCompletableFuture());
    }

    private CompletableFuture<?> applySetMutation(RedisAsyncCommands<String, String> commands, String key,
                                                   RedisKeyMutation mutation) {
        CompletableFuture<?> future = mutation.getSetAdds() == null || mutation.getSetAdds().isEmpty()
                ? CompletableFuture.completedFuture(null)
                : commands.sadd(key, mutation.getSetAdds().toArray(String[]::new)).toCompletableFuture();
        return future.thenCompose(ignored -> mutation.getSetRemoves() == null || mutation.getSetRemoves().isEmpty()
                ? CompletableFuture.completedFuture(null)
                : commands.srem(key, mutation.getSetRemoves().toArray(String[]::new)).toCompletableFuture());
    }

    private CompletableFuture<?> applyZsetMutation(RedisAsyncCommands<String, String> commands, String key,
                                                    RedisKeyMutation mutation) {
        CompletableFuture<?> future = CompletableFuture.completedFuture(null);
        if (mutation.getZsetUpserts() != null) {
            for (Map.Entry<String, Double> entry : mutation.getZsetUpserts().entrySet()) {
                future = future.thenCompose(ignored -> commands.zadd(key, entry.getValue(), entry.getKey())
                        .toCompletableFuture());
            }
        }
        return future.thenCompose(ignored -> mutation.getZsetRemoves() == null || mutation.getZsetRemoves().isEmpty()
                ? CompletableFuture.completedFuture(null)
                : commands.zrem(key, mutation.getZsetRemoves().toArray(String[]::new)).toCompletableFuture());
    }

    private CompletableFuture<Void> applyOptionalTtl(RedisAsyncCommands<String, String> commands, String key,
                                                      Long ttl) {
        if (ttl == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (ttl == 0) {
            throw new IllegalArgumentException("Redis TTL 不能为 0");
        }
        return applyTtl(commands, key, ttl);
    }

    private long parseOffset(String cursor) {
        if (StringUtils.isBlank(cursor) || "0".equals(cursor)) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(cursor));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Redis cursor 无效: " + cursor, e);
        }
    }

    private Map<String, Object> item(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private record PageData(List<Map<String, Object>> items, String nextCursor, boolean hasMore, Long total) {
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
                if (value instanceof Map<?, ?> map) {
                    CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null) {
                            continue;
                        }
                        double score = Double.parseDouble(String.valueOf(entry.getValue()));
                        future = future.thenCompose(ignored -> commands.zadd(key, score,
                                String.valueOf(entry.getKey())).toCompletableFuture().thenApply(result -> null));
                    }
                    yield future;
                }
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
