package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.tools.base.wrapper.ServicePage;
import ai.chat2db.spi.model.Database;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for discovering Chat2DB datasource connections before running database-specific tools.
 */
@Service
public class DataSourceTools {

    private final DataSourceService dataSourceService;
    private final ResultFormatter resultFormatter;

    public DataSourceTools(DataSourceService dataSourceService, ResultFormatter resultFormatter) {
        this.dataSourceService = dataSourceService;
        this.resultFormatter = resultFormatter;
    }

    @Tool(description = "List configured Chat2DB datasources as CSV. Returns id, alias, type, host, port, url and environmentShortName. Call this before database operations to choose dataSourceId.")
    public String listDatasources(
        @ToolParam(description = "Optional keyword to search datasource alias or connection info", required = false) String keyword,
        @ToolParam(description = "Page number, default 1", required = false) Integer pageNo,
        @ToolParam(description = "Page size, default 100", required = false) Integer pageSize) {
        DataSourcePageQueryParam param = new DataSourcePageQueryParam();
        param.setSearchKey(keyword);
        param.setPageNo(pageNo == null ? 1 : pageNo);
        param.setPageSize(pageSize == null ? 100 : pageSize);
        ServicePage<DataSource> page = dataSourceService.queryPage(param, DataSourceSelector.builder().environment(true).build());
        return resultFormatter.formatDatasourcesCsv(page.getData());
    }

    @Tool(description = "Connect a datasource and return databases visible to it. This is read-only metadata discovery.")
    public List<Database> connectDatasource(@ToolParam(description = "Datasource id from list_datasources") Long dataSourceId) {
        return dataSourceService.connect(dataSourceId);
    }
}
