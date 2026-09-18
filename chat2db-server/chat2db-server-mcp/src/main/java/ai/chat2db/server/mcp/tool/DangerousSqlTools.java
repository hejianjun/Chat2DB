package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.param.DlBatchUpdateParam;
import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.McpApprovalSupport;
import ai.chat2db.server.mcp.support.ResolvedDataSource;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import ai.chat2db.server.tools.common.util.I18nUtils;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for intentionally dangerous SQL paths that require explicit server opt-in and client-side approval.
 */
@Service
public class DangerousSqlTools {

    private final DlTemplateService dlTemplateService;
    private final ConnectionScope connectionScope;
    private final ResultFormatter resultFormatter;
    private final SqlGuard sqlGuard;
    private final McpApprovalSupport approvalSupport;

    public DangerousSqlTools(DlTemplateService dlTemplateService, ConnectionScope connectionScope,
                             ResultFormatter resultFormatter, SqlGuard sqlGuard, McpApprovalSupport approvalSupport) {
        this.dlTemplateService = dlTemplateService;
        this.connectionScope = connectionScope;
        this.resultFormatter = resultFormatter;
        this.sqlGuard = sqlGuard;
        this.approvalSupport = approvalSupport;
    }

    @McpTool(description = "DESTRUCTIVE P2 DML: Execute INSERT/UPDATE/DELETE SQL statements in one transaction. "
        + "Requires write permission and a non-release datasource.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String executeUpdate(McpSyncServerExchange exchange,
                                @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
                                @McpToolParam(description = "Database name") String databaseName,
                                @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
                                @McpToolParam(description = "INSERT/UPDATE/DELETE SQL list. Each item must contain exactly one statement.") List<String> sqlList,
                                @McpToolParam(description = "Expected updated row count for each SQL item. Must have the same size as sqlList.") List<Integer> expectedUpdateCounts) {
        return connectionScope.run(datasourceUri, databaseName, schemaName,
            resolvedDataSource -> {
                sqlGuard.requireWriteAllowed(resolvedDataSource.getDataSourceId());
                sqlGuard.requireWriteSqlList(sqlList);
                sqlGuard.requireExpectedUpdateCounts(sqlList, expectedUpdateCounts);

                List<String> statements = List.copyOf(sqlList);
                List<Integer> expectedCounts = List.copyOf(expectedUpdateCounts);
                var approval = approvalSupport.confirm(exchange, "DML SQL",
                    target(resolvedDataSource, databaseName, schemaName),
                    I18nUtils.getMessage("mcp.approval.sqlPreview",
                        new Object[] {expectedCounts, StringUtils.join(statements, "\n---\n")}));
                if (!approval.approved()) {
                    return approval.message();
                }

                // Re-check the policy after the user responds in case configuration or datasource state changed.
                sqlGuard.requireWriteAllowed(resolvedDataSource.getDataSourceId());
                sqlGuard.requireWriteSqlList(statements);
                sqlGuard.requireExpectedUpdateCounts(statements, expectedCounts);
                return resultFormatter.format(dlTemplateService.executeBatchUpdate(
                    batchUpdateParam(resolvedDataSource.getDataSourceId(), databaseName, schemaName,
                        statements, expectedCounts)));
            });
    }

    @McpTool(description = "DESTRUCTIVE P2 DDL: Execute one CREATE/ALTER/DROP/TRUNCATE/RENAME/COMMENT/GRANT/REVOKE SQL statement. "
        + "Requires DDL permission and a non-release datasource.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String executeDdl(McpSyncServerExchange exchange,
                             @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
                             @McpToolParam(description = "Database name") String databaseName,
                             @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
                             @McpToolParam(description = "One DDL SQL statement") String sql) {
        return connectionScope.run(datasourceUri, databaseName, schemaName,
            resolvedDataSource -> {
                sqlGuard.requireDdlAllowed(resolvedDataSource.getDataSourceId());
                sqlGuard.requireSingleDdlSql(sql);

                var approval = approvalSupport.confirm(exchange, "DDL SQL",
                    target(resolvedDataSource, databaseName, schemaName), sql);
                if (!approval.approved()) {
                    return approval.message();
                }

                // Re-check the policy after the user responds in case configuration or datasource state changed.
                sqlGuard.requireDdlAllowed(resolvedDataSource.getDataSourceId());
                sqlGuard.requireSingleDdlSql(sql);
                return resultFormatter.format(dlTemplateService.execute(
                    executeParam(resolvedDataSource.getDataSourceId(), databaseName, schemaName, sql)));
            });
    }

    private String target(ResolvedDataSource dataSource, String databaseName, String schemaName) {
        return dataSource.getSummary() + "/" + StringUtils.defaultString(databaseName)
            + (StringUtils.isBlank(schemaName) ? "" : "." + schemaName);
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
