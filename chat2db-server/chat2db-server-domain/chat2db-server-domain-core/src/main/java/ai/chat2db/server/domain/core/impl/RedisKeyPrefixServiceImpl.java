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

/**
 * Redis key 前缀索引服务实现。
 *
 * <p>该服务用于把 Redis 中数量可能非常大的 key 按业务前缀聚合后写入 Lucene 索引，
 * 便于前端分页搜索、展示 key 数量以及维护前缀备注。刷新过程采用异步执行，避免扫描
 * Redis 时阻塞调用线程。</p>
 *
 * <p>前缀提取规则：仅处理包含冒号分隔符的 key，并把最后一段之前的内容作为前缀。
 * 例如 {@code user:1001:profile} 会聚合到 {@code user:1001}。</p>
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

    /** 每扫描到指定数量的 key 后，将当前聚合结果刷新到 Lucene，控制长时间扫描时的内存增长。 */
    private static final long FLUSH_KEY_THRESHOLD = 50000L;

    /** 单次写入 Lucene 的文档数量，避免一次性提交过多文档导致索引写入压力过大。 */
    private static final int FLUSH_DOCUMENT_BATCH_SIZE = 500;

    @Autowired
    private LuceneIndexManagerFactory managerFactory;

    /**
     * 刷新任务状态缓存。
     *
     * <p>key 由数据源 ID 和 Redis 数据库名组成，同一个数据源的不同 Redis 数据库互不影响。</p>
     */
    private final Map<String, RedisKeyPrefixBuildStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 从 Lucene 索引中分页搜索 Redis key 前缀。
     *
     * <p>搜索关键字会先经过 {@link QueryParser#escape(String)} 转义，避免用户输入 Lucene
     * 查询语法中的特殊字符时破坏查询表达式。返回结果会附带当前数据源/库是否正在构建，
     * 前端可据此展示刷新中的提示。</p>
     */
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
        return statusKey;
    }

    /**
     * 更新指定 Redis key 前缀的备注。
     *
     * <p>更新前会先读取索引中已存在的文档，并保留其 refreshVersion。这样用户维护的备注
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
        RedisKeyPrefix storedPrefix = luceneMgr.findDocument(source);
        if (storedPrefix != null) {
            source.setRefreshVersion(storedPrefix.getRefreshVersion());
        }
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
        return statusMap.getOrDefault(buildStatusKey(dataSourceId, databaseName),
                RedisKeyPrefixBuildStatus.builder()
                        .status(STATUS_IDLE)
                        .cursor("0")
                        .scannedCount(0L)
                        .prefixCount(0L)
                        .build());
    }

    /**
     * 异步刷新 Redis 前缀缓存：流式扫描 key、统计前缀数量，并写入 Lucene 索引。
     *
     * <p>扫描过程中会持续更新 {@link #statusMap} 中的进度信息，包括已扫描 key 数量、
     * 当前前缀数量和最后更新时间。扫描完成后，会使用本轮 refreshVersion 清理旧版本文档，
     * 确保索引中只保留最新扫描结果。</p>
     */
    private void doRefresh(Long dataSourceId, String databaseName, ConnectInfo connectInfo, String statusKey) {
        // 根据 refresh 方法提前写入的 statusKey，取出本轮异步刷新任务的状态对象。
        RedisKeyPrefixBuildStatus status = statusMap.get(statusKey);

        // 前缀计数表：key 是提取出的 Redis 前缀，value 是该前缀下匹配到的 key 数量。
        Map<String, Long> prefixCountMap = new ConcurrentHashMap<>();

        // 已扫描的 Redis key 总数，用 AtomicLong 便于在流式回调中安全累加。
        AtomicLong scannedCount = new AtomicLong();

        // 本轮刷新版本号，所有本次扫描写入的 Lucene 文档都会带上该版本。
        Long refreshVersion = status.getRefreshVersion();
        try {
            // 异步线程没有调用线程的 ThreadLocal 上下文，需要先放入连接信息。
            Chat2DBContext.putContext(connectInfo);

            // 从上下文中的数据源元信息获取 Redis key 浏览器，用于流式扫描 key。
            RedisKeyBrowser browser = getRedisKeyBrowser();

            // 获取当前数据源对应的 RedisKeyPrefix Lucene 索引管理器。
            LuceneIndexManager<RedisKeyPrefix> luceneMgr = managerFactory.getManager(dataSourceId);

            // 流式遍历 Redis key，按分隔符提取前缀并统计出现次数。
            browser.streamKeyNames(databaseName, null, "0", -1, keys -> {
                // 对当前批次返回的每个 key 提取前缀，并累计到 prefixCountMap。
                keys.forEach(key -> collectPrefixes(key, prefixCountMap));

                // 累加本批次 key 数量，得到当前任务已经扫描的 key 总数。
                long scanned = scannedCount.addAndGet(keys.size());

                // 更新任务进度，供前端轮询展示刷新进度。
                status.setScannedCount(scanned);
                // 前缀数量取当前 map 的大小，表示已经发现多少个不同前缀。
                status.setPrefixCount((long) prefixCountMap.size());
                // 每处理一个批次都刷新更新时间，便于判断任务是否仍在推进。
                status.setUpdatedAt(now());

            }).join();

            // 最后把剩余前缀一次性写入索引，并清理本轮刷新前的旧版本数据。
            flushPrefixes(luceneMgr, dataSourceId, databaseName, refreshVersion, prefixCountMap);
            // 扫描和索引写入全部完成后，标记任务成功。
            status.setStatus(STATUS_DONE);
            // 当前扫描接口未暴露游标恢复能力，完成后统一重置为初始游标。
            status.setCursor("0");
            // 最终前缀数量以完整扫描后的聚合结果为准。
            status.setPrefixCount((long) prefixCountMap.size());
            // 记录任务完成时间。
            status.setUpdatedAt(now());
        } catch (Exception e) {
            log.error("Refresh redis key prefix cache failed, dataSourceId={}, databaseName={}",
                    dataSourceId, databaseName, e);
            status.setStatus(STATUS_FAILED);
            status.setErrorMessage(e.getMessage());
            // 记录失败时间。
            status.setUpdatedAt(now());
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
     * <p>只处理包含 {@code :} 的 key；空片段会被忽略，最后一个片段通常代表具体业务对象
     * 或字段，不计入前缀。例如 {@code order::2024:1001} 会被规整为 {@code order:2024}。</p>
     */
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
        if (parts.size() < 2) {
            log.debug("Skip redis key prefix because valid key parts are fewer than 2, key={}", key);
            return;
        }
        StringJoiner joiner = new StringJoiner(":");
        for (int i = 0; i < parts.size() - 1; i++) {
            joiner.add(parts.get(i));
        }
        String prefix = joiner.toString();
        if (StringUtils.isBlank(prefix)) {
            log.debug("Skip blank redis key prefix, key={}", key);
            return;
        }
        prefixCountMap.merge(prefix, 1L, Long::sum);
    }

    /**
     * 将当前内存中的前缀聚合结果分批写入 Lucene 索引。
     *
     * <p>这里使用 updateDocuments 而不是简单追加，保证同名数据源/库/前缀的文档会被更新。
     * 每个文档都带上本轮 refreshVersion，后续可据此区分本轮扫描结果和历史残留结果。</p>
     */
    private void flushPrefixes(LuceneIndexManager<RedisKeyPrefix> luceneMgr, Long dataSourceId, String databaseName,
                               Long refreshVersion, Map<String, Long> prefixCountMap) {
        if (prefixCountMap.isEmpty()) {
            log.info("Skip flushing redis key prefixes because prefixCountMap is empty, dataSourceId={}, "
                            + "databaseName={}, refreshVersion={}",
                    dataSourceId, databaseName, refreshVersion);
            return;
        }
        List<String> samples = prefixCountMap.entrySet().stream()
                .limit(10)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        log.info("Start flushing redis key prefixes, dataSourceId={}, databaseName={}, refreshVersion={}, "
                        + "prefixCount={}, samplePrefixes={}",
                dataSourceId, databaseName, refreshVersion, prefixCountMap.size(), samples);
        List<RedisKeyPrefix> prefixes = prefixCountMap.entrySet().stream()
                .map(entry -> RedisKeyPrefix.builder()
                        .dataSourceId(dataSourceId)
                        .databaseName(databaseName)
                        .name(entry.getKey())
                        .keyCount(entry.getValue())
                        .refreshVersion(refreshVersion)
                        .build())
                .toList();
        luceneMgr.updateDocuments(prefixes, null, true);
        
        log.info("Finished flushing redis key prefixes, dataSourceId={}, databaseName={}, refreshVersion={}, "
                        + "prefixCount={}",
                dataSourceId, databaseName, refreshVersion, prefixes.size());
    }


    /**
     * 构建刷新状态缓存 key。
     *
     * <p>状态按数据源和 Redis 数据库隔离，避免同一数据源下不同数据库的刷新状态互相覆盖。</p>
     */
    private String buildStatusKey(Long dataSourceId, String databaseName) {
        return dataSourceId + ":" + StringUtils.defaultString(databaseName);
    }

    /**
     * 生成状态时间戳字符串。
     */
    private String now() {
        return LocalDateTime.now().toString();
    }
}
