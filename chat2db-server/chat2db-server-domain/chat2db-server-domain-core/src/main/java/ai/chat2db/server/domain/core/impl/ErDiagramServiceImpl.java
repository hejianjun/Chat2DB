package ai.chat2db.server.domain.core.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.chat2db.server.domain.api.param.ErDiagramQueryParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.TableSelector;
import ai.chat2db.server.domain.api.service.ErDiagramService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.spi.model.ErDiagram;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.VirtualForeignKey;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ErDiagramServiceImpl implements ErDiagramService {

    @Autowired
    private TableService tableService;

    @Override
    public DataResult<ErDiagram> queryErDiagram(ErDiagramQueryParam param) {
        List<Table> tables = queryTables(param);
        List<ErDiagram.Node> nodes = buildNodes(tables);
        Set<String> tableNameSet = tables.stream().map(Table::getName).collect(Collectors.toSet());
        boolean includeVirtual = param.getIncludeVirtualFk() == null || param.getIncludeVirtualFk();
        List<ErDiagram.Edge> edges = buildEdges(tables, tableNameSet, includeVirtual);
        return DataResult.of(ErDiagram.builder().nodes(nodes).edges(edges).build());
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
        return tableService.pageQuery(tablePageQueryParam, selector).getData();
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
}