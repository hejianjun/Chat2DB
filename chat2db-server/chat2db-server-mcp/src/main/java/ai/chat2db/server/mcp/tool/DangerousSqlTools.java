package ai.chat2db.server.mcp.tool;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.chat2db.server.domain.api.param.DlBatchUpdateParam;
import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResolvedDataSource;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for intentionally dangerous SQL paths that require explicit server opt-in and user elicitation.
 */
@Service
public class DangerousSqlTools {

    private static final String CONFIRM_FIELD = "confirm";

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

    @McpTool(description = "DESTRUCTIVE P2 DML: Execute INSERT/UPDATE/DELETE SQL statements in one transaction. "
        + "Requires allow-write=true and a non-release datasource. The server always requests MCP elicitation "
        + "before execution; only an explicit user acceptance with confirm=true executes SQL. "
        + "Do not use a separate confirmation parameter or try to execute this operation through another tool.",
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
                McpSchema.ElicitResult elicitationResult = requestConfirmation(exchange, "DML",
                    resolvedDataSource, databaseName, schemaName, statements, expectedCounts);
                if (!isAccepted(elicitationResult)) {
                    return noExecutionMessage("DML", elicitationResult);
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
        + "Requires allow-ddl=true and a non-release datasource. The server always requests MCP elicitation before "
        + "execution; only an explicit user acceptance with confirm=true executes SQL. "
        + "Do not use a separate confirmation parameter or try to execute this operation through another tool.",
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

                McpSchema.ElicitResult elicitationResult = requestConfirmation(exchange, "DDL",
                    resolvedDataSource, databaseName, schemaName, List.of(sql), null);
                if (!isAccepted(elicitationResult)) {
                    return noExecutionMessage("DDL", elicitationResult);
                }

                // Re-check the policy after the user responds in case configuration or datasource state changed.
                sqlGuard.requireDdlAllowed(resolvedDataSource.getDataSourceId());
                sqlGuard.requireSingleDdlSql(sql);
                return resultFormatter.format(dlTemplateService.execute(
                    executeParam(resolvedDataSource.getDataSourceId(), databaseName, schemaName, sql)));
            });
    }

    private McpSchema.ElicitResult requestConfirmation(McpSyncServerExchange exchange, String operationType,
                                                        ResolvedDataSource resolvedDataSource, String databaseName,
                                                        String schemaName, List<String> sqlList,
                                                        List<Integer> expectedUpdateCounts) {
        if (exchange == null || exchange.getClientCapabilities() == null
            || exchange.getClientCapabilities().elicitation() == null) {
            throw new IllegalStateException("MCP client does not advertise elicitation capability. "
                + operationType + " SQL was not executed.");
        }
        String expected = expectedUpdateCounts == null ? "not applicable"
            : StringUtils.join(expectedUpdateCounts, ", ");
        String message = "Chat2DB requests explicit user approval for destructive " + operationType + " SQL.\n"
            + "Target: " + target(resolvedDataSource, databaseName, schemaName) + "\n"
            + "Expected update counts: " + expected + "\n"
            + "Review the exact SQL below. No SQL will execute unless you accept this request and submit confirm=true.\n"
            + "SQL:\n" + StringUtils.join(sqlList, "\n---\n");
        return exchange.createElicitation(McpSchema.ElicitRequest.builder()
            .message(message)
            .requestedSchema(Map.of(
                "type", "object",
                "properties", Map.of(CONFIRM_FIELD, Map.of(
                    "type", "boolean",
                    "title", "Confirm database change",
                    "description", "Confirm that the displayed SQL should be executed now.",
                    "default", false)),
                "required", List.of(CONFIRM_FIELD)))
            .build());
    }

    private boolean isAccepted(McpSchema.ElicitResult result) {
        return result != null && result.action() == McpSchema.ElicitResult.Action.ACCEPT
            && result.content() != null && Boolean.TRUE.equals(result.content().get(CONFIRM_FIELD));
    }

    private String noExecutionMessage(String operationType, McpSchema.ElicitResult result) {
        String action = result == null || result.action() == null ? "unknown"
            : result.action().name().toLowerCase(Locale.ROOT);
        return "No SQL was executed. The destructive " + operationType
            + " operation was not confirmed (elicitation action: " + action + ").";
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
