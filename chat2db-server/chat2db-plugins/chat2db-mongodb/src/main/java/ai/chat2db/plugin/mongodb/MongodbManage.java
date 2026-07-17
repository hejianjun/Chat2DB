package ai.chat2db.plugin.mongodb;

import java.sql.Connection;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import org.bson.Document;

public class MongodbManage extends DefaultDBManage implements DBManage {
    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        MongodbConnectionProvider.testConnect(connectInfo);
        return null;
    }

    @Override
    public void connectDatabase(Connection connection, String database) {
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        try (MongodbConnectionProvider.MongodbConnectionContext context =
                     MongodbConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            context.client().getDatabase(databaseName).getCollection(tableName).drop();
        }
    }

    @Override
    public void dropDatabase(Connection connection, String databaseName) {
        try (MongodbConnectionProvider.MongodbConnectionContext context =
                     MongodbConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            context.client().getDatabase(databaseName).drop();
        }
    }

    @Override
    public void truncate(Connection connection, String databaseName, String schemaName, String tableName) {
        try (MongodbConnectionProvider.MongodbConnectionContext context =
                     MongodbConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            context.client().getDatabase(databaseName).getCollection(tableName).deleteMany(new Document());
        }
    }

}
