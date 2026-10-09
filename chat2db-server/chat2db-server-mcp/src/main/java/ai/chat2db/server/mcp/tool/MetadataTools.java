package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.param.DeprecatedTableParam;
import ai.chat2db.server.domain.api.param.SchemaQueryParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.TableSelector;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.SimpleTable;
import ai.chat2db.spi.model.Table;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * MCP tools for table, schema and metadata preference operations.
 */
@Service
public class MetadataTools {

    private final DatabaseService databaseService;
    private final TableService tableService;
    private final ConnectionScope connectionScope;
    private final ResultFormatter resultFormatter;

    public MetadataTools(@Lazy DatabaseService databaseService, @Lazy TableService tableService,
        ConnectionScope connectionScope, ResultFormatter resultFormatter) {
        this.databaseService = databaseService;
        this.tableService = tableService;
        this.connectionScope = connectionScope;
        this.resultFormatter = resultFormatter;
    }

    @McpTool(description = "List databases for a datasource as CSV. Read-only metadata operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String listDatabases(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri) {
        return connectionScope.run(datasourceUri, null, null, resolvedDataSource -> {
            DatabaseQueryAllParam param = new DatabaseQueryAllParam();
            param.setDataSourceId(resolvedDataSource.getDataSourceId());
            return resultFormatter.formatDatabasesCsv(databaseService.queryAll(param));
        });
    }

    @McpTool(description = "List schemas under a database as CSV. Read-only metadata operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String listSchemas(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName) {
        return connectionScope.run(datasourceUri, databaseName, null, resolvedDataSource -> {
            SchemaQueryParam param = new SchemaQueryParam();
            param.setDataSourceId(resolvedDataSource.getDataSourceId());
            param.setDataBaseName(databaseName);
            return resultFormatter.formatSchemasCsv(databaseService.querySchema(param));
        });
    }

    @McpTool(description = "List tables in a database/schema as CSV. Supports optional keyword search. Read-only metadata operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String listTables(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Optional table search keyword", required = false) String keyword) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> {
            TablePageQueryParam param = new TablePageQueryParam();
            param.setDataSourceId(resolvedDataSource.getDataSourceId());
            param.setDatabaseName(databaseName);
            param.setSchemaName(schemaName);
            param.setSearchKey(keyword);
            param.setPageNo(1);
            param.setPageSize(1000);
            return resultFormatter.formatSimpleTablesCsv(tableService.queryTables(param));
        });
    }

    @McpTool(description = "Mark a table as deprecated and hide it from normal table lists. Read-only metadata preference operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public String deprecateTable(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Table name") String tableName) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> {
            tableService.deprecatedTable(
                deprecatedTableParam(resolvedDataSource.getDataSourceId(), databaseName, schemaName, tableName));
            return "Deprecated table: " + tableName;
        });
    }

    @McpTool(description = "Restore a deprecated table from recycle bin back to normal table lists. Read-only metadata preference operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public String restoreDeprecatedTable(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Table name") String tableName) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> {
            tableService.deleteDeprecatedTable(
                deprecatedTableParam(resolvedDataSource.getDataSourceId(), databaseName, schemaName, tableName));
            return "Restored table: " + tableName;
        });
    }

    @McpTool(description = "List deprecated tables in the recycle bin for a database/schema as CSV. Read-only metadata operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String listDeprecatedTables(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Optional table search keyword", required = false) String keyword) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> {
            TablePageQueryParam param = new TablePageQueryParam();
            param.setDataSourceId(resolvedDataSource.getDataSourceId());
            param.setDatabaseName(databaseName);
            param.setSchemaName(schemaName);
            param.setSearchKey(keyword);
            param.setPageNo(1);
            param.setPageSize(1000);
            return resultFormatter.formatTablesCsv(
                tableService.pageQueryDeprecated(param, TableSelector.builder().columnList(false).indexList(false).build()));
        });
    }

    @McpTool(description = "Save AI comment for a table or one column. Pass columnName only when updating a column AI comment. Metadata-only operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public String saveAiComment(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Table name") String tableName,
        @McpToolParam(description = "Column name, optional. Omit to update table AI comment", required = false) String columnName,
        @McpToolParam(description = "AI comment text") String aiComment) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> {
            tableService.saveAiComment(resolvedDataSource.getDataSourceId(), databaseName, schemaName, tableName,
                columnName, aiComment);
            return columnName == null || columnName.isBlank()
                ? "Saved table AI comment: " + tableName
                : "Saved column AI comment: " + tableName + "." + columnName;
        });
    }

    private DeprecatedTableParam deprecatedTableParam(Long dataSourceId, String databaseName, String schemaName,
        String tableName) {
        // Both deprecate and restore operations use the same table identity payload.
        DeprecatedTableParam param = new DeprecatedTableParam();
        param.setDataSourceId(dataSourceId);
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        param.setTableName(tableName);
        return param;
    }
}
