package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.param.DlBatchUpdateParam;
import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for intentionally dangerous SQL paths that require explicit server opt-in and confirmation.
 */
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

    @Tool(description = "DESTRUCTIVE P2: Execute INSERT/UPDATE/DELETE SQL statements in one transaction. Requires allow-write=true, non-release datasource environment, and confirm=true. Rolls back if any actual update count differs from expectedUpdateCounts.")
    public String executeUpdate(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "INSERT/UPDATE/DELETE SQL list. Each item must contain exactly one statement.") List<String> sqlList,
        @ToolParam(description = "Expected updated row count for each SQL item. Must have the same size as sqlList.") List<Integer> expectedUpdateCounts,
        @ToolParam(description = "Must be true after explicit user confirmation") Boolean confirm) {
        sqlGuard.requireWriteAllowed(dataSourceId, confirm);
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> {
                sqlGuard.requireWriteSqlList(sqlList);
                return resultFormatter.format(dlTemplateService.executeBatchUpdate(
                    batchUpdateParam(dataSourceId, databaseName, schemaName, sqlList, expectedUpdateCounts)));
            });
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
        // Dangerous operations do not need pagination, but DlExecuteParam still requires these defaults.
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

    private DlBatchUpdateParam batchUpdateParam(Long dataSourceId, String databaseName, String schemaName,
        List<String> sqlList, List<Integer> expectedUpdateCounts) {
        DlBatchUpdateParam param = new DlBatchUpdateParam();
        param.setDataSourceId(dataSourceId);
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        param.setConsoleId(0L);
        param.setSqlList(sqlList);
        param.setExpectedUpdateCounts(expectedUpdateCounts);
        return param;
    }
}
