package ai.chat2db.server.mcp.tool;

import java.util.List;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.tools.base.wrapper.ServicePage;
import ai.chat2db.spi.model.Database;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class DataSourceTools {

    private final DataSourceService dataSourceService;

    public DataSourceTools(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @Tool(description = "List configured Chat2DB datasources. Returns id, alias, type, host and port. Call this before database operations to choose dataSourceId.")
    public List<DataSource> listDatasources(
        @ToolParam(description = "Optional keyword to search datasource alias or connection info", required = false) String keyword,
        @ToolParam(description = "Page number, default 1", required = false) Integer pageNo,
        @ToolParam(description = "Page size, default 100", required = false) Integer pageSize) {
        DataSourcePageQueryParam param = new DataSourcePageQueryParam();
        param.setSearchKey(keyword);
        param.setPageNo(pageNo == null ? 1 : pageNo);
        param.setPageSize(pageSize == null ? 100 : pageSize);
        ServicePage<DataSource> page = dataSourceService.queryPage(param, DataSourceSelector.builder().environment(true).build());
        return page.getData();
    }

    @Tool(description = "Connect a datasource and return databases visible to it. This is read-only metadata discovery.")
    public List<Database> connectDatasource(@ToolParam(description = "Datasource id from list_datasources") Long dataSourceId) {
        return dataSourceService.connect(dataSourceId);
    }
}
