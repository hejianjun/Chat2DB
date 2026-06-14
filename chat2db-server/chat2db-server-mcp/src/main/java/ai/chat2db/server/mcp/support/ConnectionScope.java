package ai.chat2db.server.mcp.support;

import java.util.function.Supplier;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ConnectionScope {

    private final DataSourceService dataSourceService;

    public ConnectionScope(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    public <T> T run(Long dataSourceId, String databaseName, String schemaName, Supplier<T> supplier) {
        Chat2DBContext.putContext(toInfo(dataSourceId, databaseName, schemaName));
        try {
            return supplier.get();
        } finally {
            ContextUtils.removeContext();
            Chat2DBContext.removeContext();
        }
    }

    private ConnectInfo toInfo(Long dataSourceId, String databaseName, String schemaName) {
        DataSource dataSource = dataSourceService.queryById(dataSourceId);
        if (dataSource == null) {
            throw new ParamBusinessException("dataSourceId");
        }
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setAlias(dataSource.getAlias());
        connectInfo.setUser(dataSource.getUserName());
        connectInfo.setDataSourceId(dataSourceId);
        connectInfo.setUserId(ContextUtils.getUserId());
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
}
