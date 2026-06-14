package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.param.CreateVirtualFKParam;
import ai.chat2db.server.domain.api.param.UpdateVirtualFKParam;
import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.VirtualForeignKey;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class ForeignKeyTools {

    private final ForeignKeySyncService foreignKeySyncService;
    private final ConnectionScope connectionScope;

    public ForeignKeyTools(ForeignKeySyncService foreignKeySyncService, ConnectionScope connectionScope) {
        this.foreignKeySyncService = foreignKeySyncService;
        this.connectionScope = connectionScope;
    }

    @Tool(description = "List real and virtual foreign keys for a table or its relationships. Read-only metadata operation. relation can be SOURCE, REFERENCED or ANY; default SOURCE.")
    public List<ForeignKey> listForeignKeys(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name, optional. Omit to list all foreign keys in the database/schema", required = false) String tableName,
        @ToolParam(description = "Relation filter: SOURCE, REFERENCED, ANY. Default SOURCE", required = false) String relation) {
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> foreignKeySyncService.listForeignKeys(dataSourceId, databaseName, schemaName, tableName,
                parseRelation(relation)));
    }

    @Tool(description = "List real database foreign keys only. Read-only metadata operation.")
    public List<ForeignKey> listRealForeignKeys(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name, optional. Omit to list all real foreign keys in the database/schema", required = false) String tableName) {
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> foreignKeySyncService.queryRealForeignKeys(dataSourceId, databaseName, schemaName, tableName));
    }

    @Tool(description = "List virtual foreign keys only. Read-only metadata operation.")
    public List<VirtualForeignKey> listVirtualForeignKeys(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name, optional. Omit to list all virtual foreign keys in the database/schema", required = false) String tableName) {
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> foreignKeySyncService.queryVirtualForeignKeys(dataSourceId, databaseName, schemaName, tableName));
    }

    @Tool(description = "Create a virtual foreign key. Metadata-only operation; it does not alter the physical database schema.")
    public VirtualForeignKey createVirtualForeignKey(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Source table name") String tableName,
        @ToolParam(description = "Source column name") String columnName,
        @ToolParam(description = "Referenced table name") String referencedTable,
        @ToolParam(description = "Referenced column name") String referencedColumnName,
        @ToolParam(description = "Comment, optional", required = false) String comment) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> foreignKeySyncService.createVirtualFK(
            CreateVirtualFKParam.builder()
                .dataSourceId(dataSourceId)
                .databaseName(databaseName)
                .schemaName(schemaName)
                .tableName(tableName)
                .columnName(columnName)
                .referencedTable(referencedTable)
                .referencedColumnName(referencedColumnName)
                .comment(comment)
                .build()));
    }

    @Tool(description = "Update a virtual foreign key by id. Metadata-only operation; it does not alter the physical database schema.")
    public VirtualForeignKey updateVirtualForeignKey(@ToolParam(description = "Virtual foreign key id") Long id,
        @ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Source table name, optional", required = false) String tableName,
        @ToolParam(description = "Source column name, optional", required = false) String columnName,
        @ToolParam(description = "Referenced table name, optional", required = false) String referencedTable,
        @ToolParam(description = "Referenced column name, optional", required = false) String referencedColumnName,
        @ToolParam(description = "Virtual foreign key name, optional", required = false) String name,
        @ToolParam(description = "Comment, optional", required = false) String comment) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> foreignKeySyncService.updateVirtualFK(
            UpdateVirtualFKParam.builder()
                .id(id)
                .dataSourceId(dataSourceId)
                .databaseName(databaseName)
                .schemaName(schemaName)
                .tableName(tableName)
                .columnName(columnName)
                .referencedTable(referencedTable)
                .referencedColumnName(referencedColumnName)
                .vkName(name)
                .comment(comment)
                .build()));
    }

    @Tool(description = "Delete a virtual foreign key by id. Metadata-only operation; it does not alter the physical database schema.")
    public String deleteVirtualForeignKey(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Virtual foreign key id") Long id) {
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            foreignKeySyncService.deleteVirtualFK(id);
            return "Deleted virtual foreign key: " + id;
        });
    }

    private ForeignKeySyncService.ForeignKeyRelation parseRelation(String relation) {
        if (relation == null || relation.isBlank()) {
            return ForeignKeySyncService.ForeignKeyRelation.SOURCE;
        }
        return ForeignKeySyncService.ForeignKeyRelation.valueOf(relation.trim().toUpperCase());
    }
}
