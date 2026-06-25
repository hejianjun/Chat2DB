package ai.chat2db.server.mcp.support;

import java.util.function.Function;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Creates and clears the Chat2DB connection context required by existing database services.
 */
@Component
public class ConnectionScope {

    private final DataSourceResourceService dataSourceResourceService;

    public ConnectionScope(DataSourceResourceService dataSourceResourceService) {
        this.dataSourceResourceService = dataSourceResourceService;
    }

    public <T> T run(String datasourceUri, String databaseName, String schemaName,
        Function<ResolvedDataSource, T> supplier) {
        ResolvedDataSource resolvedDataSource = dataSourceResourceService.resolve(datasourceUri);
        Chat2DBContext.putContext(toInfo(resolvedDataSource, databaseName, schemaName));
        try {
            return supplier.apply(resolvedDataSource);
        } finally {
            // MCP calls share server threads, so both context holders must be cleared after each tool call.
            ContextUtils.removeContext();
            Chat2DBContext.removeContext();
        }
    }

    /**
     * Converts a persisted datasource into the SPI connection object expected by database plugins.
     */
    public ConnectInfo toInfo(ResolvedDataSource resolvedDataSource, String databaseName, String schemaName) {
        DataSource dataSource = resolvedDataSource.getDataSource();
        if (dataSource == null) {
            throw new ParamBusinessException("datasourceUri");
        }
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setAlias(dataSource.getAlias());
        connectInfo.setUser(dataSource.getUserName());
        connectInfo.setDataSourceId(resolvedDataSource.getDataSourceId());
        connectInfo.setUserId(currentUserId());
        connectInfo.setPassword(dataSource.getPassword());
        connectInfo.setDbType(dataSource.getType());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setDatabase(databaseName);
        connectInfo.setSchemaName(schemaName);
        connectInfo.setConsoleOwn(false);
        connectInfo.setDriver(dataSource.getDriver());
        connectInfo.setSsh(dataSource.getSsh());
        connectInfo.setSsl(dataSource.getSsl());
        connectInfo.setJdbc(dataSource.getJdbc());
        connectInfo.setExtendInfo(dataSource.getExtendInfo());
        connectInfo.setPort(StringUtils.isNotBlank(dataSource.getPort()) ? Integer.parseInt(dataSource.getPort()) : null);
        connectInfo.setHost(dataSource.getHost());
        DriverConfig driverConfig = dataSource.getDriverConfig();
        if (driverConfig != null && driverConfig.notEmpty()) {
            connectInfo.setDriverConfig(driverConfig);
        }
        return connectInfo;
    }

    private Long currentUserId() {
        LoginUser loginUser = ContextUtils.queryLoginUser();
        return loginUser == null ? null : loginUser.getId();
    }
}
