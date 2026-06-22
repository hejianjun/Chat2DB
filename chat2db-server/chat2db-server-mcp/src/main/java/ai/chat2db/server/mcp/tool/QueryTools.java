package ai.chat2db.server.mcp.tool;

import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for safe read-only SQL execution.
 */
@Service
public class QueryTools {

    private final DlTemplateService dlTemplateService;
    private final ConnectionScope connectionScope;
    private final ResultFormatter resultFormatter;
    private final SqlGuard sqlGuard;

    public QueryTools(DlTemplateService dlTemplateService, ConnectionScope connectionScope,
        ResultFormatter resultFormatter, SqlGuard sqlGuard) {
        this.dlTemplateService = dlTemplateService;
        this.connectionScope = connectionScope;
        this.resultFormatter = resultFormatter;
        this.sqlGuard = sqlGuard;
    }

    @McpTool(description = "Execute a read-only SQL query. Only SELECT/SHOW/DESC/DESCRIBE/EXPLAIN/WITH are allowed. Use this for normal data exploration."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String executeSelect(@McpToolParam(description = "Datasource id") Long dataSourceId,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Read-only SQL") String sql,
        @McpToolParam(description = "Page number, default 1", required = false) Integer pageNo,
        @McpToolParam(description = "Page size, default 100, capped by server config", required = false) Integer pageSize) {
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> {
                sqlGuard.requireSingleReadOnlySql(sql);
                return resultFormatter.formatCsv(dlTemplateService.execute(
                    executeParam(dataSourceId, databaseName, schemaName, sql, pageNo, pageSize)));
            });
    }

    DlExecuteParam executeParam(Long dataSourceId, String databaseName, String schemaName, String sql, Integer pageNo,
        Integer pageSize) {
        // Reuse the existing DL execution service while enforcing MCP-specific paging limits.
        DlExecuteParam param = new DlExecuteParam();
        param.setDataSourceId(dataSourceId);
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        param.setConsoleId(0L);
        param.setSql(sql);
        param.setPageNo(pageNo == null ? 1 : pageNo);
        param.setPageSize(sqlGuard.normalizePageSize(pageSize));
        param.setPageSizeAll(false);
        return param;
    }
}
