package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.mcp.support.DataSourceRefService;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.tools.base.wrapper.ServicePage;
import ai.chat2db.spi.model.Database;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for discovering Chat2DB datasource connections before running database-specific tools.
 */
@Service
public class DataSourceTools {

    private final DataSourceService dataSourceService;
    private final DataSourceRefService dataSourceRefService;
    private final ResultFormatter resultFormatter;

    public DataSourceTools(DataSourceService dataSourceService, DataSourceRefService dataSourceRefService,
        ResultFormatter resultFormatter) {
        this.dataSourceService = dataSourceService;
        this.dataSourceRefService = dataSourceRefService;
        this.resultFormatter = resultFormatter;
    }

    @McpTool(description = "List configured Chat2DB datasources as CSV. Returns datasourceRef plus readable datasource details. Call this before database operations and pass datasourceRef, not raw id, to other tools."
            , annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public String listDatasources(
            @McpToolParam(description = "Optional keyword to search datasource alias or connection info", required = false) String keyword,
            @McpToolParam(description = "Page number, default 1", required = false) Integer pageNo,
            @McpToolParam(description = "Page size, default 100", required = false) Integer pageSize) {
        DataSourcePageQueryParam param = new DataSourcePageQueryParam();
        param.setSearchKey(keyword);
        param.setPageNo(pageNo == null ? 1 : pageNo);
        param.setPageSize(pageSize == null ? 100 : pageSize);
        ServicePage<DataSource> page = dataSourceService.queryPage(param, DataSourceSelector.builder().environment(true).build());
        return resultFormatter.formatDatasourcesCsv(page.getData(), dataSourceRefService::issue);
    }

    @McpTool(description = "Connect a datasource and return databases visible to it. This is read-only metadata discovery."
            , annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public List<Database> connectDatasource(@McpToolParam(description = "Datasource ref from listDatasources") String datasourceRef) {
        return dataSourceService.connect(dataSourceRefService.resolve(datasourceRef).getDataSourceId());
    }
}
