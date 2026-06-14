package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.param.SchemaQueryParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.SimpleTable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class MetadataTools {

    private final DatabaseService databaseService;
    private final TableService tableService;
    private final ConnectionScope connectionScope;

    public MetadataTools(DatabaseService databaseService, TableService tableService, ConnectionScope connectionScope) {
        this.databaseService = databaseService;
        this.tableService = tableService;
        this.connectionScope = connectionScope;
    }

    @Tool(description = "List databases for a datasource. Read-only metadata operation.")
    public List<Database> listDatabases(@ToolParam(description = "Datasource id") Long dataSourceId) {
        return connectionScope.run(dataSourceId, null, null, () -> {
            DatabaseQueryAllParam param = new DatabaseQueryAllParam();
            param.setDataSourceId(dataSourceId);
            return databaseService.queryAll(param);
        });
    }

    @Tool(description = "List schemas under a database. Read-only metadata operation.")
    public List<Schema> listSchemas(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName) {
        return connectionScope.run(dataSourceId, databaseName, null, () -> {
            SchemaQueryParam param = new SchemaQueryParam();
            param.setDataSourceId(dataSourceId);
            param.setDataBaseName(databaseName);
            return databaseService.querySchema(param);
        });
    }

    @Tool(description = "List tables in a database/schema. Supports optional keyword search. Read-only metadata operation.")
    public List<SimpleTable> listTables(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Optional table search keyword", required = false) String keyword) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            TablePageQueryParam param = new TablePageQueryParam();
            param.setDataSourceId(dataSourceId);
            param.setDatabaseName(databaseName);
            param.setSchemaName(schemaName);
            param.setSearchKey(keyword);
            param.setPageNo(1);
            param.setPageSize(1000);
            return tableService.queryTables(param);
        });
    }
}
