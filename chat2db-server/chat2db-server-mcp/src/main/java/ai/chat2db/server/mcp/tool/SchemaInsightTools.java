package ai.chat2db.server.mcp.tool;

import ai.chat2db.server.domain.api.param.ErDiagramQueryParam;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.ErDiagramService;
import ai.chat2db.server.domain.api.service.FunctionService;
import ai.chat2db.server.domain.api.service.ProcedureService;
import ai.chat2db.server.domain.api.service.ViewService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.spi.model.ErDiagram;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class SchemaInsightTools {

    private final DatabaseService databaseService;
    private final ErDiagramService erDiagramService;
    private final ViewService viewService;
    private final FunctionService functionService;
    private final ProcedureService procedureService;
    private final ConnectionScope connectionScope;

    public SchemaInsightTools(DatabaseService databaseService, ErDiagramService erDiagramService, ViewService viewService,
        FunctionService functionService, ProcedureService procedureService, ConnectionScope connectionScope) {
        this.databaseService = databaseService;
        this.erDiagramService = erDiagramService;
        this.viewService = viewService;
        this.functionService = functionService;
        this.procedureService = procedureService;
        this.connectionScope = connectionScope;
    }

    @Tool(description = "Return a compact overview of database tables, columns, comments and foreign keys for LLM reasoning. Read-only operation.")
    public String getDatabaseOverview(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName) {
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> databaseService.queryDatabaseTables(dataSourceId, databaseName, schemaName));
    }

    @Tool(description = "Return ER diagram data for a database/schema. Read-only operation.")
    public ErDiagram getErDiagram(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Optional table name filter", required = false) String tableNameFilter,
        @ToolParam(description = "Include virtual foreign keys, default true", required = false) Boolean includeVirtualFk) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            ErDiagramQueryParam param = new ErDiagramQueryParam();
            param.setDataSourceId(dataSourceId);
            param.setDatabaseName(databaseName);
            param.setSchemaName(schemaName);
            param.setTableNameFilter(tableNameFilter);
            param.setIncludeVirtualFk(includeVirtualFk == null ? Boolean.TRUE : includeVirtualFk);
            param.setSyncForeignKeys(Boolean.FALSE);
            param.setOnlyRelatedTables(Boolean.FALSE);
            return erDiagramService.queryErDiagram(param);
        });
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
