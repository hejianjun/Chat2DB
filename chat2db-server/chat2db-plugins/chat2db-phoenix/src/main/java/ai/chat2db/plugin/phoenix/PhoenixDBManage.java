package ai.chat2db.plugin.phoenix;

import java.sql.Connection;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.SQLExecutor;

public class PhoenixDBManage extends DefaultDBManage implements DBManage {


    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "drop table if exists " +schemaName+"." +tableName;
        SQLExecutor.getInstance().execute(connection,sql, resultSet -> null);
    }
}
