package ai.chat2db.server.mcp.tool;

import ai.chat2db.server.domain.api.param.DlCountParam;
import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.config.McpProperties;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class QueryTools {

    private final DlTemplateService dlTemplateService;
    private final ConnectionScope connectionScope;
    private final ResultFormatter resultFormatter;
    private final SqlGuard sqlGuard;
    private final McpProperties properties;

    public QueryTools(DlTemplateService dlTemplateService, ConnectionScope connectionScope,
        ResultFormatter resultFormatter, SqlGuard sqlGuard, McpProperties properties) {
        this.dlTemplateService = dlTemplateService;
        this.connectionScope = connectionScope;
        this.resultFormatter = resultFormatter;
        this.sqlGuard = sqlGuard;
        this.properties = properties;
    }

    @Tool(description = "Execute SQL. Defaults to read-only mode; when chat2db.mcp.sql.read-only=true only SELECT/SHOW/DESC/DESCRIBE/EXPLAIN/WITH are allowed.")
    public String executeSql(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "SQL to execute") String sql,
        @ToolParam(description = "Page number, default 1", required = false) Integer pageNo,
        @ToolParam(description = "Page size, default 100, capped by server config", required = false) Integer pageSize) {
        if (properties.getSql().isReadOnly()) {
            sqlGuard.requireReadOnlySql(sql);
        }
        return connectionScope.run(dataSourceId, databaseName, schemaName,
            () -> resultFormatter.format(dlTemplateService.execute(executeParam(dataSourceId, databaseName, schemaName, sql, pageNo, pageSize))));
    }

    @Tool(description = "Execute a read-only SQL query. Only SELECT/SHOW/DESC/DESCRIBE/EXPLAIN/WITH are allowed. Use this for normal data exploration.")
    public String executeSelect(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Read-only SQL") String sql,
        @ToolParam(description = "Page number, default 1", required = false) Integer pageNo,
        @ToolParam(description = "Page size, default 100, capped by server config", required = false) Integer pageSize) {
        sqlGuard.requireReadOnlySql(sql);
        return executeSql(dataSourceId, databaseName, schemaName, sql, pageNo, pageSize);
    }

    @Tool(description = "Count rows for a table by running SELECT COUNT(1). Read-only operation.")
    public Long countTableRows(@ToolParam(description = "Datasource id") Long dataSourceId,
        @ToolParam(description = "Database name") String databaseName,
        @ToolParam(description = "Schema name, optional", required = false) String schemaName,
        @ToolParam(description = "Table name") String tableName) {
        String qualifiedTable = schemaName == null || schemaName.isBlank() ? tableName : schemaName + "." + tableName;
        String sql = "select count(1) from " + qualifiedTable;
        return connectionScope.run(dataSourceId, databaseName, schemaName, () -> {
            DlCountParam param = new DlCountParam();
            param.setDataSourceId(dataSourceId);
            param.setDatabaseName(databaseName);
            param.setConsoleId(0L);
            param.setSql(sql);
            return dlTemplateService.count(param);
        });
    }

    DlExecuteParam executeParam(Long dataSourceId, String databaseName, String schemaName, String sql, Integer pageNo,
        Integer pageSize) {
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
