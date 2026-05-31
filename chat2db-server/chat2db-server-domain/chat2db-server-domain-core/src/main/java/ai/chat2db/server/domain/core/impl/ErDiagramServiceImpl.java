package ai.chat2db.server.domain.core.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.chat2db.server.domain.api.param.CreateVirtualFKParam;
import ai.chat2db.server.domain.api.param.ErDiagramQueryParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.TableSelector;
import ai.chat2db.server.domain.api.service.ErDiagramService;
import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.vo.InferVirtualFkResultVO;
import ai.chat2db.spi.model.ErDiagram;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.model.TableIndexColumn;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.VirtualForeignKey;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ErDiagramServiceImpl implements ErDiagramService {

    @Autowired
    private TableService tableService;

    @Autowired
    private ForeignKeySyncService foreignKeySyncService;

    @Override
    public ErDiagram queryErDiagram(ErDiagramQueryParam param) {
        if (Boolean.TRUE.equals(param.getSyncForeignKeys())) {
            foreignKeySyncService.syncForeignKeys(
                    param.getDataSourceId(),
                    param.getDatabaseName(),
                    param.getSchemaName(),
                    null
            );
        }
        
        List<Table> tables = queryTables(param);
        List<ErDiagram.Node> nodes = buildNodes(tables);
        Set<String> tableNameSet = tables.stream().map(Table::getName).collect(Collectors.toSet());
        boolean includeVirtual = param.getIncludeVirtualFk() == null || param.getIncludeVirtualFk();
        List<ErDiagram.Edge> edges = buildEdges(tables, tableNameSet, includeVirtual);
        
        if (Boolean.TRUE.equals(param.getOnlyRelatedTables())) {
            Set<String> relatedTableIds = edges.stream()
                    .flatMap(e -> java.util.stream.Stream.of(e.getSource(), e.getTarget()))
                    .collect(Collectors.toSet());
            nodes = nodes.stream()
                    .filter(n -> relatedTableIds.contains(n.getId()))
                    .collect(Collectors.toList());
            edges = edges.stream()
                    .filter(e -> relatedTableIds.contains(e.getSource()) && relatedTableIds.contains(e.getTarget()))
                    .collect(Collectors.toList());
        }
        
        return ErDiagram.builder().nodes(nodes).edges(edges).build();
    }

    private List<Table> queryTables(ErDiagramQueryParam param) {
        TablePageQueryParam tablePageQueryParam = TablePageQueryParam.builder()
                .dataSourceId(param.getDataSourceId())
                .databaseName(param.getDatabaseName())
                .schemaName(param.getSchemaName())
                .searchKey(param.getTableNameFilter())
                .build();
        TableSelector selector = new TableSelector();
        selector.setColumnList(true);
        selector.setIndexList(true);
        selector.setForeignKey(true);
        return tableService.pageQuery(tablePageQueryParam, selector);
    }

    private List<ErDiagram.Node> buildNodes(List<Table> tables) {
        return tables.stream().map(table -> ErDiagram.Node.builder()
                .id(table.getName())
                .name(table.getName())
                .comment(table.getComment())
                .columnCount(CollectionUtils.isEmpty(table.getColumnList()) ? 0 : table.getColumnList().size())
                .build()
        ).collect(Collectors.toList());
    }

    private List<ErDiagram.Edge> buildEdges(List<Table> tables, Set<String> tableNameSet, boolean includeVirtual) {
        List<ErDiagram.Edge> edges = new ArrayList<>();
        for (Table table : tables) {
            if (CollectionUtils.isNotEmpty(table.getForeignKeyList())) {
                for (ForeignKey fk : table.getForeignKeyList()) {
                    if (tableNameSet.contains(fk.getReferencedTable())) {
                        edges.add(buildEdge(table.getName(), fk, false));
                    }
                }
            }
            if (includeVirtual && CollectionUtils.isNotEmpty(table.getVirtualForeignKeyList())) {
                for (VirtualForeignKey vfk : table.getVirtualForeignKeyList()) {
                    if (tableNameSet.contains(vfk.getReferencedTable())) {
                        edges.add(buildEdge(table.getName(), vfk, true));
                    }
                }
            }
        }
        return edges;
    }

    private ErDiagram.Edge buildEdge(String tableName, ForeignKey fk, boolean virtual) {
        String defaultId = virtual
                ? "VFK_" + tableName + "_" + fk.getColumn()
                : tableName + "_" + fk.getColumn() + "_" + fk.getReferencedTable();
        return ErDiagram.Edge.builder()
                .id(fk.getName() != null ? fk.getName() : defaultId)
                .source(tableName)
                .target(fk.getReferencedTable())
                .sourceColumn(fk.getColumn())
                .targetColumn(fk.getReferencedColumn())
                .label(fk.getColumn() + " -> " + fk.getReferencedColumn())
                .virtual(virtual)
                .build();
    }

    @Override
    public InferVirtualFkResultVO inferVirtualForeignKeys(ErDiagramQueryParam param) {
        List<VirtualForeignKey> existingVirtualForeignKeys = foreignKeySyncService.queryAllVirtualForeignKeys(
                param.getDataSourceId(),
                param.getDatabaseName(),
                param.getSchemaName()
        );

        List<Table> tables = queryTablesForInference(param);

        List<String> existingTableNames = tables.stream()
                .map(Table::getName)
                .collect(Collectors.toList());

        Map<String, Table> tableMap = tables.stream()
                .filter(table -> table.getName() != null)
                .collect(Collectors.toMap(
                        table -> normalizeName(table.getName()),
                        table -> table,
                        (first, second) -> first
                ));
        Map<String, Table> targetTableCache = new HashMap<>(tableMap);

        boolean cleanInvalidVirtualForeignKeys = param.getTableNameFilter() == null
                || param.getTableNameFilter().isBlank();
        // 复用推断开始时的虚拟外键快照，避免后续按表重复查询 H2；有表过滤时不做清理，防止误删过滤范围外的外键。
        List<VirtualForeignKey> invalidVirtualForeignKeys = cleanInvalidVirtualForeignKeys
                ? existingVirtualForeignKeys.stream()
                        .filter(vfk -> !tableMap.containsKey(normalizeName(vfk.getTableName()))
                                || !tableMap.containsKey(normalizeName(vfk.getReferencedTable())))
                        .collect(Collectors.toList())
                : Collections.emptyList();

        int cleanedCount = cleanInvalidVirtualForeignKeys
                ? foreignKeySyncService.cleanInvalidVirtualForeignKeys(
                        param.getDataSourceId(),
                        param.getDatabaseName(),
                        param.getSchemaName(),
                        existingTableNames
                )
                : 0;
        log.info("Cleaned {} invalid virtual foreign keys before inference", cleanedCount);

        List<VirtualForeignKey> validVirtualForeignKeys = existingVirtualForeignKeys.stream()
                .filter(vfk -> !invalidVirtualForeignKeys.contains(vfk))
                .collect(Collectors.toList());
        Map<String, List<VirtualForeignKey>> virtualForeignKeysByTable = validVirtualForeignKeys.stream()
                .filter(vfk -> vfk.getTableName() != null)
                .collect(Collectors.groupingBy(vfk -> normalizeName(vfk.getTableName())));

        Set<String> tablesWithVirtualForeignKeys = validVirtualForeignKeys.stream()
                .map(VirtualForeignKey::getTableName)
                .filter(Objects::nonNull)
                .map(this::normalizeName)
                .collect(Collectors.toSet());

        List<VirtualForeignKey> addedList = new ArrayList<>();
        for (Table table : tables) {
            if (table.getName() != null
                    && tablesWithVirtualForeignKeys.contains(normalizeName(table.getName()))) {
                continue;
            }
            List<VirtualForeignKey> inferredFKs = findVirtualForeignKeys(
                    table,
                    targetTableCache,
                    param,
                    virtualForeignKeysByTable.getOrDefault(normalizeName(table.getName()), Collections.emptyList())
            );
            for (VirtualForeignKey vfk : inferredFKs) {
                try {
                    foreignKeySyncService.createVirtualFK(
                            CreateVirtualFKParam.builder()
                                    .dataSourceId(param.getDataSourceId())
                                    .databaseName(param.getDatabaseName())
                                    .schemaName(param.getSchemaName())
                                    .tableName(table.getName())
                                    .columnName(vfk.getColumn())
                                    .referencedTable(vfk.getReferencedTable())
                                    .referencedColumnName(vfk.getReferencedColumn())
                                    .comment("Inferred from column naming convention")
                                    .sourceType("INFERRED")
                                    .build()
                    );
                    addedList.add(vfk);
                } catch (Exception e) {
                    log.warn("Failed to create inferred virtual FK for {}.{} -> {}.{}",
                            table.getName(), vfk.getColumn(),
                            vfk.getReferencedTable(), vfk.getReferencedColumn(), e);
                }
            }
        }

        List<InferVirtualFkResultVO.VirtualFkItem> deletedItems = invalidVirtualForeignKeys.stream()
                .map(vfk -> InferVirtualFkResultVO.VirtualFkItem.builder()
                        .tableName(vfk.getTableName())
                        .columnName(vfk.getColumn())
                        .referencedTable(vfk.getReferencedTable())
                        .referencedColumnName(vfk.getReferencedColumn())
                        .build())
                .collect(Collectors.toList());

        List<InferVirtualFkResultVO.VirtualFkItem> addedItems = addedList.stream()
                .map(vfk -> InferVirtualFkResultVO.VirtualFkItem.builder()
                        .tableName(vfk.getTableName())
                        .columnName(vfk.getColumn())
                        .referencedTable(vfk.getReferencedTable())
                        .referencedColumnName(vfk.getReferencedColumn())
                        .build())
                .collect(Collectors.toList());

        InferVirtualFkResultVO result = InferVirtualFkResultVO.builder()
                .addedCount(addedItems.size())
                .deletedCount(deletedItems.size())
                .added(addedItems)
                .deleted(deletedItems)
                .build();

        return result;
    }

    private List<Table> queryTablesForInference(ErDiagramQueryParam param) {
        TablePageQueryParam tablePageQueryParam = TablePageQueryParam.builder()
                .dataSourceId(param.getDataSourceId())
                .databaseName(param.getDatabaseName())
                .schemaName(param.getSchemaName())
                .searchKey(param.getTableNameFilter())
                .build();
        TableSelector selector = new TableSelector();
        selector.setColumnList(true);
        selector.setIndexList(true);

        List<Table> tables = tableService.pageQuery(tablePageQueryParam, selector);
        List<ForeignKey> realForeignKeys = foreignKeySyncService.queryRealForeignKeys(
                param.getDataSourceId(),
                param.getDatabaseName(),
                param.getSchemaName(),
                null
        );
        Map<String, List<ForeignKey>> realForeignKeysByTable = realForeignKeys.stream()
                .filter(fk -> fk.getTableName() != null)
                .collect(Collectors.groupingBy(fk -> normalizeName(fk.getTableName())));

        // 真实外键一次批量加载后回填到表对象；虚拟外键由调用方的全量快照处理。
        for (Table table : tables) {
            table.setForeignKeyList(realForeignKeysByTable.getOrDefault(
                    normalizeName(table.getName()),
                    Collections.emptyList()
            ));
        }
        return tables;
    }

    /**
     * 发现可能的虚拟外键关系（根据命名规范推断）
     * 参考 TableServiceImpl.findVirtualForeignKeys 实现
     */
    private List<VirtualForeignKey> findVirtualForeignKeys(Table table,
                                                           Map<String, Table> targetTableCache,
                                                           ErDiagramQueryParam param,
                                                           List<VirtualForeignKey> storedVirtualFKs) {
        List<VirtualForeignKey> result = new ArrayList<>();

        // 预加载已明确声明的外键列名（用于排除已存在的外键）
        Set<String> explicitForeignKeys = defaultList(table.getForeignKeyList()).stream()
                .map(ForeignKey::getColumn)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // 排除唯一索引列
        if (CollectionUtils.isNotEmpty(table.getIndexList())) {
            table.getIndexList().stream()
                    .filter(index -> Boolean.TRUE.equals(index.getUnique()))
                    .map(TableIndex::getColumnList)
                    .flatMap(List::stream)
                    .map(TableIndexColumn::getColumnName)
                    .forEach(explicitForeignKeys::add);
        }

        // 调用方传入预加载的虚拟外键，避免每张表单独查询一次 H2。
        Set<String> existingVirtualFKColumns = storedVirtualFKs.stream()
                .map(VirtualForeignKey::getColumn)
                .collect(Collectors.toSet());

        // 筛选候选列
        if (CollectionUtils.isNotEmpty(table.getColumnList())) {
            for (TableColumn column : table.getColumnList()) {
                if (isPotentialVirtualKeyCandidate(column)
                        && !explicitForeignKeys.contains(column.getName())
                        && !existingVirtualFKColumns.contains(column.getName())) {
                    VirtualForeignKey vfk = analyzeColumnRelation(table, column, targetTableCache, param);
                    if (vfk != null) {
                        result.add(vfk);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 判断列是否为虚拟外键候选列
     */
    private boolean isPotentialVirtualKeyCandidate(TableColumn column) {
        return column.getName() != null
                && column.getName().endsWith("_id")
                && Boolean.FALSE.equals(column.getPrimaryKey())
                && column.getName().length() > 3;
    }

    /**
     * 分析列关联关系并构建虚拟外键
     */
    private VirtualForeignKey analyzeColumnRelation(Table currentTable,
                                                    TableColumn currentColumn,
                                                    Map<String, Table> targetTableCache,
                                                    ErDiagramQueryParam param) {
        String columnName = currentColumn.getName();
        String referencedTableName = columnName.substring(0, columnName.length() - 3);
        String currentTableName = currentTable.getName();

        // 排除自关联
        if (referencedTableName.equalsIgnoreCase(currentTableName)) {
            return null;
        }

        // 先从已加载表中匹配；若当前请求有过滤导致目标表缺失，则按引用表名缓存一次补查结果。
        Table targetTable = resolveTargetTable(currentTable, currentColumn, referencedTableName, targetTableCache, param);
        if (targetTable == null) {
            return null;
        }

        // 查找目标表的关联列
        String referencedColumnName = "id";
        if (CollectionUtils.isNotEmpty(targetTable.getColumnList())) {
            for (TableColumn tableColumn : targetTable.getColumnList()) {
                if (columnName.equalsIgnoreCase(tableColumn.getName())) {
                    referencedColumnName = tableColumn.getName();
                    break;
                } else if ("id".equals(referencedColumnName) && Boolean.TRUE.equals(tableColumn.getPrimaryKey())) {
                    referencedColumnName = tableColumn.getName();
                }
            }
        }

        return VirtualForeignKey.builder()
                .name(String.format("VFK_%s_%s", currentTableName, columnName))
                .tableName(currentTableName)
                .column(columnName)
                .referencedTable(targetTable.getName())
                .referencedColumn(referencedColumnName)
                .virtualProperty("Inferred from column naming convention")
                .build();
    }

    private Table resolveTargetTable(Table currentTable,
                                     TableColumn currentColumn,
                                     String referencedTableName,
                                     Map<String, Table> targetTableCache,
                                     ErDiagramQueryParam param) {
        String cacheKey = normalizeName(referencedTableName);
        if (targetTableCache.containsKey(cacheKey)) {
            return targetTableCache.get(cacheKey);
        }

        TableSelector selector = new TableSelector();
        selector.setColumnList(true);
        List<Table> tables = tableService.pageQuery(
                TablePageQueryParam.builder()
                        .dataSourceId(param.getDataSourceId())
                        .databaseName(currentColumn.getDatabaseName())
                        .schemaName(currentColumn.getSchemaName())
                        .searchKey(referencedTableName)
                        .build(),
                selector
        );
        Table targetTable = tables.stream()
                .filter(t -> currentTable.getName() == null || !currentTable.getName().equalsIgnoreCase(t.getName()))
                .findFirst()
                .orElse(null);
        targetTableCache.put(cacheKey, targetTable);
        return targetTable;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private <T> List<T> defaultList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
