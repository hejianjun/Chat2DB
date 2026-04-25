package ai.chat2db.server.domain.core.impl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.chat2db.server.domain.api.param.ErDiagramQueryParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.domain.api.param.TableSelector;
import ai.chat2db.server.domain.api.service.ErDiagramService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.model.ErDiagram;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.VirtualForeignKey;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;

/**
 * ER图服务实现类
 * 根据数据库表和外键关系构建ER图数据
 */
@Service
@Slf4j
public class ErDiagramServiceImpl implements ErDiagramService {

    @Autowired
    private TableService tableService;

    /**
     * 查询ER图数据
     * 1. 获取数据库中所有表及其列、索引、外键信息
     * 2. 根据表名过滤条件筛选表
     * 3. 构建节点（表）和边（外键关系）
     * 4. 支持真实外键和虚拟外键（根据命名规范推断）
     */
    @Override
    public DataResult<ErDiagram> queryErDiagram(ErDiagramQueryParam param) {
        // 构建表查询参数，获取所有表及其详细信息
        TablePageQueryParam tablePageQueryParam = TablePageQueryParam.builder()
                .dataSourceId(param.getDataSourceId())
                .databaseName(param.getDatabaseName())
                .schemaName(param.getSchemaName())
                .pageNo(1)
                .pageSize(Integer.MAX_VALUE)
                .build();

        // 设置选择器，获取列、索引、外键信息
        TableSelector selector = new TableSelector();
        selector.setColumnList(true);
        selector.setIndexList(true);
        selector.setForeignKey(true);

        // 查询表列表
        PageResult<Table> tableResult = tableService.pageQuery(tablePageQueryParam, selector);
        List<Table> tables = tableResult.getData();

        // 根据表名过滤条件筛选表
        if (StringUtils.isNotBlank(param.getTableNameFilter())) {
            String filter = param.getTableNameFilter().toLowerCase();
            tables = tables.stream()
                    .filter(t -> t.getName() != null && t.getName().toLowerCase().contains(filter))
                    .collect(Collectors.toList());
        }

        // 构建节点和边列表
        List<ErDiagram.Node> nodes = new ArrayList<>();
        List<ErDiagram.Edge> edges = new ArrayList<>();
        Map<String, ErDiagram.Node> nodeMap = new HashMap<>();

        // 构建节点（每张表对应一个节点）
        for (Table table : tables) {
            ErDiagram.Node node = ErDiagram.Node.builder()
                    .id(table.getName())
                    .name(table.getName())
                    .comment(table.getComment())
                    .columnCount(CollectionUtils.isEmpty(table.getColumnList()) ? 0 : table.getColumnList().size())
                    .build();
            nodes.add(node);
            nodeMap.put(table.getName(), node);
        }

        // 获取表名集合，用于检查外键引用的目标表是否存在
        Set<String> tableNameSet = tables.stream()
                .map(Table::getName)
                .collect(Collectors.toSet());

        // 判断是否包含虚拟外键
        boolean includeVirtual = param.getIncludeVirtualFk() == null || param.getIncludeVirtualFk();

        // 构建边（外键关系）
        for (Table table : tables) {
            // 处理真实外键
            if (CollectionUtils.isNotEmpty(table.getForeignKeyList())) {
                for (ForeignKey fk : table.getForeignKeyList()) {
                    // 只添加目标表存在于当前表集合中的外键关系
                    if (tableNameSet.contains(fk.getReferencedTable())) {
                        ErDiagram.Edge edge = ErDiagram.Edge.builder()
                                .id(fk.getName() != null ? fk.getName() : (table.getName() + "_" + fk.getColumn() + "_" + fk.getReferencedTable()))
                                .source(table.getName())
                                .target(fk.getReferencedTable())
                                .sourceColumn(fk.getColumn())
                                .targetColumn(fk.getReferencedColumn())
                                .label(fk.getColumn() + " -> " + fk.getReferencedColumn())
                                .virtual(false)
                                .build();
                        edges.add(edge);
                    }
                }
            }

            // 处理虚拟外键（根据命名规范推断的外键）
            if (includeVirtual && CollectionUtils.isNotEmpty(table.getVirtualForeignKeyList())) {
                for (VirtualForeignKey vfk : table.getVirtualForeignKeyList()) {
                    if (tableNameSet.contains(vfk.getReferencedTable())) {
                        ErDiagram.Edge edge = ErDiagram.Edge.builder()
                                .id(vfk.getName() != null ? vfk.getName() : ("VFK_" + table.getName() + "_" + vfk.getColumn()))
                                .source(table.getName())
                                .target(vfk.getReferencedTable())
                                .sourceColumn(vfk.getColumn())
                                .targetColumn(vfk.getReferencedColumn())
                                .label(vfk.getColumn() + " -> " + vfk.getReferencedColumn())
                                .virtual(true)
                                .build();
                        edges.add(edge);
                    }
                }
            }
        }

        // 构建并返回ER图数据
        ErDiagram erDiagram = ErDiagram.builder()
                .nodes(nodes)
                .edges(edges)
                .build();

        return DataResult.of(erDiagram);
    }
}