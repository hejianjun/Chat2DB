package ai.chat2db.server.mcp.tool;

import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.param.DropParam;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Sql;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class DangerousSqlTools {

    private final DlTemplateService dlTemplateService;
    private final TableService tableService;
    private final DatabaseService databaseService;
    private final ConnectionScope connectionScope;
    private final ResultFormatter resultFormatter;
    private final SqlGuard sqlGuard;

    public DangerousSqlTools(DlTemplateService dlTemplateService, TableService tableService,
        DatabaseService databaseService, ConnectionScope connectionScope, ResultFormatter resultFormatter,
        SqlGuard sqlGuard) {
        this.dlTemplateService = dlTemplateService;
        this.tableService = tableService;
        this.databaseService = databaseService;
        this.connectionScope = connectionScope;
        this.resultFormatter = resultFormatter;
        this.sqlGuard = sqlGuard;
    }

    @Tool(description = "DESTRUCTIVE P2: Execute INSERT/UPDATE/DELETE SQL. Requires allow-write=true, non-release datasource environment, and confirm=true. MCP clients should show a confirmation dialog before calling.")
    public String executeUpdate(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "INSERT/UPDATE/DELETE SQL") String sql,
        @ToolParam(description = "Must be true after explicit user confirmation") Boolean confirm) {
        sqlGuard.requireWriteAllowed(dataSourceId, confirm);
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> resultFormatter.format(dlTemplateService.executeUpdate(executeParam(dataSourceId, databaseName, schemaName, sql))));
    }

    @Tool(description = "DESTRUCTIVE P2: Execute DDL SQL such as CREATE/ALTER/DROP. Requires allow-ddl=true, non-release datasource environment, and confirm=true. MCP clients should show a destructive confirmation dialog before calling.")
    public String executeDdl(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "DDL SQL") String sql,
        @ToolParam(description = "Must be true after explicit user confirmation") Boolean confirm) {
        sqlGuard.requireDdlAllowed(dataSourceId, confirm);
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> resultFormatter.format(dlTemplateService.execute(executeParam(dataSourceId, databaseName, schemaName, sql))));
    }

    @Tool(description = "DESTRUCTIVE P2: Drop a table. Requires allow-ddl=true, non-release datasource environment, and confirm=true. MCP clients should show a destructive confirmation dialog before calling.")
    public String dropTable(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name") String tableName,
        @ToolParam(description = "Must be true after explicit user confirmation") Boolean confirm) {
        sqlGuard.requireDdlAllowed(dataSourceId, confirm);
        connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            tableService.drop(dropParam(dataSourceId, databaseName, schemaName, tableName));
            return null;
        });
        return "Dropped table: " + tableName;
    }

    @Tool(description = "DESTRUCTIVE P2: Truncate a table. Requires allow-ddl=true, non-release datasource environment, and confirm=true. MCP clients should show a destructive confirmation dialog before calling.")
    public String truncateTable(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name") String tableName,
        @ToolParam(description = "Must be true after explicit user confirmation") Boolean confirm) {
        sqlGuard.requireDdlAllowed(dataSourceId, confirm);
        connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            tableService.truncate(dropParam(dataSourceId, databaseName, schemaName, tableName));
            return null;
        });
        return "Truncated table: " + tableName;
    }

    @Tool(description = "P2 DDL helper: Generate CREATE DATABASE SQL. Requires allow-ddl=true, non-release datasource environment, and confirm=true. This tool returns SQL only; call execute_ddl to execute it.")
    public Sql createDatabase(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name to create") String databaseName,
        @ToolParam(description = "Optional comment", required = false) String comment,
        @ToolParam(description = "Optional charset", required = false) String charset,
        @ToolParam(description = "Optional collation", required = false) String collation,
        @ToolParam(description = "Must be true after explicit user confirmation") Boolean confirm) {
        sqlGuard.requireDdlAllowed(dataSourceId, confirm);
        return connectionScope.run(dataSourceId, databaseName, null, () -> {
            Database database = new Database();
            database.setName(databaseName);
            database.setComment(comment);
            database.setCharset(charset);
            database.setCollation(collation);
            return databaseService.createDatabase(database);
        });
    }

    private DlExecuteParam executeParam(Long dataSourceId, String databaseName, String schemaName, String sql) {
        DlExecuteParam param = new DlExecuteParam();
        param.setDataSourceId(dataSourceId);
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        param.setConsoleId(0L);
        param.setSql(sql);
        param.setPageNo(1);
        param.setPageSize(100);
        param.setPageSizeAll(false);
        return param;
    }

    private DropParam dropParam(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        DropParam param = new DropParam();
        param.setDataSourceId(dataSourceId);
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        param.setTableName(tableName);
        return param;
    }
}
