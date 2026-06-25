package ai.chat2db.server.mcp.tool;

import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.FunctionService;
import ai.chat2db.server.domain.api.service.ProcedureService;
import ai.chat2db.server.domain.api.service.ViewService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for higher-level schema discovery such as views, functions, procedures and table search.
 */
@Service
public class SchemaInsightTools {

    private final DatabaseService databaseService;
    private final ViewService viewService;
    private final FunctionService functionService;
    private final ProcedureService procedureService;
    private final ConnectionScope connectionScope;

    public SchemaInsightTools(DatabaseService databaseService, ViewService viewService, FunctionService functionService,
        ProcedureService procedureService, ConnectionScope connectionScope) {
        this.databaseService = databaseService;
        this.viewService = viewService;
        this.functionService = functionService;
        this.procedureService = procedureService;
        this.connectionScope = connectionScope;
    }

    @McpTool(description = "List database objects of type view, function or procedure. Read-only metadata operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public Object listDbObjects(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Object type: view, function, procedure") String type,
        @McpToolParam(description = "Optional search keyword", required = false) String keyword) {
        return connectionScope.run(datasourceUri, databaseName, schemaName, resolvedDataSource -> switch (type.toLowerCase()) {
            case "view", "views" -> viewService.viewsWithCache(resolvedDataSource.getDataSourceId(), databaseName,
                schemaName, keyword, false);
            case "function", "functions" -> functionService.functionsWithCache(resolvedDataSource.getDataSourceId(),
                databaseName, schemaName, keyword, false);
            case "procedure", "procedures" -> procedureService.proceduresWithCache(
                resolvedDataSource.getDataSourceId(), databaseName, schemaName, keyword, false);
            default -> throw new IllegalArgumentException("Unsupported object type: " + type);
        });
    }

    @McpTool(description = "Search tables by keyword. Currently searches table names via list_tables metadata cache. Read-only operation."
        , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String searchTables(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "Database name") String databaseName,
        @McpToolParam(description = "Schema name, optional", required = false) String schemaName,
        @McpToolParam(description = "Search keyword") String keyword) {
        return connectionScope.run(datasourceUri, databaseName, schemaName,
            resolvedDataSource -> databaseService.searchTableColumns(resolvedDataSource.getDataSourceId(), databaseName,
                schemaName, keyword));
    }
}
