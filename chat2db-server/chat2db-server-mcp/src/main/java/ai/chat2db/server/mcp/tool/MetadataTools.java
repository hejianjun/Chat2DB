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
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.SimpleTable;
import ai.chat2db.spi.model.Table;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for table, schema and metadata preference operations.
 */
@Service
public class MetadataTools {

    private final DatabaseService databaseService;
    private final TableService tableService;
    private final ConnectionScope connectionScope;

    public MetadataTools(DatabaseService databaseService, TableService tableService, ConnectionScope connectionScope) {
        this.databaseService = databaseService;
        this.tableService = tableService;
        this.connectionScope = connectionScope;
    }

    @Tool(description = "List databases for a datasource. Read-only metadata operation.")
    public List<Database> listDatabases(@ToolParam(description = "Datasource id") Long dataSourceId) {
        return connectionScope.run(dataSourceId, null, null, () -> {
            DatabaseQueryAllParam param = new DatabaseQueryAllParam();
            param.setDataSourceId(dataSourceId);
            return databaseService.queryAll(param);
        });
    }

    @Tool(description = "List schemas under a database. Read-only metadata operation.")
    public List<Schema> listSchemas(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName) {
        return connectionScope.run(dataSourceId, databaseName, null, () -> {
            SchemaQueryParam param = new SchemaQueryParam();
            param.setDataSourceId(dataSourceId);
            param.setDataBaseName(databaseName);
            return databaseService.querySchema(param);
        });
    }

    @Tool(description = "List tables in a database/schema. Supports optional keyword search. Read-only metadata operation.")
    public List<SimpleTable> listTables(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Optional table search keyword", required = false) String keyword) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            TablePageQueryParam param = new TablePageQueryParam();
            param.setDataSourceId(dataSourceId);
            param.setDatabaseName(databaseName);
            param.setSchemaName(schemaName);
            param.setSearchKey(keyword);
            param.setPageNo(1);
            param.setPageSize(1000);
            return tableService.queryTables(param);
        });
    }

    @Tool(description = "Mark a table as deprecated and hide it from normal table lists. Read-only metadata preference operation.")
    public String deprecateTable(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name") String tableName) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            tableService.deprecatedTable(deprecatedTableParam(dataSourceId, databaseName, schemaName, tableName));
            return "Deprecated table: " + tableName;
        });
    }

    @Tool(description = "Restore a deprecated table from recycle bin back to normal table lists. Read-only metadata preference operation.")
    public String restoreDeprecatedTable(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name") String tableName) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            tableService.deleteDeprecatedTable(deprecatedTableParam(dataSourceId, databaseName, schemaName, tableName));
            return "Restored table: " + tableName;
        });
    }

    @Tool(description = "List deprecated tables in the recycle bin for a database/schema. Read-only metadata operation.")
    public List<Table> listDeprecatedTables(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Optional table search keyword", required = false) String keyword) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            TablePageQueryParam param = new TablePageQueryParam();
            param.setDataSourceId(dataSourceId);
            param.setDatabaseName(databaseName);
            param.setSchemaName(schemaName);
            param.setSearchKey(keyword);
            param.setPageNo(1);
            param.setPageSize(1000);
            return tableService.pageQueryDeprecated(param, TableSelector.builder().columnList(false).indexList(false).build());
        });
    }

    @Tool(description = "Save AI comment for a table or one column. Pass columnName only when updating a column AI comment. Metadata-only operation.")
    public String saveAiComment(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name") String tableName,
        @ToolParam(description = "Column name, optional. Omit to update table AI comment", required = false) String columnName,
        @ToolParam(description = "AI comment text") String aiComment) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            tableService.saveAiComment(dataSourceId, databaseName, schemaName, tableName, columnName, aiComment);
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
