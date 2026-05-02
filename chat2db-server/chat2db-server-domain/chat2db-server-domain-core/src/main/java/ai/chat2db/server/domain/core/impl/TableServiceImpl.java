package ai.chat2db.server.domain.core.impl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ai.chat2db.spi.enums.IndexTypeEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import ai.chat2db.server.domain.api.model.TreeNode;
import ai.chat2db.server.domain.api.param.DeprecatedTableParam;
import ai.chat2db.server.domain.api.param.DropKeyParam;
import ai.chat2db.server.domain.api.param.DropParam;
import ai.chat2db.server.domain.api.param.PinTableParam;
import ai.chat2db.server.domain.api.param.ShowCreateTableParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.domain.api.param.TableSelector;
import ai.chat2db.server.domain.api.param.TreeSearchParam;
import ai.chat2db.server.domain.api.param.TypeQueryParam;
import ai.chat2db.server.domain.api.service.DeprecatedTableService;
import ai.chat2db.server.domain.api.service.PinService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.domain.core.cache.LuceneIndexManager;
import ai.chat2db.server.domain.core.cache.LuceneIndexManagerFactory;
import ai.chat2db.server.domain.core.converter.PinTableConverter;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.VirtualForeignKeyDO;
import ai.chat2db.server.domain.repository.mapper.VirtualForeignKeyMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.enums.EditStatus;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.IndexModel;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PreDestroy;
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
    @Qualifier("indexUpdateExecutor")
    private ExecutorService executor;

    @Autowired
    private LuceneIndexManagerFactory managerFactory;

    @Override
    public DataResult<String> showCreateTable(ShowCreateTableParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        String ddl = metaSchema.tableDDL(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                param.getTableName());
        return DataResult.of(ddl);
    }

    @Override
    public ActionResult drop(DropParam param) {
        DBManage metaSchema = Chat2DBContext.getDBManage();
        metaSchema.dropTable(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                param.getTableName());
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<String> createTableExample(String dbType) {
        String sql = Chat2DBContext.getDBConfig().getSimpleCreateTable();
        return DataResult.of(sql);
    }

    @Override
    public DataResult<String> alterTableExample(String dbType) {
        String sql = Chat2DBContext.getDBConfig().getSimpleAlterTable();
        return DataResult.of(sql);
    }

    @Override
    public DataResult<Table> query(TableQueryParam param, TableSelector selector) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        List<Table> tables = metaSchema.tables(Chat2DBContext.getConnection(), param.getDatabaseName(),
                param.getSchemaName(), param.getTableName());
        if (!CollectionUtils.isEmpty(tables)) {
            Table table = tables.get(0);
            table.setIndexList(
                    metaSchema.indexes(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                            param.getTableName()));
            table.setColumnList(
                    metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                            param.getTableName()));
            table.setForeignKeyList(
                    metaSchema.foreignKeys(Chat2DBContext.getConnection(), param.getDatabaseName(),
                            param.getSchemaName(), param.getTableName()));
            setPrimaryKey(table);
            return DataResult.of(table);
        }
        return DataResult.of(null);
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
        List<TableIndex> indexes = new ArrayList<>();
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
            } else {
                indexes.add(tableIndex);
            }
        }
        table.setIndexList(indexes);
    }

    @Override
    public ListResult<Sql> buildSql(Table oldTable, Table newTable) {
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
        return ListResult.of(sqls);
    }

    @Override
    public ListResult<String> buildBatchSql(List<Table> oldTables, List<Table> newTables) {
        if (oldTables.size() != newTables.size()) {
            throw new IllegalArgumentException("Old tables and new tables lists must have the same size.");
        }
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        List<String> batchSqls = IntStream.range(0, oldTables.size())
                .mapToObj(i -> sqlBuilder.buildModifyTaleSql(oldTables.get(i), newTables.get(i)))
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());
        return ListResult.of(batchSqls);
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
    public PageResult<Table> pageQuery(TablePageQueryParam param, TableSelector selector) {
        LuceneIndexManager<Table> luceneMgr = managerFactory.getManager(param.getDataSourceId());
        Long version = luceneMgr.getMaxVersion(param);
        // 仅对元数据加载环节加锁
        if (needRefreshCache(param, version)) {
            loadAndCacheMetadata(luceneMgr, param.getDatabaseName(), param.getSchemaName(), version);
        }
        List<Table> tables = luceneMgr.search(param, param.getLastDocId(), param.getSearchKey());
        long total = luceneMgr.getTotal();
        log.info("total:{}", total);
        for (Table table : tables) {
            TableQueryParam queryParam = TableQueryParam.builder()
                    .dataSourceId(param.getDataSourceId())
                    .schemaName(table.getSchemaName())
                    .databaseName(table.getDatabaseName())
                    .tableName(table.getName())
                    .refresh(param.isRefresh())
                    .build();
            if (Boolean.TRUE.equals(selector.getColumnList())) {
                queryParam.setClassType(TableColumn.class);
                List<TableColumn> columnList = getTableColumns((LuceneIndexManager) luceneMgr, queryParam);
                table.setColumnList(columnList);
            }
            if (Boolean.TRUE.equals(selector.getForeignKey())) {
                List<ForeignKey> foreignKeys = foreignKeySyncService.queryRealForeignKeys(
                        param.getDataSourceId(),
                        table.getDatabaseName(),
                        table.getSchemaName(),
                        table.getName()
                );
                table.setForeignKeyList(foreignKeys);
            }
            if (Boolean.TRUE.equals(selector.getColumnList())
                    && Boolean.TRUE.equals(selector.getForeignKey())) {
                List<VirtualForeignKey> virtualForeignKeys = findVirtualForeignKeys(luceneMgr, table);
                table.setVirtualForeignKeyList(virtualForeignKeys);
            }
        }
        if (param.getLastDocId() == null) {
            tables = pinTable(tables, param);
            tables = deprecatedTable(tables, param);
        }
        param.setLastDocId(luceneMgr.getLastDocId());

        return PageResult.of(tables, total, param);
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public ListResult<SimpleTable> queryTables(TablePageQueryParam param) {
        LuceneIndexManager<Table> luceneMgr = managerFactory.getManager(param.getDataSourceId());
        Long version = luceneMgr.getMaxVersion(param);
        if (needRefreshCache(param, version)) {
            loadAndCacheMetadata(luceneMgr, param.getDatabaseName(), param.getSchemaName(), version);
        }
        List<Table> search = luceneMgr.search(param, param.getLastDocId(), param.getSearchKey());
        List<SimpleTable> tables = new ArrayList<>();
        for (Table table : search) {
            SimpleTable t = new SimpleTable();
            t.setName(table.getName());
            t.setComment(table.getComment());
            tables.add(t);
        }
        return ListResult.of(tables);
    }

    private boolean needRefreshCache(TablePageQueryParam param, Long version) {
        return param.isRefresh() || version == null;
    }

    private void loadAndCacheMetadata(LuceneIndexManager<Table> mgr, String databaseName, String schemaName, Long version) {
        mgr.getLock().writeLock().lock();
        try {
            Connection conn = Chat2DBContext.getConnection();
            MetaData meta = Chat2DBContext.getMetaData();
            List<Table> tables = meta.tables(conn, databaseName, schemaName, null);
            mgr.updateDocuments(tables, version);
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
        ListResult<String> listResult = pinService.queryPinTables(pinTableParam);
        if (!listResult.success() || CollectionUtils.isEmpty(listResult.getData())) {
            return list;
        }
        List<Table> tables = new ArrayList<>();
        Map<String, Table> tableMap = list.stream()
                .collect(Collectors.toMap(Table::getName, Function.identity(), (o1, o2) -> o1));
        for (String tableName : listResult.getData()) {
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
        ListResult<String> listResult = deprecatedTableService.queryDeprecatedTables(deprecatedTableParam);
        if (!listResult.success() || CollectionUtils.isEmpty(listResult.getData())) {
            return list;
        }
        Set<String> deprecatedTableNames = new java.util.HashSet<>(listResult.getData());
        List<Table> filteredTables = new ArrayList<>();
        for (Table table : list) {
            if (table != null && !deprecatedTableNames.contains(table.getName())) {
                filteredTables.add(table);
            }
        }
        return filteredTables;
    }

    @Override
    public PageResult<Table> pageQueryDeprecated(TablePageQueryParam param, TableSelector selector) {
        DeprecatedTableParam deprecatedTableParam = new DeprecatedTableParam();
        deprecatedTableParam.setDataSourceId(param.getDataSourceId());
        deprecatedTableParam.setDatabaseName(param.getDatabaseName());
        deprecatedTableParam.setSchemaName(param.getSchemaName());
        deprecatedTableParam.setUserId(ContextUtils.getUserId());
        ListResult<String> listResult = deprecatedTableService.queryDeprecatedTables(deprecatedTableParam);
        if (!listResult.success() || CollectionUtils.isEmpty(listResult.getData())) {
            return PageResult.of(Lists.newArrayList(), 0L, param);
        }
        Set<String> deprecatedTableNames = new java.util.HashSet<>(listResult.getData());
        List<Table> allTables = queryAllTables(param);
        List<Table> deprecatedTables = new ArrayList<>();
        for (Table table : allTables) {
            if (table != null && deprecatedTableNames.contains(table.getName())) {
                table.setDeprecated(true);
                deprecatedTables.add(table);
            }
        }
        return PageResult.of(deprecatedTables, (long) deprecatedTables.size(), param);
    }

    private List<Table> queryAllTables(TablePageQueryParam param) {
        LuceneIndexManager<Table> luceneMgr = managerFactory.getManager(param.getDataSourceId());
        Long version = luceneMgr.getMaxVersion(param);
        if (needRefreshCache(param, version)) {
            loadAndCacheMetadata(luceneMgr, param.getDatabaseName(), param.getSchemaName(), version);
        }
        return luceneMgr.search(param, param.getLastDocId(), param.getSearchKey());
    }

    @Override
    public ActionResult deprecatedTable(DeprecatedTableParam param) {
        param.setUserId(ContextUtils.getUserId());
        return deprecatedTableService.deprecatedTable(param);
    }

    @Override
    public ActionResult deleteDeprecatedTable(DeprecatedTableParam param) {
        param.setUserId(ContextUtils.getUserId());
        return deprecatedTableService.deleteDeprecatedTable(param);
    }

    @Override
    public ListResult<String> queryDeprecatedTables(DeprecatedTableParam param) {
        param.setUserId(ContextUtils.getUserId());
        return deprecatedTableService.queryDeprecatedTables(param);
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
        return (List<TableColumn>) mgr.search(param, null, null);
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
    public List<ForeignKey> queryForeignKeys(TableQueryParam param) {
        return foreignKeySyncService.queryRealForeignKeys(
                param.getDataSourceId(),
                param.getDatabaseName(),
                param.getSchemaName(),
                param.getTableName()
        );
    }

    /**
     * 发现可能的虚拟外键关系（根据命名规范推断）
     *
     * @param luceneIndexManager 提供表结构检索能力的索引管理器
     * @param table              需要分析的表对象
     * @return 虚拟外键关系列表（符合命名规范但未显式声明的外键）
     */
    private List<VirtualForeignKey> findVirtualForeignKeys(LuceneIndexManager<Table> luceneIndexManager, Table table) {
        List<VirtualForeignKey> result = new ArrayList<>();

        List<VirtualForeignKey> storedVirtualFKs = foreignKeySyncService.queryVirtualForeignKeys(
                table.getDatabaseName() != null ? Long.parseLong(table.getDatabaseName() + "0") : null,
                table.getDatabaseName(),
                table.getSchemaName(),
                table.getName()
        );
        if (!CollectionUtils.isEmpty(storedVirtualFKs)) {
            result.addAll(storedVirtualFKs);
        }

        Set<String> explicitForeignKeys = table.getForeignKeyList().stream()
                .map(ForeignKey::getColumn)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        table.getIndexList().stream()
                .filter(index -> Boolean.TRUE.equals(index.getUnique()))
                .map(TableIndex::getColumnList)
                .flatMap(List::stream)
                .map(TableIndexColumn::getColumnName)
                .forEach(explicitForeignKeys::add);

        Set<String> storedVKColumns = result.stream()
                .map(VirtualForeignKey::getColumn)
                .collect(Collectors.toSet());

        List<VirtualForeignKey> inferredFKs = table.getColumnList().stream()
                .filter(this::isPotentialVirtualKeyCandidate)
                .filter(column -> !explicitForeignKeys.contains(column.getName()))
                .filter(column -> !storedVKColumns.contains(column.getName()))
                .map(column -> analyzeColumnRelation(luceneIndexManager, table, column))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        result.addAll(inferredFKs);
        return result;
    }

    /**
     * 判断列是否为虚拟外键候选列（核心匹配规则）
     */
    private boolean isPotentialVirtualKeyCandidate(TableColumn column) {
        return column.getName() != null
                // 匹配后缀命名规范
                && column.getName().endsWith("_id")
                // 排除主键列
                && Boolean.FALSE.equals(column.getPrimaryKey())
                // 防止类似"id"的误判
                && column.getName().length() > 3;
    }

    /**
     * 分析列关联关系并构建虚拟外键
     */
    private VirtualForeignKey analyzeColumnRelation(LuceneIndexManager<Table> luceneIndexManager,
                                                    Table currentTable,
                                                    TableColumn currentColumn) {
        String columnName = currentColumn.getName();

        // 推导关联表名称（移除_id后缀）
        String referencedTableName = columnName.substring(0, columnName.length() - 3);

        // 排除自关联情况（当前表与目标表同名时跳过）
        if (referencedTableName.equalsIgnoreCase(currentTable.getName())) {
            return null;
        }
        Table table = Table.builder()
                .databaseName(currentColumn.getDatabaseName())
                .schemaName(currentColumn.getSchemaName())
                .build();
        // 使用模糊查询查找关联表（考虑表名大小写不敏感的情况）
        List<Table> matchedTables = luceneIndexManager.search(table, null, referencedTableName);
        if (CollectionUtils.isEmpty(matchedTables)) {
            return null;
        }

        // 取第一个匹配表（实际业务可能需要更精确的匹配策略）
        Table targetTable = null;
        for (Table matchedTable : matchedTables) {
            if (!currentTable.getName().equals(matchedTable.getName())) {
                targetTable = matchedTable;
                break;
            }
        }

        if (targetTable == null) {
            return null;
        }

        String referencedColumnName = "id";
        for (TableColumn tableColumn : targetTable.getColumnList()) {
            if (columnName.equalsIgnoreCase(tableColumn.getName())) {
                referencedColumnName = tableColumn.getName();
                break;
            } else if (referencedColumnName.equals("id") && Boolean.TRUE.equals(tableColumn.getPrimaryKey())) {
                referencedColumnName = tableColumn.getName();
            }
        }

        // 构建虚拟外键关系
        return VirtualForeignKey.builder()
                // 生成唯一标识
                .name(String.format("VFK_%s_%s", currentTable.getName(), columnName))
                .tableName(currentTable.getName())
                .column(columnName)
                .referencedTable(targetTable.getName())
                // 约定外键默认关联目标表主键
                .referencedColumn(referencedColumnName)
                .virtualProperty("Inferred from column naming convention")
                .build();
    }

    @Override
    public ActionResult truncate(DropParam param) {
        DBManage metaSchema = Chat2DBContext.getDBManage();
        metaSchema.truncate(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(),
                param.getTableName());
        return ActionResult.isSuccess();
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

}
