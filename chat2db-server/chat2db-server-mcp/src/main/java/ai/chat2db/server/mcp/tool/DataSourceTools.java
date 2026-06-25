package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.mcp.support.DataSourceResourceService;
import ai.chat2db.spi.model.Database;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for connecting Chat2DB datasource resources before running database-specific tools.
 */
@Service
public class DataSourceTools {

    private final DataSourceService dataSourceService;
    private final DataSourceResourceService dataSourceResourceService;

    public DataSourceTools(DataSourceService dataSourceService, DataSourceResourceService dataSourceResourceService) {
        this.dataSourceService = dataSourceService;
        this.dataSourceResourceService = dataSourceResourceService;
    }

    @McpTool(description = "Connect a datasource and return databases visible to it. This is read-only metadata discovery."
            , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public List<Database> connectDatasource(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri) {
        return dataSourceService.connect(dataSourceResourceService.resolve(datasourceUri).getDataSourceId());
    }
}
