package ai.chat2db.server.domain.core.impl;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import ai.chat2db.server.domain.api.model.MetadataSnapshot;
import ai.chat2db.server.domain.api.model.RedisKeyPrefix;
import ai.chat2db.server.domain.api.model.RedisKeyPrefixBuildStatus;
import ai.chat2db.server.domain.api.param.RedisKeyPrefixQueryParam;
import ai.chat2db.server.domain.api.service.MetadataSnapshotService;
import ai.chat2db.server.domain.api.service.RedisKeyPrefixService;
import ai.chat2db.server.domain.core.cache.LuceneIndexManager;
import ai.chat2db.server.domain.core.cache.LuceneIndexManagerFactory;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.redis.RedisKeyBrowser;
import ai.chat2db.spi.redis.RedisKeyInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Redis key 前缀索引服务实现。
 *
 * <p>该服务用于把 Redis 中数量可能非常大的 key 按业务前缀聚合后写入 Lucene 索引，
 * 便于前端分页搜索、展示 key 数量以及维护前缀备注。刷新过程采用异步执行，避免扫描
 * Redis 时阻塞调用线程。</p>
 *
 * <p>前缀提取规则：仅在配置的分隔符位置截断，并优先选择最长且 key 数量大于 1 的前缀。
 * 例如 {@code user:1001:profile} 会优先聚合到 {@code user:1001:}。</p>
 */
@Slf4j
@Service
public class RedisKeyPrefixServiceImpl implements RedisKeyPrefixService {

    /** 当前数据源/库尚未启动刷新任务 */
    private static final String STATUS_IDLE = "IDLE";
    /** 正在执行 Redis 前缀缓存刷新 */
    private static final String STATUS_RUNNING = "RUNNING";
    /** 本轮刷新已完成 */
    private static final String STATUS_DONE = "DONE";
    /** 本轮刷新失败 */
    private static final String STATUS_FAILED = "FAILED";
    /** Redis 前缀搜索默认按前缀名升序展示。 */
    private static final String DEFAULT_SORT_FIELD = "name";

    /** 默认 Redis key 前缀分隔符，支持冒号、下划线、中划线和点号。 */
    private static final String DEFAULT_PREFIX_DELIMITERS = ":_-.";

    /** 自定义 Redis key 前缀分隔符系统属性。 */
    private static final String PREFIX_DELIMITERS_PROPERTY = "chat2db.redis.key-prefix.delimiters";

    /** 自定义 Redis key 前缀分隔符环境变量。 */
    private static final String PREFIX_DELIMITERS_ENV = "CHAT2DB_REDIS_KEY_PREFIX_DELIMITERS";


    @Autowired
    private LuceneIndexManagerFactory managerFactory;

    @Autowired
    private MetadataSnapshotService metadataSnapshotService;

    /**
     * 刷新任务状态缓存。
     *
     * <p>key 由数据源 ID 和 Redis 数据库名组成，同一个数据源的不同 Redis 数据库互不影响。</p>
     */
    private final Map<String, RedisKeyPrefixBuildStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 从 Lucene 索引中分页搜索 Redis key 前缀。
     *
     * <p>带通配符的搜索关键字按 Redis 前缀做开头匹配，避免 Lucene 全文分词把中间包含相同片段的
     * 其它前缀混入结果；普通关键字仍支持按前缀名称和注释搜索，并优先展示前缀名称命中的结果。
     * 返回结果会附带当前数据源/库是否正在构建，前端可据此展示刷新中的提示。</p>
     */
    @Override
    public List<RedisKeyPrefix> search(RedisKeyPrefixQueryParam param) {
        LuceneIndexManager<RedisKeyPrefix> luceneMgr = managerFactory.getManager(param.getDataSourceId());
        String sortField = normalizeSortField(param.getSortField());
        boolean reverse = "descend".equalsIgnoreCase(param.getSortOrder());
        String prefixSearchKey = normalizePrefixSearchKey(param.getSearchKey());
        List<RedisKeyPrefix> prefixes;
        if (StringUtils.isNotBlank(prefixSearchKey)) {
            prefixes = searchByPrefix(luceneMgr, param, prefixSearchKey, isWildcardPrefixSearch(param.getSearchKey()),
                    sortField, reverse);
        } else {
            prefixes = luceneMgr.search(param, param.getLastDocId(), "", sortField, reverse);
            param.setLastDocId(luceneMgr.getLastDocId());
        }
        RedisKeyPrefixBuildStatus status = getStatus(param.getDataSourceId(), param.getDatabaseName());
        boolean building = STATUS_RUNNING.equals(status.getStatus());
        prefixes.forEach(prefix -> prefix.setBuilding(building));
        return prefixes;
    }

    private List<RedisKeyPrefix> searchByPrefix(LuceneIndexManager<RedisKeyPrefix> luceneMgr,
                                                 RedisKeyPrefixQueryParam param,
                                                 String prefixSearchKey,
                                                 boolean wildcardPrefixSearch,
                                                 String sortField,
                                                 boolean reverse) {
        int pageSize = param.getPageSize() == null ? 1000 : param.getPageSize();
        List<RedisKeyPrefix> prefixes = luceneMgr.searchAll(param, "")
                .stream()
                .filter(prefix -> isPrefixSearchMatch(prefix, prefixSearchKey, wildcardPrefixSearch))
                .sorted(buildPrefixSearchComparator(prefixSearchKey, sortField, reverse))
                .limit(pageSize)
                .toList();
        param.setLastDocId(null);
        return prefixes;
    }

    /**
     * 启动 Redis key 前缀缓存刷新。
     *
     * <p>同一数据源/库同时只允许一个刷新任务运行。如果已有任务处于 RUNNING 状态，
     * 本次调用会直接返回。刷新任务在独立线程中执行，并复制连接信息，避免异步线程读取到
     * 调用线程后续变更的上下文数据。</p>
     */
    @Override
    public String refresh(Long dataSourceId, String databaseName, ConnectInfo connectInfo) {
        // 仅在当前数据源/库的刷新任务未运行时，才启动新的异步刷新任务。
        String statusKey = buildStatusKey(dataSourceId, databaseName);
        RedisKeyPrefixBuildStatus currentStatus = statusMap.get(statusKey);
        if (currentStatus != null && STATUS_RUNNING.equals(currentStatus.getStatus())) {
            return statusKey;
        }
        ConnectInfo asyncConnectInfo = connectInfo.copy();
        RedisKeyPrefixBuildStatus status = RedisKeyPrefixBuildStatus.builder()
                .status(STATUS_RUNNING)
                .cursor("0")
                .scannedCount(0L)
                .prefixCount(0L)
                .startedAt(now())
                .updatedAt(now())
                .build();
        statusMap.put(statusKey, status);
        updateRedisSnapshots(dataSourceId, databaseName, 0L, 0L, MetadataSnapshotService.STATUS_REFRESHING, null);

        CompletableFuture.runAsync(() -> doRefresh(dataSourceId, databaseName, asyncConnectInfo, statusKey));
        return statusKey;
    }

    /**
     * 更新指定 Redis key 前缀的备注。
     *
     * <p>更新前会先读取索引中已存在的文档。这样用户维护的备注
     * 不会因为版本字段缺失而在下一次清理旧索引数据时被误删。</p>
     */
    @Override
    public long updateComment(Long dataSourceId, String databaseName, String prefix, String comment) {
        LuceneIndexManager<RedisKeyPrefix> luceneMgr = managerFactory.getManager(dataSourceId);
        RedisKeyPrefix source = RedisKeyPrefix.builder()
                .dataSourceId(dataSourceId)
                .databaseName(databaseName)
                .name(prefix)
                .comment(comment)
                .build();
        return luceneMgr.updateDocument(source);
    }

    /**
     * 从 Lucene 索引中删除指定 Redis key 前缀。
     */
    @Override
    public void delete(Long dataSourceId, String databaseName, String prefix) {
        RedisKeyPrefix source = RedisKeyPrefix.builder()
                .dataSourceId(dataSourceId)
                .databaseName(databaseName)
                .name(prefix)
                .build();
        managerFactory.<RedisKeyPrefix>getManager(dataSourceId).deleteDocument(source);
    }

    /**
     * 查询指定数据源/库的刷新任务状态。
     *
     * <p>如果当前进程内没有该数据源/库的刷新记录，则返回 IDLE 状态，表示尚未启动过刷新任务。</p>
     */
    @Override
    public RedisKeyPrefixBuildStatus getStatus(Long dataSourceId, String databaseName) {
        RedisKeyPrefixBuildStatus memoryStatus = statusMap.get(buildStatusKey(dataSourceId, databaseName));
        if (memoryStatus != null) {
            return memoryStatus;
        }
        RedisKeyPrefixBuildStatus snapshotStatus = querySnapshotStatus(dataSourceId, databaseName);
        if (snapshotStatus != null) {
            return snapshotStatus;
        }
        return RedisKeyPrefixBuildStatus.builder()
                .status(STATUS_IDLE)
                .cursor("0")
                .scannedCount(0L)
                .prefixCount(0L)
                .build();
    }

    /**
     * 异步刷新 Redis 前缀缓存：流式扫描 key、统计前缀数量，并写入 Lucene 索引。
     *
     * <p>扫描过程中会持续更新 {@link #statusMap} 中的进度信息，包括已扫描 key 数量、
     * 当前前缀数量和最后更新时间。
     * 确保索引中只保留最新扫描结果。</p>
     */
    private void doRefresh(Long dataSourceId, String databaseName, ConnectInfo connectInfo, String statusKey) {
        // 根据 refresh 方法提前写入的 statusKey，取出本轮异步刷新任务的状态对象。
        RedisKeyPrefixBuildStatus status = statusMap.get(statusKey);

        // 候选前缀计数表：key 是 Redis 前缀候选，value 是该候选覆盖的 key 数量。
        Map<String, Long> prefixCountMap = new ConcurrentHashMap<>();

        // 已扫描的 Redis key 总数，用 AtomicLong 便于在流式回调中安全累加。
        AtomicLong scannedCount = new AtomicLong();

        try {
            // 异步线程没有调用线程的 ThreadLocal 上下文，需要先放入连接信息。
            Chat2DBContext.putContext(connectInfo);

            // 从上下文中的数据源元信息获取 Redis key 浏览器，用于流式扫描 key。
            RedisKeyBrowser browser = getRedisKeyBrowser();

            // 获取当前数据源对应的 RedisKeyPrefix Lucene 索引管理器。
            LuceneIndexManager<RedisKeyPrefix> luceneMgr = managerFactory.getManager(dataSourceId);

            // 流式遍历 Redis key，只按 key 名提取前缀并统计出现次数。
            browser.streamPrefixKeys(databaseName, keys -> {
                // 类型采样延后到最长前缀筛选完成后，避免全量扫描阶段逐 key 识别类型。
                keys.forEach(key -> collectPrefixes(key.getName(), prefixCountMap));

                // 累加本批次 key 数量，得到当前任务已经扫描的 key 总数。
                long scanned = scannedCount.addAndGet(keys.size());

                // 更新任务进度，供前端轮询展示刷新进度。
                status.setScannedCount(scanned);
                // 前缀数量取当前 map 的大小，表示已经发现多少个不同前缀。
                status.setPrefixCount((long) prefixCountMap.size());
                // 每处理一个批次都刷新更新时间，便于判断任务是否仍在推进。
                status.setUpdatedAt(now());

            }).join();

            Map<String, Long> selectedPrefixCountMap = selectLongestPrefixes(prefixCountMap);

            // 最后把剩余前缀一次性写入索引，并清理本轮刷新前的旧版本数据。
            flushPrefixes(luceneMgr, dataSourceId, databaseName, selectedPrefixCountMap);
            // 扫描和索引写入全部完成后，标记任务成功。
            status.setStatus(STATUS_DONE);
            // 当前扫描接口未暴露游标恢复能力，完成后统一重置为初始游标。
            status.setCursor("0");
            // 最终前缀数量以完整扫描后的聚合结果为准。
            status.setPrefixCount((long) selectedPrefixCountMap.size());
            // 记录任务完成时间。
            status.setUpdatedAt(now());
            updateRedisSnapshots(dataSourceId, databaseName, scannedCount.get(), (long) selectedPrefixCountMap.size(),
                    MetadataSnapshotService.STATUS_READY, null);
        } catch (Exception e) {
            log.error("Refresh redis key prefix cache failed, dataSourceId={}, databaseName={}",
                    dataSourceId, databaseName, e);
            status.setStatus(STATUS_FAILED);
            status.setErrorMessage(e.getMessage());
            // 记录失败时间。
            status.setUpdatedAt(now());
            updateRedisSnapshots(dataSourceId, databaseName, scannedCount.get(), (long) prefixCountMap.size(),
                    MetadataSnapshotService.STATUS_FAILED, e.getMessage());
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    /**
     * 从当前 Chat2DB 上下文中获取 Redis key 浏览能力。
     *
     * <p>该服务只适用于 Redis 数据源。如果当前数据源元信息不支持 {@link RedisKeyBrowser}，
     * 说明调用方传入了非 Redis 连接，此时直接抛出业务异常。</p>
     */
    private RedisKeyBrowser getRedisKeyBrowser() {
        MetaData metaData = Chat2DBContext.getMetaData();
        if (metaData instanceof RedisKeyBrowser browser) {
            return browser;
        }
        throw new BusinessException("当前数据源不是 Redis");
    }

    /**
     * 从单个 Redis key 中提取前缀并累计出现次数。
     *
     * <p>只在分隔符位置截断，截断结果保留分隔符，避免把 {@code us}、{@code use} 这类普通字符
     * 误判为前缀。例如 {@code user:1001:profile} 会产生 {@code user:} 和
     * {@code user:1001:} 两个候选。</p>
     */
    private void collectPrefixes(String key, Map<String, Long> prefixCountMap) {
        collectPrefixes(key, prefixCountMap, resolvePrefixDelimiters());
    }

    static Map<String, Long> buildPrefixCountMap(Collection<String> keys, String delimiters) {
        Map<String, Long> candidatePrefixCountMap = new ConcurrentHashMap<>();
        keys.forEach(key -> collectPrefixes(key, candidatePrefixCountMap, delimiters));
        return selectLongestPrefixes(candidatePrefixCountMap);
    }

    private static void collectPrefixes(String key, Map<String, Long> prefixCountMap, String delimiters) {
        extractPrefixCandidates(key, delimiters).forEach(prefix -> prefixCountMap.merge(prefix, 1L, Long::sum));
    }

    private static List<String> extractPrefixCandidates(String key, String delimiters) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(delimiters)) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, key.length())
                .filter(index -> index > 0 && index < key.length() - 1)
                .filter(index -> delimiters.indexOf(key.charAt(index)) >= 0)
                .mapToObj(index -> key.substring(0, index + 1))
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    static Map<String, Long> selectLongestPrefixes(Map<String, Long> candidatePrefixCountMap) {
        List<Map.Entry<String, Long>> candidates = candidatePrefixCountMap.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .sorted(Map.Entry.comparingByKey())
                .toList();
        Map<String, Long> longestPrefixes = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            Map.Entry<String, Long> current = candidates.get(index);
            String nextPrefix = index + 1 < candidates.size() ? candidates.get(index + 1).getKey() : null;
            if (nextPrefix == null || !nextPrefix.startsWith(current.getKey())) {
                longestPrefixes.put(current.getKey(), current.getValue());
            }
        }
        return longestPrefixes.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingInt(entry -> entry.getKey().length())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private static String resolvePrefixDelimiters() {
        String propertyDelimiters = StringUtils.trimToNull(System.getProperty(PREFIX_DELIMITERS_PROPERTY));
        if (propertyDelimiters != null) {
            return propertyDelimiters;
        }
        return StringUtils.defaultIfBlank(System.getenv(PREFIX_DELIMITERS_ENV), DEFAULT_PREFIX_DELIMITERS);
    }

    /**
     * 将当前内存中的前缀聚合结果分批写入 Lucene 索引。
     *
     * <p>这里使用 updateDocuments 而不是简单追加，保证同名数据源/库/前缀的文档会被更新。
     */
    private void flushPrefixes(LuceneIndexManager<RedisKeyPrefix> luceneMgr, Long dataSourceId, String databaseName,
                               Map<String, Long> prefixCountMap) {
        if (prefixCountMap.isEmpty()) {
            log.info("Skip flushing redis key prefixes because prefixCountMap is empty, dataSourceId={}, "
                            + "databaseName={}",
                    dataSourceId, databaseName);
            return;
        }
        List<String> samples = prefixCountMap.entrySet().stream()
                .limit(10)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        log.info("Start flushing redis key prefixes, dataSourceId={}, databaseName={},"
                        + "prefixCount={}, samplePrefixes={}",
                dataSourceId, databaseName, prefixCountMap.size(), samples);
        List<RedisKeyPrefix> prefixes = prefixCountMap.entrySet().stream()
                .map(entry -> RedisKeyPrefix.builder()
                        .dataSourceId(dataSourceId)
                        .databaseName(databaseName)
                        .name(entry.getKey())
                        .keyCount(entry.getValue())
                        .build())
                .toList();
        luceneMgr.updateDocuments(prefixes, null, true);
        
        log.info("Finished flushing redis key prefixes, dataSourceId={}, databaseName={}, "
                        + "prefixCount={}",
                dataSourceId, databaseName, prefixes.size());
    }


    /**
     * 构建刷新状态缓存 key。
     *
     * <p>状态按数据源和 Redis 数据库隔离，避免同一数据源下不同数据库的刷新状态互相覆盖。</p>
     */
    private String buildStatusKey(Long dataSourceId, String databaseName) {
        return dataSourceId + ":" + StringUtils.defaultString(databaseName);
    }

    private void updateRedisSnapshots(Long dataSourceId, String databaseName, Long keyCount, Long prefixCount,
                                      String status, String errorMessage) {
        Dbutils.runWithSession(() -> {
            String redisKeyObjectKey = metadataSnapshotService.buildObjectKey(MetadataSnapshotService.TYPE_REDIS_KEY,
                    databaseName, null, databaseName);
            metadataSnapshotService.upsertCount(dataSourceId, MetadataSnapshotService.TYPE_REDIS_KEY, redisKeyObjectKey,
                    databaseName, null, databaseName, keyCount, null, status, errorMessage);

            String redisPrefixObjectKey = metadataSnapshotService.buildObjectKey(
                    MetadataSnapshotService.TYPE_REDIS_PREFIX, databaseName, null, databaseName);
            metadataSnapshotService.upsertCount(dataSourceId, MetadataSnapshotService.TYPE_REDIS_PREFIX,
                    redisPrefixObjectKey, databaseName, null, databaseName, prefixCount, null, status, errorMessage);
        });
    }

    private RedisKeyPrefixBuildStatus querySnapshotStatus(Long dataSourceId, String databaseName) {
        String redisKeyObjectKey = metadataSnapshotService.buildObjectKey(MetadataSnapshotService.TYPE_REDIS_KEY,
                databaseName, null, databaseName);
        String redisPrefixObjectKey = metadataSnapshotService.buildObjectKey(MetadataSnapshotService.TYPE_REDIS_PREFIX,
                databaseName, null, databaseName);
        MetadataSnapshot redisKeySnapshot = metadataSnapshotService.get(dataSourceId,
                MetadataSnapshotService.TYPE_REDIS_KEY, redisKeyObjectKey);
        MetadataSnapshot redisPrefixSnapshot = metadataSnapshotService.get(dataSourceId,
                MetadataSnapshotService.TYPE_REDIS_PREFIX, redisPrefixObjectKey);
        if (redisKeySnapshot == null && redisPrefixSnapshot == null) {
            return null;
        }
        String status = redisPrefixSnapshot == null ? redisKeySnapshot.getStatus() : redisPrefixSnapshot.getStatus();
        return RedisKeyPrefixBuildStatus.builder()
                .status(toBuildStatus(status))
                .cursor("0")
                .scannedCount(redisKeySnapshot == null ? 0L : redisKeySnapshot.getObjectCount())
                .prefixCount(redisPrefixSnapshot == null ? 0L : redisPrefixSnapshot.getObjectCount())
                .updatedAt(redisPrefixSnapshot == null || redisPrefixSnapshot.getRefreshedAt() == null
                        ? null : redisPrefixSnapshot.getRefreshedAt().toString())
                .errorMessage(redisPrefixSnapshot == null ? null : redisPrefixSnapshot.getErrorMessage())
                .build();
    }

    private String toBuildStatus(String snapshotStatus) {
        if (MetadataSnapshotService.STATUS_REFRESHING.equals(snapshotStatus)) {
            return STATUS_RUNNING;
        }
        if (MetadataSnapshotService.STATUS_READY.equals(snapshotStatus)
                || MetadataSnapshotService.STATUS_EMPTY.equals(snapshotStatus)) {
            return STATUS_DONE;
        }
        if (MetadataSnapshotService.STATUS_FAILED.equals(snapshotStatus)) {
            return STATUS_FAILED;
        }
        return STATUS_IDLE;
    }

    private String normalizeSortField(String sortField) {
        if ("keyCount".equals(sortField)) {
            return "keyCount";
        }
        return DEFAULT_SORT_FIELD;
    }

    static String normalizePrefixSearchKey(String searchKey) {
        String normalized = StringUtils.trimToEmpty(searchKey);
        while (normalized.endsWith("*")) {
            normalized = StringUtils.trimToEmpty(normalized.substring(0, normalized.length() - 1));
        }
        return normalized;
    }

    static boolean isWildcardPrefixSearch(String searchKey) {
        return StringUtils.trimToEmpty(searchKey).endsWith("*");
    }

    static boolean isPrefixSearchMatch(RedisKeyPrefix prefix, String prefixSearchKey, boolean wildcardPrefixSearch) {
        if (prefix == null || StringUtils.isBlank(prefixSearchKey)) {
            return false;
        }
        if (isPrefixNameMatch(prefix, prefixSearchKey)) {
            return true;
        }
        return !wildcardPrefixSearch && (containsIgnoreCase(prefix.getComment(), prefixSearchKey)
                || containsIgnoreCase(prefix.getAiComment(), prefixSearchKey));
    }

    private static boolean isPrefixNameMatch(RedisKeyPrefix prefix, String prefixSearchKey) {
        return StringUtils.defaultString(prefix.getName()).startsWith(prefixSearchKey);
    }

    private static boolean containsIgnoreCase(String value, String searchKey) {
        return StringUtils.containsIgnoreCase(StringUtils.defaultString(value), searchKey);
    }

    private Comparator<RedisKeyPrefix> buildPrefixSearchComparator(String prefixSearchKey, String sortField,
                                                                    boolean reverse) {
        Comparator<RedisKeyPrefix> comparator;
        if ("keyCount".equals(sortField)) {
            comparator = Comparator.comparing(
                    RedisKeyPrefix::getKeyCount,
                    Comparator.nullsLast(Long::compareTo));
        } else {
            comparator = Comparator.comparing(
                    prefix -> StringUtils.defaultString(prefix.getName()),
                    String::compareTo);
        }
        if (reverse) {
            comparator = comparator.reversed();
        }
        return Comparator.comparingInt((RedisKeyPrefix prefix) -> isPrefixNameMatch(prefix, prefixSearchKey) ? 0 : 1)
                .thenComparing(comparator)
                .thenComparing(prefix -> StringUtils.defaultString(prefix.getName()));
    }

    /**
     * 生成状态时间戳字符串。
     */
    private String now() {
        return LocalDateTime.now().toString();
    }
}
