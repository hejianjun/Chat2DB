package ai.chat2db.server.domain.core.impl;

import java.sql.Connection;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import ai.chat2db.server.domain.api.model.MetadataSnapshot;
import ai.chat2db.server.domain.api.model.TreeNode;
import ai.chat2db.server.domain.api.param.DeprecatedTableParam;
import ai.chat2db.server.domain.api.param.DropParam;
import ai.chat2db.server.domain.api.param.PinTableParam;
import ai.chat2db.server.domain.api.param.ShowCreateTableParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.domain.api.param.TableSelector;
import ai.chat2db.server.domain.api.param.TreeSearchParam;
import ai.chat2db.server.domain.api.param.TypeQueryParam;
import ai.chat2db.server.domain.api.service.DeprecatedTableService;
import ai.chat2db.server.domain.api.service.MetadataSnapshotService;
import ai.chat2db.server.domain.api.service.PinService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.domain.core.cache.LuceneIndexManager;
import ai.chat2db.server.domain.core.cache.LuceneIndexManagerFactory;
import ai.chat2db.server.domain.core.converter.PinTableConverter;
import ai.chat2db.server.domain.core.util.TableTagUtils;
import ai.chat2db.server.tools.base.enums.DataSourceTypeEnum;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.enums.EditStatus;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.IndexType;
import ai.chat2db.spi.model.SimpleTable;
import ai.chat2db.spi.model.Sql;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.model.TableIndexColumn;
import ai.chat2db.spi.model.TableMeta;
import ai.chat2db.spi.model.Type;
import ai.chat2db.spi.model.VirtualForeignKey;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Service
@Slf4j
public class TableServiceImpl implements TableService {

    @Autowired
    private PinService pinService;

    @Autowired
    private PinTableConverter pinTableConverter;

    @Autowired
    private DeprecatedTableService deprecatedTableService;

    @Autowired
    private ForeignKeySyncService foreignKeySyncService;

    @Autowired
    private MetadataSnapshotService metadataSnapshotService;

    @Autowired
    private LuceneIndexManagerFactory managerFactory;

    @Override
    public String showCreateTable(ShowCreateTableParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        String dbType = Chat2DBContext.getConnectInfo() == null ? null : Chat2DBContext.getConnectInfo().getDbType();
        log.info("show create table start, dbType:{}, metaDataClass:{}, dataSourceId:{}, databaseName:{}, schemaName:{}, tableName:{}",
                dbType, metaSchema == null ? null : metaSchema.getClass().getName(), param.getDataSourceId(),
                param.getDatabaseName(), param.getSchemaName(), param.getTableName());
        String ddl = metaSchema.tableDDL(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                param.getTableName());
        if (StringUtils.isBlank(ddl)) {
            log.warn("show create table returned blank ddl, dbType:{}, dataSourceId:{}, databaseName:{}, schemaName:{}, tableName:{}",
                    dbType, param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(),
                    param.getTableName());
        } else {
            log.info("show create table success, dbType:{}, dataSourceId:{}, databaseName:{}, schemaName:{}, tableName:{}, ddlLength:{}",
                    dbType, param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(),
                    param.getTableName(), ddl.length());
        }
        return ddl;
    }

    @Override
    public void drop(DropParam param) {
        DBManage metaSchema = Chat2DBContext.getDBManage();
        metaSchema.dropTable(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                param.getTableName());
    }

    @Override
    public String createTableExample(String dbType) {
        return Chat2DBContext.getDBConfig().getSimpleCreateTable();
    }

    @Override
    public String alterTableExample(String dbType) {
        return Chat2DBContext.getDBConfig().getSimpleAlterTable();
    }

    @Override
    public Table query(TableQueryParam param, TableSelector selector) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        Connection connection =  Chat2DBContext.getConnection();
        List<Table> tables = metaSchema.tables(connection, param.getDatabaseName(),
                param.getSchemaName(), param.getTableName());
        if (!CollectionUtils.isEmpty(tables)) {
            Table table = tables.get(0);
            table.setIndexList(
                    metaSchema.indexes(connection, param.getDatabaseName(), param.getSchemaName(),
                            param.getTableName()));
            table.setColumnList(
                    metaSchema.columns(connection, param.getDatabaseName(), param.getSchemaName(),
                            param.getTableName()));
            metaSchema.enrichTable(connection, param.getDatabaseName(), param.getSchemaName(), table);
            table.setForeignKeyList(
                    metaSchema.foreignKeys(connection, param.getDatabaseName(),
                            param.getSchemaName(), param.getTableName()));
            setPrimaryKey(table);
            return table;
        }
        return null;
    }

    private void setPrimaryKey(Table table) {
        if (table == null) {
            return;
        }
        List<TableIndex> tableIndices = table.getIndexList();
        if (CollectionUtils.isEmpty(tableIndices)) {
            return;
        }
        List<TableColumn> columns = table.getColumnList();
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        Map<String, TableColumn> columnMap = columns.stream()
                .collect(Collectors.toMap(TableColumn::getName, Function.identity()));
        for (TableIndex tableIndex : tableIndices) {
            if ("Primary".equalsIgnoreCase(tableIndex.getType())) {
                List<TableIndexColumn> indexColumns = tableIndex.getColumnList();
                if (CollectionUtils.isNotEmpty(indexColumns)) {
                    for (TableIndexColumn indexColumn : indexColumns) {
                        TableColumn column = columnMap.get(indexColumn.getColumnName());
                        if (column != null) {
                            column.setPrimaryKey(true);
                            column.setPrimaryKeyOrder(indexColumn.getOrdinalPosition());
                            column.setPrimaryKeyName(tableIndex.getName());
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<Sql> buildSql(Table oldTable, Table newTable) {
        initOldTable(oldTable, newTable);
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        List<Sql> sqls = new ArrayList<>();
        if (oldTable == null) {
            initPrimaryKey(newTable);
            sqls.add(Sql.builder().sql(sqlBuilder.buildCreateTableSql(newTable)).build());
        } else {
            initUpdatePrimaryKey(oldTable, newTable);
            sqls.add(Sql.builder().sql(sqlBuilder.buildModifyTaleSql(oldTable, newTable)).build());
        }
        return sqls;
    }

    @Override
    public List<String> buildBatchSql(List<Table> oldTables, List<Table> newTables) {
        if (oldTables.size() != newTables.size()) {
            throw new IllegalArgumentException("Old tables and new tables lists must have the same size.");
        }
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        return IntStream.range(0, oldTables.size())
                .mapToObj(i -> sqlBuilder.buildModifyTaleSql(oldTables.get(i), newTables.get(i)))
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());
    }

    private void initUpdatePrimaryKey(Table oldTable, Table newTable) {
        if (newTable == null || oldTable == null) {
            return;
        }
        List<TableColumn> newColumns = getPrimaryKeyColumn(newTable);
        List<TableColumn> oldColumns = getPrimaryKeyColumn(oldTable);
        if (CollectionUtils.isEmpty(newColumns) && CollectionUtils.isEmpty(oldColumns)) {
            return;
        }
        if (!CollectionUtils.isEmpty(newColumns) && CollectionUtils.isEmpty(oldColumns)) {
            initPrimaryKey(newTable);
            return;
        }
        if (CollectionUtils.isEmpty(newColumns) && CollectionUtils.isNotEmpty(oldColumns)) {
            addPrimaryKey(newTable, oldColumns.get(0), EditStatus.DELETE.name());
            return;
        }
        if (newColumns.size() != oldColumns.size()) {
            for (TableColumn column : newColumns) {
                if (column.getPrimaryKey() != null && column.getPrimaryKey()) {
                    addPrimaryKey(newTable, column, EditStatus.MODIFY.name());
                }
            }
            return;
        }
        boolean flag = false;
        Map<String, TableColumn> oldColumnMap = oldColumns.stream()
                .collect(Collectors.toMap(TableColumn::getName, Function.identity()));
        for (TableColumn column : newColumns) {
            TableColumn oldColumn = oldColumnMap.get(column.getName());
            if (oldColumn == null) {
                flag = true;
            }
        }
        if (flag) {
            for (TableColumn column : newColumns) {
                if (column.getPrimaryKey() != null && column.getPrimaryKey()) {
                    addPrimaryKey(newTable, column, EditStatus.MODIFY.name());
                }
            }
        }
    }

    private List<TableColumn> getPrimaryKeyColumn(Table table) {
        if (table == null || CollectionUtils.isEmpty(table.getColumnList())) {
            return null;
        }
        return table.getColumnList().stream()
                .filter(tableColumn -> tableColumn.getPrimaryKey() != null && tableColumn.getPrimaryKey())
                .collect(Collectors.toList());
    }

    private void initPrimaryKey(Table newTable) {
        if (newTable == null) {
            return;
        }
        List<TableColumn> columns = newTable.getColumnList();
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        for (TableColumn column : columns) {
            if (column.getPrimaryKey() != null && column.getPrimaryKey()) {
                addPrimaryKey(newTable, column, EditStatus.ADD.name());
            }
        }
    }

    private void addPrimaryKey(Table newTable, TableColumn column, String status) {
        List<TableIndex> indexes = newTable.getIndexList();
        if (indexes == null) {
            indexes = new ArrayList<>();
        }
        TableIndex keyIndex = indexes.stream().filter(index -> "Primary".equalsIgnoreCase(index.getType())).findFirst()
                .orElse(null);
        if (keyIndex == null) {
            keyIndex = new TableIndex();
            keyIndex.setType("Primary");
            keyIndex.setName(
                    StringUtils.isBlank(column.getPrimaryKeyName()) ? "PRIMARY_KEY" : column.getPrimaryKeyName());
            keyIndex.setTableName(newTable.getName());
            keyIndex.setSchemaName(newTable.getSchemaName());
            keyIndex.setDatabaseName(newTable.getDatabaseName());
            keyIndex.setEditStatus(status);
            if (!EditStatus.ADD.name().equals(status)) {
                keyIndex.setOldName(keyIndex.getName());
            }
            indexes.add(keyIndex);
        }
        List<TableIndexColumn> tableIndexColumns = keyIndex.getColumnList();
        if (tableIndexColumns == null) {
            tableIndexColumns = new ArrayList<>();
        }
        boolean exists = tableIndexColumns.stream()
                .anyMatch(indexColumn -> StringUtils.equalsIgnoreCase(indexColumn.getColumnName(), column.getName()));
        if (exists) {
            keyIndex.setColumnList(tableIndexColumns.stream()
                    .sorted(Comparator.comparing(TableIndexColumn::getOrdinalPosition))
                    .collect(Collectors.toList()));
            newTable.setIndexList(indexes);
            return;
        }
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName(column.getName());
        indexColumn.setTableName(newTable.getName());
        indexColumn.setSchemaName(newTable.getSchemaName());
        indexColumn.setDatabaseName(newTable.getDatabaseName());
        indexColumn.setOrdinalPosition(Short.valueOf(column.getPrimaryKeyOrder() + ""));
        indexColumn.setEditStatus(status);
        tableIndexColumns.add(indexColumn);
        List<TableIndexColumn> sortTableIndexColumns = tableIndexColumns.stream()
                .sorted(Comparator.comparing(TableIndexColumn::getOrdinalPosition)).collect(Collectors.toList());
        Set<String> statusList = sortTableIndexColumns.stream().map(TableIndexColumn::getEditStatus)
                .collect(Collectors.toSet());
        if (statusList.size() == 1) {
            // only one status ,set index status
            keyIndex.setEditStatus(statusList.iterator().next());
        } else {
            // more status ,set index status modify
            keyIndex.setEditStatus(EditStatus.MODIFY.name());
        }

        keyIndex.setColumnList(sortTableIndexColumns);
        newTable.setIndexList(indexes);

    }

    private void initOldTable(Table oldTable, Table newTable) {
        if (oldTable == null || newTable == null) {
            return;
        }
        Map<String, TableColumn> columnMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(oldTable.getColumnList())) {
            for (TableColumn column : oldTable.getColumnList()) {
                columnMap.put(column.getName(), column);
            }
        }
        if (CollectionUtils.isNotEmpty(newTable.getColumnList())) {
            for (TableColumn newColumn : newTable.getColumnList()) {
                if (EditStatus.ADD.name().equals(newColumn.getEditStatus())) {
                    continue;
                }
                String name = newColumn.getOldName() == null ? newColumn.getName() : newColumn.getOldName();
                TableColumn oldColumn = columnMap.get(name);
                if (oldColumn != null) {
                    if (oldColumn.equals(newColumn) && EditStatus.MODIFY.name().equals(newColumn.getEditStatus())) {
                        newColumn.setEditStatus(null);
                    } else {
                        newColumn.setOldColumn(oldColumn);
                    }
                }
            }
        }
    }

    @Override
    public List<Table> pageQuery(TablePageQueryParam param, TableSelector selector) {
        LuceneIndexManager<Table> luceneMgr = managerFactory.getManager(param.getDataSourceId());
        Long version = luceneMgr.getMaxVersion(param);
        if (needRefreshCache(param, version)) {
            if (!param.isRefresh() && isEmptyTableSnapshot(param)) {
                param.setLastDocId(null);
                return Collections.emptyList();
            }
            log.info("[TableCache] Cache penetration, reason:{}, dataSourceId:{}, databaseName:{}, schemaName:{}, tableName:{}",
                    getCacheRefreshReason(param.isRefresh(), version), param.getDataSourceId(), param.getDatabaseName(),
                    param.getSchemaName(), param.getTableName());
            loadAndCacheMetadata(luceneMgr, param.getDatabaseName(), param.getSchemaName(), version);
        }
        List<Table> tables;
        // ER 推断等场景传入 preferredTag 时，保持全文检索范围不变，只提升同 Tag 的命中分数。
        if (StringUtils.isNotBlank(param.getPreferredTag())) {
            tables = luceneMgr.searchWithPreferredTerm(param, param.getLastDocId(), param.getSearchKey(), "tag", param.getPreferredTag());
        } else if (StringUtils.isNotBlank(param.getSortField())) {
            boolean reverse = "descend".equals(param.getSortOrder());
            tables = luceneMgr.search(param, param.getLastDocId(), param.getSearchKey(), param.getSortField(), reverse);
        } else {
            tables = luceneMgr.search(param, param.getLastDocId(), param.getSearchKey());
        }
        if (param.getLastDocId() == null && StringUtils.isBlank(param.getSearchKey())) {
            tables = pinTable(tables, param);
        }
        if (param.getLastDocId() == null) {
            tables = deprecatedTable(tables, param);
        }
        Map<String, List<TableColumn>> tableColumnMap = Collections.emptyMap();
        if (Boolean.TRUE.equals(selector.getColumnList())) {
            tableColumnMap = getTableColumns((LuceneIndexManager) luceneMgr, param, tables);
        }
        MetaData metaSchema = Chat2DBContext.getMetaData();
        Connection connection = null;
        if (Boolean.TRUE.equals(selector.getColumnList()) || Boolean.TRUE.equals(selector.getIndexList())) {
            connection = Chat2DBContext.getConnection();
        }
        for (Table table : tables) {
            if (Boolean.TRUE.equals(selector.getColumnList())) {
                table.setColumnList(tableColumnMap.getOrDefault(table.getName(), Collections.emptyList()));
                metaSchema.enrichTable(connection, table.getDatabaseName(), table.getSchemaName(), table);
            }
            if (Boolean.TRUE.equals(selector.getIndexList())) {
                List<TableIndex> indexList = metaSchema.indexes(connection,
                        table.getDatabaseName(), table.getSchemaName(), table.getName());
                table.setIndexList(indexList);
            }
            if (Boolean.TRUE.equals(selector.getForeignKey())) {
                List<ForeignKey> foreignKeys = foreignKeySyncService.queryRealForeignKeys(
                        param.getDataSourceId(),
                        table.getDatabaseName(),
                        table.getSchemaName(),
                        table.getName()
                );
                table.setForeignKeyList(foreignKeys);
                List<VirtualForeignKey> virtualForeignKeys = foreignKeySyncService.queryVirtualForeignKeys(
                        param.getDataSourceId(),
                        table.getDatabaseName(),
                        table.getSchemaName(),
                        table.getName());
                table.setVirtualForeignKeyList(virtualForeignKeys);
            }
            if (Boolean.TRUE.equals(selector.getColumnList()) && Boolean.TRUE.equals(selector.getIndexList())) {
                setPrimaryKey(table);
            }

        }

        param.setLastDocId(luceneMgr.getLastDocId());
        return tables;
    }


    @Override
    public List<SimpleTable> queryTables(TablePageQueryParam param) {
        LuceneIndexManager<Table> luceneMgr = managerFactory.getManager(param.getDataSourceId());
        Long version = luceneMgr.getMaxVersion(param);
        if (needRefreshCache(param, version)) {
            if (!param.isRefresh() && isEmptyTableSnapshot(param)) {
                return Collections.emptyList();
            }
            log.info("[TableCache] Cache penetration, reason:{}, dataSourceId:{}, databaseName:{}, schemaName:{}, tableName:{}",
                    getCacheRefreshReason(param.isRefresh(), version), param.getDataSourceId(), param.getDatabaseName(),
                    param.getSchemaName(), param.getTableName());
            loadAndCacheMetadata(luceneMgr, param.getDatabaseName(), param.getSchemaName(), version);
        }
        List<Table> search = luceneMgr.search(param, param.getLastDocId(), param.getSearchKey());
        List<SimpleTable> tables = new ArrayList<>();
        for (Table table : search) {
            SimpleTable t = new SimpleTable();
            t.setName(table.getName());
            t.setComment(StringUtils.defaultIfBlank(table.getComment(), table.getAiComment()));
            tables.add(t);
        }
        return tables;
    }

    @Override
    public void updateTag(TablePageQueryParam param, String tableName, String tag) {
        // Lucene 按数据源共享索引；标签是全局元数据，直接更新单个表文档即可立即用于过滤。
        LuceneIndexManager<Table> manager = managerFactory.getManager(param.getDataSourceId());
        TablePageQueryParam query = TablePageQueryParam.builder()
                .dataSourceId(param.getDataSourceId()).databaseName(param.getDatabaseName())
                .schemaName(param.getSchemaName()).tableName(tableName).build();
        List<Table> tables = manager.searchAll(query, null);
        if (!tables.isEmpty()) {
            // Empty string means the tag was explicitly cleared. Using null here would trigger Lucene field
            // inheritance and restore the previous tag during updateDocument.
            tables.get(0).setTag(StringUtils.trimToEmpty(tag));
            manager.updateDocument(tables.get(0));
        }
    }

    @Override
    public void refreshTags(TablePageQueryParam param) {
        LuceneIndexManager<Table> manager = managerFactory.getManager(param.getDataSourceId());
        List<Table> tables = deprecatedTable(manager.searchAll(param, null), param);
        if (CollectionUtils.isEmpty(tables)) {
            return;
        }
        Map<String, String> generatedTags = TableTagUtils.calculateTags(
                tables.stream().map(Table::getName).collect(Collectors.toList()));
        log.info("[TableTag] Refresh all tags, dataSourceId:{}, databaseName:{}, schemaName:{}, tableCount:{}, "
                        + "generatedTagCount:{}, fullNameTagCount:{}", param.getDataSourceId(), param.getDatabaseName(),
                param.getSchemaName(), tables.size(),
                generatedTags.values().stream().filter(StringUtils::isNotBlank).distinct().count(),
                generatedTags.entrySet().stream().filter(entry -> entry.getKey().equals(entry.getValue())).count());
        tables.forEach(table -> table.setTag(StringUtils.defaultString(generatedTags.get(table.getName()))));
        manager.updateDocumentsByName(tables);
    }



    private boolean needRefreshCache(TablePageQueryParam param, Long version) {
        return param.isRefresh() || version == null;
    }

    private boolean isEmptyTableSnapshot(TablePageQueryParam param) {
        String objectKey = metadataSnapshotService.buildObjectKey(MetadataSnapshotService.TYPE_TABLE,
                param.getDatabaseName(), param.getSchemaName(), param.getDatabaseName());
        MetadataSnapshot snapshot = metadataSnapshotService.get(param.getDataSourceId(), MetadataSnapshotService.TYPE_TABLE,
                objectKey);
        return snapshot != null && MetadataSnapshotService.STATUS_EMPTY.equals(snapshot.getStatus())
                && Long.valueOf(0L).equals(snapshot.getObjectCount());
    }

    private String getCacheRefreshReason(boolean refresh, Long version) {
        if (refresh) {
            return "requestRefresh=true";
        }
        if (version == null) {
            return "cacheVersionMissing";
        }
        return "unknown";
    }

    private void loadAndCacheMetadata(LuceneIndexManager<Table> mgr, String databaseName, String schemaName, Long version) {
        mgr.getLock().writeLock().lock();
        try {
            boolean redis = DataSourceTypeEnum.REDIS.getCode().equals(Chat2DBContext.getConnectInfo().getDbType());
            Connection conn = redis ? null : Chat2DBContext.getConnection();
            MetaData meta = Chat2DBContext.getMetaData();
            List<Table> tables = meta.tables(conn, databaseName, schemaName, null);
            Long dataSourceId = Chat2DBContext.getConnectInfo().getDataSourceId();
            String objectKey = metadataSnapshotService.buildObjectKey(MetadataSnapshotService.TYPE_TABLE, databaseName,
                    schemaName, databaseName);
            if (CollectionUtils.isEmpty(tables)) {
                mgr.deleteByDatabaseAndSchema(Table.builder()
                        .databaseName(databaseName)
                        .schemaName(schemaName)
                        .build());
                metadataSnapshotService.upsertCount(dataSourceId, MetadataSnapshotService.TYPE_TABLE, objectKey,
                        databaseName, schemaName, databaseName, 0L, version, MetadataSnapshotService.STATUS_EMPTY, null);
                return;
            }
            mgr.updateDocuments(tables, version);
            metadataSnapshotService.upsertCount(dataSourceId, MetadataSnapshotService.TYPE_TABLE, objectKey,
                    databaseName, schemaName, databaseName, (long) tables.size(), version,
                    MetadataSnapshotService.STATUS_READY, null);
        } catch (Exception e) {
            log.error("loadAndCacheMetadata error,version:{}", version, e);
        } finally {
            mgr.getLock().writeLock().unlock();
        }
    }

    private List<Table> pinTable(List<Table> list, TablePageQueryParam param) {
        if (CollectionUtils.isEmpty(list)) {
            return Lists.newArrayList();
        }
        PinTableParam pinTableParam = pinTableConverter.toPinTableParam(param);
        pinTableParam.setUserId(ContextUtils.getUserId());
        List<String> pinnedTables = pinService.queryPinTables(pinTableParam);
        if (CollectionUtils.isEmpty(pinnedTables)) {
            return list;
        }
        List<Table> tables = new ArrayList<>();
        Map<String, Table> tableMap = list.stream()
                .collect(Collectors.toMap(Table::getName, Function.identity(), (o1, o2) -> o1));
        for (String tableName : pinnedTables) {
            Table table = tableMap.get(tableName);
            if (table != null) {
                table.setPinned(true);
                tables.add(table);
            }
        }

        for (Table table : list) {
            if (table != null && !tables.contains(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    private List<Table> deprecatedTable(List<Table> list, TablePageQueryParam param) {
        if (CollectionUtils.isEmpty(list)) {
            return Lists.newArrayList();
        }
        DeprecatedTableParam deprecatedTableParam = new DeprecatedTableParam();
        deprecatedTableParam.setDataSourceId(param.getDataSourceId());
        deprecatedTableParam.setDatabaseName(param.getDatabaseName());
        deprecatedTableParam.setSchemaName(param.getSchemaName());
        deprecatedTableParam.setUserId(ContextUtils.getUserId());
        List<String> deprecatedTables = deprecatedTableService.queryDeprecatedTables(deprecatedTableParam);
        if (CollectionUtils.isEmpty(deprecatedTables)) {
            return list;
        }
        Set<String> deprecatedTableNames = new java.util.HashSet<>(deprecatedTables);
        List<Table> filteredTables = new ArrayList<>();
        for (Table table : list) {
            if (table != null && !deprecatedTableNames.contains(table.getName())) {
                filteredTables.add(table);
            }
        }
        return filteredTables;
    }

    @Override
    public List<Table> pageQueryDeprecated(TablePageQueryParam param, TableSelector selector) {
        DeprecatedTableParam deprecatedTableParam = new DeprecatedTableParam();
        deprecatedTableParam.setDataSourceId(param.getDataSourceId());
        deprecatedTableParam.setDatabaseName(param.getDatabaseName());
        deprecatedTableParam.setSchemaName(param.getSchemaName());
        deprecatedTableParam.setUserId(ContextUtils.getUserId());
        List<String> tableNames = deprecatedTableService.queryDeprecatedTables(deprecatedTableParam);
        if (CollectionUtils.isEmpty(tableNames)) {
            return Collections.emptyList();
        }
        Set<String> deprecatedTableNames = new java.util.HashSet<>(tableNames);
        List<Table> allTables = queryAllTables(param);
        List<Table> deprecatedTables = new ArrayList<>();
        for (Table table : allTables) {
            if (table != null && deprecatedTableNames.contains(table.getName())) {
                table.setDeprecated(true);
                deprecatedTables.add(table);
            }
        }
        return deprecatedTables;
    }

    private List<Table> queryAllTables(TablePageQueryParam param) {
        LuceneIndexManager<Table> luceneMgr = managerFactory.getManager(param.getDataSourceId());
        Long version = luceneMgr.getMaxVersion(param);
        if (needRefreshCache(param, version)) {
            log.info("[TableCache] Cache penetration, reason:{}, dataSourceId:{}, databaseName:{}, schemaName:{}, tableName:{}",
                    getCacheRefreshReason(param.isRefresh(), version), param.getDataSourceId(), param.getDatabaseName(),
                    param.getSchemaName(), param.getTableName());
            loadAndCacheMetadata(luceneMgr, param.getDatabaseName(), param.getSchemaName(), version);
        }
        return luceneMgr.search(param, param.getLastDocId(), param.getSearchKey());
    }

    @Override
    public void deprecatedTable(DeprecatedTableParam param) {
        param.setUserId(ContextUtils.getUserId());
        deprecatedTableService.deprecatedTable(param);
    }

    @Override
    public void deleteDeprecatedTable(DeprecatedTableParam param) {
        param.setUserId(ContextUtils.getUserId());
        deprecatedTableService.deleteDeprecatedTable(param);
    }

    @Override
    public List<String> queryDeprecatedTables(DeprecatedTableParam param) {
        param.setUserId(ContextUtils.getUserId());
        return deprecatedTableService.queryDeprecatedTables(param);
    }

    @Override
    public void saveAiComment(Long dataSourceId, String databaseName, String schemaName, String tableName,
                              String columnName, String aiComment) {
        if (StringUtils.isBlank(tableName) || StringUtils.isBlank(aiComment)) {
            return;
        }
        if (StringUtils.isBlank(columnName)) {
            LuceneIndexManager<Table> manager = managerFactory.getManager(dataSourceId);
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(tableName);
            table.setAiComment(aiComment);
            manager.updateDocuments(List.of(table), null, false);
            return;
        }
        LuceneIndexManager<TableColumn> manager = managerFactory.getManager(dataSourceId);
        TableColumn column = new TableColumn();
        column.setDatabaseName(databaseName);
        column.setSchemaName(schemaName);
        column.setTableName(tableName);
        column.setName(columnName);
        column.setAiComment(aiComment);
        manager.updateDocuments(List.of(column), null, false);
    }

    @Override
    public List<TableColumn> queryColumns(TableQueryParam param) {
        LuceneIndexManager<TableColumn> luceneIndexManager = managerFactory.getManager(param.getDataSourceId());
        param.setClassType(TableColumn.class);
        return getTableColumns(luceneIndexManager, param);
    }

    private List<TableColumn> getTableColumns(LuceneIndexManager<TableColumn> mgr,
                                               TableQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        Long version = mgr.getMaxVersion(param);
        if (param.isRefresh() || version == null) {
            log.info("[TableColumnCache] Cache penetration, reason:{}, dataSourceId:{}, databaseName:{}, schemaName:{}, tableName:{}",
                    getCacheRefreshReason(param.isRefresh(), version), param.getDataSourceId(), param.getDatabaseName(),
                    param.getSchemaName(), param.getTableName());
            mgr.getLock().writeLock().lock();
            try {
                List<TableColumn> columns = metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(),
                        param.getSchemaName(),
                        param.getTableName());
                mgr.updateDocuments(columns, version);
                return columns;
            } catch (Exception e) {
                log.error("getTableColumns error", e);
            } finally {
                mgr.getLock().writeLock().unlock();
            }
        }
        return mgr.search(param, null, null);
    }

    private Map<String, List<TableColumn>> getTableColumns(LuceneIndexManager<TableColumn> mgr,
                                                           TablePageQueryParam param,
                                                           List<Table> tables) {
        if (CollectionUtils.isEmpty(tables)) {
            return Collections.emptyMap();
        }
        TableQueryParam queryParam = TableQueryParam.builder()
                .dataSourceId(param.getDataSourceId())
                .databaseName(param.getDatabaseName())
                .schemaName(param.getSchemaName())
                .refresh(param.isRefresh())
                .classType(TableColumn.class)
                .build();
        Long version = mgr.getMaxVersion(queryParam);
        if (!param.isRefresh() && version != null) {
            Map<String, List<TableColumn>> cachedColumns = groupTableColumns(mgr.searchAll(queryParam, null), tables);
            if (hasAllTableColumns(cachedColumns, tables)) {
                return cachedColumns;
            }
            // A cache version can exist while selected tables are missing columns, so record the exact misses.
            log.info("[TableColumnCache] Cache penetration, reason:missingTableColumns, dataSourceId:{}, databaseName:{}, schemaName:{}, tableNames:{}, missingTableNames:{}",
                    param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), getTableNames(tables),
                    getMissingTableNames(cachedColumns, tables));
        } else {
            log.info("[TableColumnCache] Cache penetration, reason:{}, dataSourceId:{}, databaseName:{}, schemaName:{}, tableNames:{}",
                    getCacheRefreshReason(param.isRefresh(), version), param.getDataSourceId(), param.getDatabaseName(),
                    param.getSchemaName(), getTableNames(tables));
        }
        return groupTableColumns(loadAndCacheTableColumns(mgr, queryParam, version, tables), tables);
    }

    private List<TableColumn> loadAndCacheTableColumns(LuceneIndexManager<TableColumn> mgr,
                                                       TableQueryParam param,
                                                       Long version,
                                                       List<Table> tables) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        mgr.getLock().writeLock().lock();
        try {
            if (!metaSchema.supportBatchQueryColumns()) {
                // Some engines, including DLC, can only describe one table at a time.
                log.info("[TableColumnCache] Batch column query unsupported, query tables individually, dataSourceId:{}, databaseName:{}, schemaName:{}, tableNames:{}",
                        param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), getTableNames(tables));
                return loadAndCacheTableColumnsIndividually(mgr, metaSchema, param, version, tables);
            }
            log.info("[TableColumnCache] Load all columns from datasource, dataSourceId:{}, databaseName:{}, schemaName:{}, tableNames:{}",
                    param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), getTableNames(tables));
            List<TableColumn> columns = metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(),
                    param.getSchemaName(), null);
            if (CollectionUtils.isNotEmpty(columns)) {
                Map<String, List<TableColumn>> columnsByTable = columns.stream()
                        .filter(column -> StringUtils.isNotBlank(column.getTableName()))
                        .collect(Collectors.groupingBy(TableColumn::getTableName));
                for (List<TableColumn> tableColumns : columnsByTable.values()) {
                    mgr.updateDocuments(tableColumns, version);
                }
            }
            return columns;
        } catch (Exception e) {
            log.error("getTableColumns error", e);
        } finally {
            mgr.getLock().writeLock().unlock();
        }
        return Collections.emptyList();
    }

    private List<TableColumn> loadAndCacheTableColumnsIndividually(LuceneIndexManager<TableColumn> mgr,
                                                                   MetaData metaSchema,
                                                                   TableQueryParam param,
                                                                   Long version,
                                                                   List<Table> tables) {
        List<TableColumn> columns = new ArrayList<>();
        for (Table table : tables) {
            if (table == null || StringUtils.isBlank(table.getName())) {
                continue;
            }
            log.info("[TableColumnCache] Load table columns from datasource, dataSourceId:{}, databaseName:{}, schemaName:{}, tableName:{}",
                    param.getDataSourceId(), StringUtils.defaultIfBlank(table.getDatabaseName(), param.getDatabaseName()),
                    StringUtils.defaultIfBlank(table.getSchemaName(), param.getSchemaName()), table.getName());
            List<TableColumn> tableColumns = metaSchema.columns(
                    Chat2DBContext.getConnection(),
                    StringUtils.defaultIfBlank(table.getDatabaseName(), param.getDatabaseName()),
                    StringUtils.defaultIfBlank(table.getSchemaName(), param.getSchemaName()),
                    table.getName());
            if (CollectionUtils.isEmpty(tableColumns)) {
                continue;
            }
            mgr.updateDocuments(tableColumns, version);
            columns.addAll(tableColumns);
        }
        return columns;
    }

    private Map<String, List<TableColumn>> groupTableColumns(List<TableColumn> columns, List<Table> tables) {
        if (CollectionUtils.isEmpty(columns)) {
            return Collections.emptyMap();
        }
        Set<String> tableNames = tables.stream()
                .map(Table::getName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        return columns.stream()
                .filter(column -> tableNames.contains(column.getTableName()))
                .collect(Collectors.groupingBy(TableColumn::getTableName));
    }

    private List<String> getTableNames(List<Table> tables) {
        if (CollectionUtils.isEmpty(tables)) {
            return Collections.emptyList();
        }
        return tables.stream()
                .filter(Objects::nonNull)
                .map(Table::getName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private List<String> getMissingTableNames(Map<String, List<TableColumn>> tableColumnMap, List<Table> tables) {
        if (CollectionUtils.isEmpty(tables)) {
            return Collections.emptyList();
        }
        return tables.stream()
                .filter(Objects::nonNull)
                .map(Table::getName)
                .filter(StringUtils::isNotBlank)
                .filter(tableName -> CollectionUtils.isEmpty(tableColumnMap.get(tableName)))
                .collect(Collectors.toList());
    }

    private boolean hasAllTableColumns(Map<String, List<TableColumn>> tableColumnMap, List<Table> tables) {
        if (tableColumnMap.isEmpty()) {
            return false;
        }
        return tables.stream()
                .map(Table::getName)
                .filter(StringUtils::isNotBlank)
                .allMatch(tableName -> CollectionUtils.isNotEmpty(tableColumnMap.get(tableName)));
    }

    @Override
    public List<TableIndex> queryIndexes(TableQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return metaSchema.indexes(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                param.getTableName());

    }

    @Override
    public List<Type> queryTypes(TypeQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return metaSchema.types(Chat2DBContext.getConnection());
    }

    @Override
    public TableMeta queryTableMeta(TypeQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        Connection connection = Chat2DBContext.getConnection();
        TableMeta tableMeta = metaSchema.getTableMeta(connection, null, null);
        if (tableMeta != null) {
            // filter primary key
            List<IndexType> indexTypes = tableMeta.getIndexTypes();
            if (CollectionUtils.isNotEmpty(indexTypes)) {
                List<IndexType> types = indexTypes.stream()
                        .filter(indexType -> !"Primary".equals(indexType.getTypeName())).collect(Collectors.toList());
                tableMeta.setIndexTypes(types);
            }
        }
        return tableMeta;

    }


    @Override
    public void truncate(DropParam param) {
        DBManage metaSchema = Chat2DBContext.getDBManage();
        metaSchema.truncate(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                param.getTableName());
    }

    @Override
    public List<TreeNode> searchTreeNodes(TreeSearchParam param) {
        LuceneIndexManager<Table> mgr = managerFactory.getManager(param.getDataSourceId());
        Table queryModel = Table.builder()
                .databaseName(param.getDatabaseName())
                .schemaName(param.getSchemaName())
                .build();
        Long version = mgr.getMaxVersion(queryModel);

        if (param.isRefresh() || version == null) {
            loadAndCacheMetadata(mgr, param.getDatabaseName(), param.getSchemaName(), version);
        }

        List<Table> tables = mgr.search(queryModel, null, param.getSearchKey());
        List<TreeNode> result = new ArrayList<>();
        for (Table table : tables) {
            TreeNode node = buildTreeNode(table);
            result.add(node);
        }
        return result;
    }


    private TreeNode buildTreeNode(Table table) {
        List<String> parentPath = new ArrayList<>();
        if (StringUtils.isNotBlank(table.getDatabaseName())) {
            parentPath.add(table.getDatabaseName());
        }
        if (StringUtils.isNotBlank(table.getSchemaName())) {
            parentPath.add(table.getSchemaName());
        }

        Map<String, Object> extraParams = new HashMap<>();
        extraParams.put("databaseName", table.getDatabaseName());
        extraParams.put("schemaName", table.getSchemaName());
        extraParams.put("tableName", table.getName());

        return TreeNode.builder()
                .uuid("table-" + table.getName())
                .key(table.getName())
                .name(table.getName())
                .treeNodeType("table")
                .comment(table.getComment())
                .isLeaf(true)
                .pinned(table.isPinned())
                .parentPath(parentPath)
                .extraParams(extraParams)
                .build();
    }

    @Override
    public List<ExecuteResult> batchOptimizeTables(List<String> tableNames, String databaseName, String schemaName) {
        List<ExecuteResult> results = new ArrayList<>();
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        MetaData metaData = Chat2DBContext.getMetaData();
        Connection connection = Chat2DBContext.getConnection();

        for (String tableName : tableNames) {
            String sql = sqlBuilder.buildOptimizeTableSql(databaseName, schemaName, tableName);
            if (sql == null) {
                results.add(ExecuteResult.builder()
                        .success(false)
                        .message("OPTIMIZE TABLE is not supported for this database type")
                        .sql(sql)
                        .build());
                continue;
            }
            try {
                CommandExecutor commandExecutor = metaData.getCommandExecutor();
                ExecuteResult result = commandExecutor.execute(sql, connection, false, null, null, metaData.getValueHandler());
                result.setSql(sql);
                results.add(result);
            } catch (Exception e) {
                log.error("Failed to optimize table: {}", tableName, e);
                results.add(ExecuteResult.builder()
                        .success(false)
                        .message(e.getMessage())
                        .sql(sql)
                        .build());
            }
        }
        return results;
    }

    @Override
    public List<ExecuteResult> batchAnalyzeTables(List<String> tableNames, String databaseName, String schemaName) {
        List<ExecuteResult> results = new ArrayList<>();
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        MetaData metaData = Chat2DBContext.getMetaData();
        Connection connection = Chat2DBContext.getConnection();

        for (String tableName : tableNames) {
            String sql = sqlBuilder.buildAnalyzeTableSql(databaseName, schemaName, tableName);
            if (sql == null) {
                results.add(ExecuteResult.builder()
                        .success(false)
                        .message("ANALYZE TABLE is not supported for this database type")
                        .sql(sql)
                        .build());
                continue;
            }
            try {
                CommandExecutor commandExecutor = metaData.getCommandExecutor();
                ExecuteResult result = commandExecutor.execute(sql, connection, false, null, null, metaData.getValueHandler());
                result.setSql(sql);
                results.add(result);
            } catch (Exception e) {
                log.error("Failed to analyze table: {}", tableName, e);
                results.add(ExecuteResult.builder()
                        .success(false)
                        .message(e.getMessage())
                        .sql(sql)
                        .build());
            }
        }
        return results;
    }

}
