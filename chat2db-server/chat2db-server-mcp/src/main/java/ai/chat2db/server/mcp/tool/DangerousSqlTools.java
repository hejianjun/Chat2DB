package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.param.DlBatchUpdateParam;
import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.PreparedSqlChangeStore;
import ai.chat2db.server.mcp.support.PreparedSqlChangeStore.PreparedSqlChange;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
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
    private final PreparedSqlChangeStore preparedSqlChangeStore;

    public DangerousSqlTools(DlTemplateService dlTemplateService, ConnectionScope connectionScope,
                             ResultFormatter resultFormatter, SqlGuard sqlGuard,
                             PreparedSqlChangeStore preparedSqlChangeStore) {
        this.dlTemplateService = dlTemplateService;
        this.connectionScope = connectionScope;
        this.resultFormatter = resultFormatter;
        this.sqlGuard = sqlGuard;
        this.preparedSqlChangeStore = preparedSqlChangeStore;
    }

    @McpTool(description = "DESTRUCTIVE P2 PREPARE: Prepare INSERT/UPDATE/DELETE SQL statements. This does not execute SQL. Returns operationId, target summary, SQL preview and exact confirmationText required by executePreparedSqlChange."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String prepareUpdateSqlChange(@McpToolParam(description = "Datasource ref from listDatasources") String datasourceRef,
                                @McpToolParam(description = "Database name") String databaseName,
                                @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
                                @McpToolParam(description = "INSERT/UPDATE/DELETE SQL list. Each item must contain exactly one statement.") List<String> sqlList,
                                @McpToolParam(description = "Expected updated row count for each SQL item. Must have the same size as sqlList.") List<Integer> expectedUpdateCounts) {
        return connectionScope.run(datasourceRef, databaseName, schemaName,
                resolvedDataSource -> {
                    sqlGuard.requireWriteAllowed(resolvedDataSource.getDataSourceId(), true);
                    sqlGuard.requireWriteSqlList(sqlList);
                    sqlGuard.requireExpectedUpdateCounts(sqlList, expectedUpdateCounts);
                    PreparedSqlChange change = preparedSqlChangeStore.prepare("UPDATE", datasourceRef, resolvedDataSource,
                        databaseName, schemaName, sqlList, expectedUpdateCounts);
                    return formatPreparedChange(change);
                });
    }

    @McpTool(description = "DESTRUCTIVE P2 PREPARE: Prepare DDL SQL such as CREATE/ALTER/DROP/TRUNCATE. This does not execute SQL. Returns operationId, target summary, SQL preview and exact confirmationText required by executePreparedSqlChange."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String prepareDdlSqlChange(@McpToolParam(description = "Datasource ref from listDatasources") String datasourceRef,
                             @McpToolParam(description = "Database name") String databaseName,
                             @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
                             @McpToolParam(description = "DDL SQL") String sql) {
        return connectionScope.run(datasourceRef, databaseName, schemaName,
                resolvedDataSource -> {
                    sqlGuard.requireDdlAllowed(resolvedDataSource.getDataSourceId(), true);
                    sqlGuard.requireSingleDdlSql(sql);
                    PreparedSqlChange change = preparedSqlChangeStore.prepare("DDL", datasourceRef, resolvedDataSource,
                        databaseName, schemaName, List.of(sql), null);
                    return formatPreparedChange(change);
                });
    }

    @McpTool(description = "DESTRUCTIVE P2 EXECUTE: Execute a previously prepared SQL change. The caller must pass operationId and the exact confirmationText returned by prepare. The datasource and SQL cannot be changed at this step."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String executePreparedSqlChange(@McpToolParam(description = "operationId returned by prepareUpdateSqlChange or prepareDdlSqlChange") String operationId,
        @McpToolParam(description = "Exact confirmationText returned by prepareUpdateSqlChange or prepareDdlSqlChange") String confirmationText) {
        PreparedSqlChange change = preparedSqlChangeStore.consume(operationId, confirmationText);
        return connectionScope.run(change.getDatasourceRef(), change.getDatabaseName(), change.getSchemaName(),
            resolvedDataSource -> {
                if ("UPDATE".equals(change.getOperationType())) {
                    sqlGuard.requireWriteAllowed(resolvedDataSource.getDataSourceId(), true);
                    sqlGuard.requireWriteSqlList(change.getSqlList());
                    sqlGuard.requireExpectedUpdateCounts(change.getSqlList(), change.getExpectedUpdateCounts());
                    return resultFormatter.format(dlTemplateService.executeBatchUpdate(
                        batchUpdateParam(resolvedDataSource.getDataSourceId(), change.getDatabaseName(),
                            change.getSchemaName(), change.getSqlList(), change.getExpectedUpdateCounts())));
                }
                sqlGuard.requireDdlAllowed(resolvedDataSource.getDataSourceId(), true);
                sqlGuard.requireSingleDdlSql(change.getSqlList().get(0));
                return resultFormatter.format(dlTemplateService.execute(executeParam(resolvedDataSource.getDataSourceId(),
                    change.getDatabaseName(), change.getSchemaName(), change.getSqlList().get(0))));
            });
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

    private String formatPreparedChange(PreparedSqlChange change) {
        return "operationId: " + change.getOperationId() + '\n'
            + "operationType: " + change.getOperationType() + '\n'
            + "target: " + change.getTarget() + '\n'
            + "sqlDigest: sha256:" + change.getSqlDigest() + '\n'
            + "confirmationText: " + change.getConfirmationText() + '\n'
            + "sqlPreview:\n" + StringUtils.join(change.getSqlList(), "\n---\n") + '\n';
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
