package ai.chat2db.server.domain.core.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import ai.chat2db.spi.model.ExecuteResult;
import com.alibaba.fastjson2.JSON;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Short-lived compressed cache for Hive and DLC query results.
 */
@Slf4j
@Component
public class QueryResultCache {

    private static final long DEFAULT_MAXIMUM_BYTES = 100L * 1024 * 1024;
    private static final long DEFAULT_EXPIRE_MINUTES = 24L * 60;

    private final Cache<Long, byte[]> cache;
    private final long maximumBytes;

    public QueryResultCache(Environment environment) {
        maximumBytes = Math.max(1L, environment.getProperty(
                "chat2db.query-result-cache.maximum-bytes", Long.class, DEFAULT_MAXIMUM_BYTES));
        long expireMinutes = Math.max(1L, environment.getProperty(
                "chat2db.query-result-cache.expire-after-access-minutes", Long.class, DEFAULT_EXPIRE_MINUTES));
        cache = CacheBuilder.newBuilder()
                .maximumWeight(maximumBytes)
                .weigher((Weigher<Long, byte[]>) (key, value) -> value.length)
                .expireAfterAccess(expireMinutes, TimeUnit.MINUTES)
                .build();
    }

    public void put(Long operationLogId, Long userId, Long dataSourceId, String sql, ExecuteResult result) {
        if (operationLogId == null || result == null) {
            return;
        }
        try {
            CachedQueryResult cachedResult = new CachedQueryResult();
            cachedResult.setOperationLogId(operationLogId);
            cachedResult.setUserId(userId);
            cachedResult.setDataSourceId(dataSourceId);
            cachedResult.setSql(sql);
            cachedResult.setResult(result);
            byte[] compressed = compress(JSON.toJSONBytes(cachedResult));
            if (compressed.length > maximumBytes) {
                log.warn("Skip oversized query result cache, operationLogId={}, compressedBytes={}",
                        operationLogId, compressed.length);
                return;
            }
            cache.put(operationLogId, compressed);
        } catch (Exception e) {
            log.warn("Cache query result failed, operationLogId={}", operationLogId, e);
        }
    }

    public CachedQueryResult get(Long operationLogId) {
        if (operationLogId == null) {
            return null;
        }
        byte[] compressed = cache.getIfPresent(operationLogId);
        if (compressed == null) {
            return null;
        }
        try {
            return JSON.parseObject(decompress(compressed), CachedQueryResult.class);
        } catch (Exception e) {
            cache.invalidate(operationLogId);
            log.warn("Read query result cache failed, operationLogId={}", operationLogId, e);
            return null;
        }
    }

    private byte[] compress(byte[] source) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(source);
        }
        return output.toByteArray();
    }

    private byte[] decompress(byte[] source) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            return output.toByteArray();
        }
    }

    @Data
    public static class CachedQueryResult {
        private Long operationLogId;
        private Long userId;
        private Long dataSourceId;
        private String sql;
        private ExecuteResult result;
    }
}
