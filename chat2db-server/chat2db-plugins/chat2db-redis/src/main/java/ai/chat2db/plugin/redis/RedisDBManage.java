package ai.chat2db.plugin.redis;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.ConnectInfo;

import java.sql.Connection;

public class RedisDBManage extends DefaultDBManage implements DBManage {

    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        RedisConnectionProvider.testConnect(connectInfo);
        return null;
    }

}
