package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.param.CreateVirtualFKParam;
import ai.chat2db.server.domain.api.param.UpdateVirtualFKParam;
import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.VirtualForeignKey;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for real database foreign keys and Chat2DB virtual foreign-key metadata.
 */
@Service
public class ForeignKeyTools {

    private final ForeignKeySyncService foreignKeySyncService;
    private final ConnectionScope connectionScope;

    public ForeignKeyTools(ForeignKeySyncService foreignKeySyncService, ConnectionScope connectionScope) {
        this.foreignKeySyncService = foreignKeySyncService;
        this.connectionScope = connectionScope;
    }

    @McpTool(description = "List real and virtual foreign keys for a table or its relationships. Read-only metadata operation. relation can be SOURCE, REFERENCED or ANY; default SOURCE."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<ForeignKey> listForeignKeys(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Table name, optional. Omit to list all foreign keys in the database/schema", required = false) String tableName,
        @McpToolParam(description = "Relation filter: SOURCE, REFERENCED, ANY. Default SOURCE", required = false) String relation) {
        return connectionScope.run(datasourceUri, databaseName, schemaName,
            resolvedDataSource -> foreignKeySyncService.listForeignKeys(resolvedDataSource.getDataSourceId(),
                databaseName, schemaName, tableName,
                parseRelation(relation)));
    }

    @McpTool(description = "List real database foreign keys only. Read-only metadata operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<ForeignKey> listRealForeignKeys(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Table name, optional. Omit to list all real foreign keys in the database/schema", required = false) String tableName) {
        return connectionScope.run(datasourceUri, databaseName, schemaName,
            resolvedDataSource -> foreignKeySyncService.queryRealForeignKeys(resolvedDataSource.getDataSourceId(),
                databaseName, schemaName, tableName));
    }

    @McpTool(description = "List virtual foreign keys only. Read-only metadata operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<VirtualForeignKey> listVirtualForeignKeys(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Table name, optional. Omit to list all virtual foreign keys in the database/schema", required = false) String tableName) {
        return connectionScope.run(datasourceUri, databaseName, schemaName,
            resolvedDataSource -> foreignKeySyncService.queryVirtualForeignKeys(resolvedDataSource.getDataSourceId(),
                databaseName, schemaName, tableName));
    }

    @McpTool(description = "Create a virtual foreign key. Metadata-only operation; it does not alter the physical database schema."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public VirtualForeignKey createVirtualForeignKey(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Source table name") String tableName,
        @McpToolParam(description = "Source column name") String columnName,
        @McpToolParam(description = "Referenced table name") String referencedTable,
        @McpToolParam(description = "Referenced column name") String referencedColumnName,
        @McpToolParam(description = "Comment, optional", required = false) String comment) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> foreignKeySyncService.createVirtualFK(
            CreateVirtualFKParam.builder()
                .dataSourceId(resolvedDataSource.getDataSourceId())
                .databaseName(databaseName)
                .schemaName(schemaName)
                .tableName(tableName)
                .columnName(columnName)
                .referencedTable(referencedTable)
                .referencedColumnName(referencedColumnName)
                .comment(comment)
                .build()));
    }

    @McpTool(description = "Update a virtual foreign key by id. Metadata-only operation; it does not alter the physical database schema."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public VirtualForeignKey updateVirtualForeignKey(@McpToolParam(description = "Virtual foreign key id") Long id,
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Source table name, optional", required = false) String tableName,
        @McpToolParam(description = "Source column name, optional", required = false) String columnName,
        @McpToolParam(description = "Referenced table name, optional", required = false) String referencedTable,
        @McpToolParam(description = "Referenced column name, optional", required = false) String referencedColumnName,
        @McpToolParam(description = "Virtual foreign key name, optional", required = false) String name,
        @McpToolParam(description = "Comment, optional", required = false) String comment) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> foreignKeySyncService.updateVirtualFK(
            UpdateVirtualFKParam.builder()
                .id(id)
                .dataSourceId(resolvedDataSource.getDataSourceId())
                .databaseName(databaseName)
                .schemaName(schemaName)
                .tableName(tableName)
                .columnName(columnName)
                .referencedTable(referencedTable)
                .referencedColumnName(referencedColumnName)
                .vkName(name)
                .comment(comment)
                .build()));
    }

    @McpTool(description = "Delete a virtual foreign key by id. Metadata-only operation; it does not alter the physical database schema."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public String deleteVirtualForeignKey(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Virtual foreign key id") Long id) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> {
            foreignKeySyncService.deleteVirtualFK(id);
            return "Deleted virtual foreign key: " + id;
        });
    }

    private ForeignKeySyncService.ForeignKeyRelation parseRelation(String relation) {
        // SOURCE matches the historical UI default: keys declared on the current table.
        if (relation == null || relation.isBlank()) {
            return ForeignKeySyncService.ForeignKeyRelation.SOURCE;
        }
        return ForeignKeySyncService.ForeignKeyRelation.valueOf(relation.trim().toUpperCase());
    }
}
