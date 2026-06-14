package ai.chat2db.server.mcp.tool;

import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.FunctionService;
import ai.chat2db.server.domain.api.service.ProcedureService;
import ai.chat2db.server.domain.api.service.ViewService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

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

    @Tool(description = "List database objects of type view, function or procedure. Read-only metadata operation.")
    public Object listDbObjects(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Object type: view, function, procedure") String type,
        @ToolParam(description = "Optional search keyword", required = false) String keyword) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> switch (type.toLowerCase()) {
            case "view", "views" -> viewService.viewsWithCache(dataSourceId, databaseName, schemaName, keyword, false);
            case "function", "functions" -> functionService.functionsWithCache(dataSourceId, databaseName, schemaName,
                keyword, false);
            case "procedure", "procedures" -> procedureService.proceduresWithCache(dataSourceId, databaseName,
                schemaName, keyword, false);
            default -> throw new IllegalArgumentException("Unsupported object type: " + type);
        });
    }

    @Tool(description = "Search tables by keyword. Currently searches table names via list_tables metadata cache. Read-only operation.")
    public Object searchTables(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Search keyword") String keyword) {
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> databaseService.searchTableColumns(dataSourceId, databaseName, schemaName, keyword));
    }
}
