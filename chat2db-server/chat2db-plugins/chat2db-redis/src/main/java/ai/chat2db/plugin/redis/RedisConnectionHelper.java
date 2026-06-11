package ai.chat2db.plugin.redis;

import ai.chat2db.spi.redis.RedisKeyScanResult;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 连接与扫描公共工具类。
 * <p>
 * 提供数据库选择、连接上下文关闭、SCAN 游标和结果构建等共享方法，
 * 供 {@link RedisKeyBrowserService} 和其他需要 Redis 连接管理的组件复用。
 */
@Slf4j
final class RedisConnectionHelper {

    static final int KEY_SCAN_BATCH_SIZE = 1000;
    static final int KEY_INFO_EMIT_BATCH_SIZE = 50;
    static final int VALUE_PREVIEW_LIMIT = 5;
    static final int VALUE_TEXT_LIMIT = 200;

    private RedisConnectionHelper() {
    }

    /**
     * 异步选择 Redis 数据库，databaseName 为空时跳过。
     */
    static CompletableFuture<Void> selectDatabase(RedisAsyncCommands<String, String> commands, String databaseName) {
        if (StringUtils.isBlank(databaseName)) {
            return CompletableFuture.completedFuture(null);
        }
        return commands.select(Integer.parseInt(databaseName)).toCompletableFuture().thenApply(ignored -> null);
    }

    /**
     * 安全关闭连接上下文，通过 AtomicBoolean 保证只关闭一次。
     */
    static void closeContext(RedisConnectionProvider.RedisConnectionContext context, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            context.close();
        } catch (Exception e) {
            log.warn("Redis connection close failed", e);
        }
    }

    /**
     * 异步关闭连接上下文。
     */
    static void closeContextAsync(RedisConnectionProvider.RedisConnectionContext context, AtomicBoolean closed) {
        CompletableFuture.runAsync(() -> closeContext(context, closed));
    }

    /**
     * 判断是否为初始游标（空或 "0"）。
     */
    static boolean isInitialCursor(String cursor) {
        return StringUtils.isBlank(cursor) || "0".equals(cursor);
    }

    /**
     * 根据字符串游标构建 ScanCursor 实例。
     */
    static ScanCursor buildScanCursor(String cursor) {
        if (isInitialCursor(cursor)) {
            return ScanCursor.INITIAL;
        }
        return ScanCursor.of(cursor);
    }

    /**
     * 根据扫描游标和已输出数量构建扫描结果。
     */
    static RedisKeyScanResult buildScanResult(ScanCursor scanCursor, long emitted) {
        boolean hasMore = !scanCursor.isFinished();
        return RedisKeyScanResult.builder()
                .cursor(hasMore ? scanCursor.getCursor() : "0")
                .hasMore(hasMore)
                .total(Math.toIntExact(Math.min(emitted, Integer.MAX_VALUE)))
                .build();
    }

    /**
     * 异步获取键类型，失败时默认返回 "string"。
     */
    static CompletableFuture<String> getTypeAsync(RedisAsyncCommands<String, String> commands, String key) {
        return commands.type(key).toCompletableFuture()
                .thenApply(type -> StringUtils.isNotBlank(type) ? type : "string")
                .exceptionally(e -> {
                    log.error("type获取失败", e);
                    return "string";
                });
    }

    /**
     * 截断超长文本。
     */
    static String abbreviate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }
}
