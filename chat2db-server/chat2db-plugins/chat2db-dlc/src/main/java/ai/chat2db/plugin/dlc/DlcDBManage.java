package ai.chat2db.plugin.dlc;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.IDriverManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * DLC数据库管理实现
 * 
 * DLC JDBC驱动不支持动态切换数据库（setSchema抛出SQLFeatureNotSupportedException），
 * 因此数据库切换需要通过重新创建连接来实现。
 * 
 * @author chat2db
 */
@Slf4j
public class DlcDBManage extends DefaultDBManage implements DBManage {

    private static final String DATABASE_NAME_KEY = "database_name";

    @Override
    @SneakyThrows
    public Connection getConnection(ConnectInfo connectInfo) {
        Connection connection = connectInfo.getConnection();
        if (connection != null) {
            return connection;
        }

        String url = connectInfo.getUrl();
        Map<String, Object> properties = connectInfo.getExtendMap();
        
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        
        String databaseName = connectInfo.getDatabaseName();
        if (StringUtils.isNotBlank(databaseName)) {
            url = replaceQueryParam(url, DATABASE_NAME_KEY, databaseName);
            properties.put(DATABASE_NAME_KEY, databaseName);
        }

        connection = IDriverManager.getConnection(url, connectInfo.getUser(), connectInfo.getPassword(),
                connectInfo.getDriverConfig(), properties);
        
        connectInfo.setConnection(connection);
        return connection;
    }

    @Override
    public void connectDatabase(Connection connection, String database) {
        if (StringUtils.isBlank(database)) {
            return;
        }
        
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
        if (connectInfo == null) {
            return;
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            log.warn("close connection error", e);
        }

        connectInfo.setDatabaseName(database);
        connectInfo.setConnection(null);
        
        Connection newConnection = getConnection(connectInfo);
        connectInfo.setConnection(newConnection);
    }

    private String replaceQueryParam(String url, String key, String value) {
        String param = key + "=";
        int paramIndex = url.indexOf(param);
        if (paramIndex < 0) {
            return url + (url.contains("?") ? "&" : "?") + param + value;
        }

        int valueStart = paramIndex + param.length();
        int valueEnd = url.indexOf("&", valueStart);
        if (valueEnd < 0) {
            return url.substring(0, valueStart) + value;
        }
        return url.substring(0, valueStart) + value + url.substring(valueEnd);
    }
}
