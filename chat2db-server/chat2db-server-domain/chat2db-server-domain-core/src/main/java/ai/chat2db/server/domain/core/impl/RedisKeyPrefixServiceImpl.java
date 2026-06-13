package ai.chat2db.server.domain.core.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import ai.chat2db.server.domain.api.model.RedisKeyPrefix;
import ai.chat2db.server.domain.api.model.RedisKeyPrefixBuildStatus;
import ai.chat2db.server.domain.api.param.RedisKeyPrefixQueryParam;
import ai.chat2db.server.domain.api.service.RedisKeyPrefixService;
import ai.chat2db.server.domain.core.cache.LuceneIndexManager;
import ai.chat2db.server.domain.core.cache.LuceneIndexManagerFactory;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.redis.RedisKeyBrowser;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisKeyPrefixServiceImpl implements RedisKeyPrefixService {

    private static final String STATUS_IDLE = "IDLE";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";
    private static final long FLUSH_KEY_THRESHOLD = 50000L;
    private static final int FLUSH_DOCUMENT_BATCH_SIZE = 500;

    @Autowired
    private LuceneIndexManagerFactory managerFactory;

    private final Map<String, RedisKeyPrefixBuildStatus> statusMap = new ConcurrentHashMap<>();

    @Override
    public List<RedisKeyPrefix> search(RedisKeyPrefixQueryParam param) {
        LuceneIndexManager<RedisKeyPrefix> luceneMgr = managerFactory.getManager(param.getDataSourceId());
        List<RedisKeyPrefix> prefixes = luceneMgr.search(param, param.getLastDocId(),
                QueryParser.escape(StringUtils.defaultString(param.getSearchKey())));
        RedisKeyPrefixBuildStatus status = getStatus(param.getDataSourceId(), param.getDatabaseName());
        boolean building = STATUS_RUNNING.equals(status.getStatus());
        prefixes.forEach(prefix -> prefix.setBuilding(building));
        param.setLastDocId(luceneMgr.getLastDocId());
        return prefixes;
    }

    @Override
    public void refresh(Long dataSourceId, String databaseName, ConnectInfo connectInfo) {
        String statusKey = buildStatusKey(dataSourceId, databaseName);
        RedisKeyPrefixBuildStatus currentStatus = statusMap.get(statusKey);
        if (currentStatus != null && STATUS_RUNNING.equals(currentStatus.getStatus())) {
            return;
        }

        Long refreshVersion = System.currentTimeMillis();
        ConnectInfo asyncConnectInfo = connectInfo.copy();
        RedisKeyPrefixBuildStatus status = RedisKeyPrefixBuildStatus.builder()
                .status(STATUS_RUNNING)
                .cursor("0")
                .scannedCount(0L)
                .prefixCount(0L)
                .refreshVersion(refreshVersion)
                .startedAt(now())
                .updatedAt(now())
                .build();
        statusMap.put(statusKey, status);

        CompletableFuture.runAsync(() -> doRefresh(dataSourceId, databaseName, asyncConnectInfo, statusKey));
    }

    @Override
    public void updateComment(Long dataSourceId, String databaseName, String prefix, String comment) {
        LuceneIndexManager<RedisKeyPrefix> luceneMgr = managerFactory.getManager(dataSourceId);
        RedisKeyPrefix source = RedisKeyPrefix.builder()
                .dataSourceId(dataSourceId)
                .databaseName(databaseName)
                .name(prefix)
                .comment(comment)
                .build();
        RedisKeyPrefix storedPrefix = luceneMgr.findDocument(source);
        if (storedPrefix != null) {
            source.setRefreshVersion(storedPrefix.getRefreshVersion());
        }
        luceneMgr.updateDocument(source);
    }

    @Override
    public void delete(Long dataSourceId, String databaseName, String prefix) {
        RedisKeyPrefix source = RedisKeyPrefix.builder()
                .dataSourceId(dataSourceId)
                .databaseName(databaseName)
                .name(prefix)
                .build();
        managerFactory.<RedisKeyPrefix>getManager(dataSourceId).deleteDocument(source);
    }

    @Override
    public RedisKeyPrefixBuildStatus getStatus(Long dataSourceId, String databaseName) {
        return statusMap.getOrDefault(buildStatusKey(dataSourceId, databaseName),
                RedisKeyPrefixBuildStatus.builder()
                        .status(STATUS_IDLE)
                        .cursor("0")
                        .scannedCount(0L)
                        .prefixCount(0L)
                        .build());
    }

    private void doRefresh(Long dataSourceId, String databaseName, ConnectInfo connectInfo, String statusKey) {
        RedisKeyPrefixBuildStatus status = statusMap.get(statusKey);
        Map<String, Long> prefixCountMap = new ConcurrentHashMap<>();
        AtomicLong scannedCount = new AtomicLong();
        AtomicLong lastFlushScannedCount = new AtomicLong();
        Long refreshVersion = status.getRefreshVersion();
        try {
            Chat2DBContext.putContext(connectInfo);
            RedisKeyBrowser browser = getRedisKeyBrowser();
            LuceneIndexManager<RedisKeyPrefix> luceneMgr = managerFactory.getManager(dataSourceId);
            browser.streamKeyNames(databaseName, null, "0", -1, keys -> {
                keys.forEach(key -> collectPrefixes(key, prefixCountMap));
                long scanned = scannedCount.addAndGet(keys.size());
                status.setScannedCount(scanned);
                status.setPrefixCount((long) prefixCountMap.size());
                status.setUpdatedAt(now());
                if (scanned - lastFlushScannedCount.get() >= FLUSH_KEY_THRESHOLD) {
                    flushPrefixes(luceneMgr, dataSourceId, databaseName, refreshVersion, prefixCountMap);
                    lastFlushScannedCount.set(scanned);
                }
            }).join();
            flushPrefixes(luceneMgr, dataSourceId, databaseName, refreshVersion, prefixCountMap);
            deleteStalePrefixes(luceneMgr, databaseName, refreshVersion);
            status.setStatus(STATUS_DONE);
            status.setCursor("0");
            status.setPrefixCount((long) prefixCountMap.size());
            status.setUpdatedAt(now());
        } catch (Exception e) {
            log.error("Refresh redis key prefix cache failed, dataSourceId={}, databaseName={}",
                    dataSourceId, databaseName, e);
            status.setStatus(STATUS_FAILED);
            status.setErrorMessage(e.getMessage());
            status.setUpdatedAt(now());
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    private RedisKeyBrowser getRedisKeyBrowser() {
        MetaData metaData = Chat2DBContext.getMetaData();
        if (metaData instanceof RedisKeyBrowser browser) {
            return browser;
        }
        throw new BusinessException("当前数据源不是 Redis");
    }

    private void collectPrefixes(String key, Map<String, Long> prefixCountMap) {
        if (StringUtils.isBlank(key) || !key.contains(":")) {
            return;
        }
        String[] rawParts = key.split(":");
        List<String> parts = new ArrayList<>();
        for (String rawPart : rawParts) {
            if (StringUtils.isNotEmpty(rawPart)) {
                parts.add(rawPart);
            }
        }
        StringJoiner joiner = new StringJoiner(":");
        for (int i = 0; i < parts.size() - 1; i++) {
            joiner.add(parts.get(i));
        }
        prefixCountMap.merge(joiner.toString(), 1L, Long::sum);
    }

    private void flushPrefixes(LuceneIndexManager<RedisKeyPrefix> luceneMgr, Long dataSourceId, String databaseName,
                               Long refreshVersion, Map<String, Long> prefixCountMap) {
        if (prefixCountMap.isEmpty()) {
            return;
        }
        List<RedisKeyPrefix> prefixes = prefixCountMap.entrySet().stream()
                .map(entry -> RedisKeyPrefix.builder()
                        .dataSourceId(dataSourceId)
                        .databaseName(databaseName)
                        .name(entry.getKey())
                        .keyCount(entry.getValue())
                        .refreshVersion(refreshVersion)
                        .build())
                .toList();
        for (int i = 0; i < prefixes.size(); i += FLUSH_DOCUMENT_BATCH_SIZE) {
            int toIndex = Math.min(i + FLUSH_DOCUMENT_BATCH_SIZE, prefixes.size());
            luceneMgr.updateDocuments(prefixes.subList(i, toIndex), null, false);
        }
    }

    private void deleteStalePrefixes(LuceneIndexManager<RedisKeyPrefix> luceneMgr, String databaseName,
                                     Long refreshVersion) {
        if (refreshVersion == null) {
            return;
        }
        RedisKeyPrefix queryModel = RedisKeyPrefix.builder()
                .databaseName(databaseName)
                .build();
        luceneMgr.deleteByDatabaseAndSchemaExceptLongField(queryModel, "refreshVersion", refreshVersion);
    }

    private String buildStatusKey(Long dataSourceId, String databaseName) {
        return dataSourceId + ":" + StringUtils.defaultString(databaseName);
    }

    private String now() {
        return LocalDateTime.now().toString();
    }
}
