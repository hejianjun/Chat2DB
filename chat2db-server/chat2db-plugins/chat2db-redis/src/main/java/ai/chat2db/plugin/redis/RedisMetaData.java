package ai.chat2db.plugin.redis;

import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.sql.SQLExecutor;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class RedisMetaData extends DefaultMetaService implements MetaData {

    @Override
    public List<Database> databases(Connection connection) {
        List<Database> databases = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, "config get databases", resultSet -> {
            try {
                if (resultSet.next()) {
                    Object count = resultSet.getObject(2);
                    if (StringUtils.isNotBlank(count.toString())) {
                        for (int i = 0; i < Integer.parseInt(count.toString()); i++) {
                            Database database = Database.builder().name(String.valueOf(i)).build();
                            databases.add(database);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return databases;
        });
    }

    @Override
    @SneakyThrows
    public List<Schema> schemas(Connection connection, String databaseName) {
        if (StringUtils.isNotBlank(databaseName)) {
            // 切换到指定的数据库
            SQLExecutor.getInstance().executeUpdate("select " + databaseName, connection, 1);
        }
        return SQLExecutor.getInstance().execute(connection, "scan 0 COUNT 1000", resultSet -> {
            List<Schema> schemas = new ArrayList<>();
            Set<String> uniqueNames = new HashSet<>();

            try {
                while (resultSet.next()) {
                    ArrayList list = (ArrayList) resultSet.getObject(2);
                    for (Object object : list) {
                        String fullName = object.toString();
                        // 检查字符串是否包含 ':'
                        if (StringUtils.contains(fullName, ":")) {
                            // 使用 StringUtils 处理字符串
                            String schemaName = getSchemaName(fullName);
                            // 检查是否已经存在
                            if (uniqueNames.add(schemaName)) {
                                Schema schema = new Schema();
                                schema.setName(schemaName);
                                schema.setDatabaseName(databaseName == null ? "0" : databaseName);
                                schema.setTreeNodeType("tables");
                                schema.setKeyType(getType(connection, fullName));
                                schemas.add(schema);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return schemas;
        });
    }

    @Override
    @SneakyThrows
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        if (StringUtils.isNotBlank(databaseName)) {
            // 切换到指定的数据库
            SQLExecutor.getInstance().executeUpdate("select " + databaseName, connection, 1);
        }
        String dbName = databaseName == null ? "0" : databaseName;
        if (StringUtils.isNotBlank(tableName)) {
            return Lists.newArrayList(Table.builder()
                    .name(tableName)
                    .databaseName(dbName)
                    .schemaName(getSchemaName(tableName))
                    .type(getType(connection, tableName))
                    .build());
        }
        return SQLExecutor.getInstance().execute(connection, "scan 0 MATCH " + schemaName + "* COUNT 1000", resultSet -> {
            List<Table> tables = new ArrayList<>();
            try {
                while (resultSet.next()) {
                    ArrayList list = (ArrayList) resultSet.getObject(2);
                    for (Object object : list) {
                        String name = object.toString();
                        tables.add(Table.builder()
                                .name(name)
                                .databaseName(dbName)
                                .schemaName(getSchemaName(name))
                                .build());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return tables;
        });
    }

    private String getType(Connection connection, String tableName) {
        try {
            return SQLExecutor.getInstance().execute(connection, "type " + tableName, resultSet -> {
                try {
                    if (resultSet.next()) {
                        return resultSet.getString(1);
                    }
                } catch (Exception e) {
                    log.error("type获取失败", e);
                }
                return "string";
            });
        } catch (Exception e) {
            log.error("type获取失败", e);
        }
        return "string";
    }


    private String getSchemaName(String fullName) {
        // 使用 StringUtils 处理字符串
        return StringUtils.substringBeforeLast(fullName, ":");
    }
}
