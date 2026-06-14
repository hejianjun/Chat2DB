package ai.chat2db.server.mcp.tool;

import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class DangerousSqlTools {

    private final DlTemplateService dlTemplateService;
    private final ConnectionScope connectionScope;
    private final ResultFormatter resultFormatter;
    private final SqlGuard sqlGuard;

    public DangerousSqlTools(DlTemplateService dlTemplateService, ConnectionScope connectionScope,
        ResultFormatter resultFormatter, SqlGuard sqlGuard) {
        this.dlTemplateService = dlTemplateService;
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
}
