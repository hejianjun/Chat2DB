package ai.chat2db.plugin.dlc;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import ai.chat2db.plugin.dlc.builder.DlcSqlBuilder;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;

public class DlcMetaData extends DefaultMetaService implements MetaData {

    @Override
    public List<Database> databases(Connection connection) {
        try {
            return SQLExecutor.getInstance().execute(connection, "SHOW DATABASES", resultSet -> {
                List<Database> databases = new ArrayList<>();
                while (resultSet.next()) {
                    Database database = new Database();
                    String name = resultSet.getString(1);
                    database.setName(name);
                    databases.add(database);
                }
                return databases;
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        try {
            String sql = "SHOW TABLES";
            if (StringUtils.isNotBlank(tableName)) {
                sql += " LIKE '" + tableName + "'";
            }

            return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                List<Table> tables = new ArrayList<>();
                while (resultSet.next()) {
                    String namespace = resultSet.getString("namespace");
                    String name = resultSet.getString("tableName");
                    Table table = Table.builder()
                            .name(name)
                            .databaseName(StringUtils.isNotBlank(namespace) ? namespace : databaseName)
                            .schemaName(schemaName)
                            .type("TABLE")
                            .build();
                    tables.add(table);
                }
                return tables;
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        try {
            String sql = "SHOW CREATE TABLE " + tableName;
            return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                if (resultSet.next()) {
                    return resultSet.getString(2);
                }
                return null;
            });
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        try {
            String sql = "DESCRIBE TABLE " + tableName;
            return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                List<TableColumn> columns = new ArrayList<>();
                int ordinalPosition = 1;
                while (resultSet.next()) {
                    String colName = resultSet.getString("col_name");
                    if (StringUtils.isBlank(colName) || "# col_name".equals(colName)
                            || "---".equals(colName) || "data_type".equals(colName)) {
                        continue;
                    }
                    String dataType = resultSet.getString("data_type");
                    String comment = resultSet.getString("comment");

                    TableColumn column = new TableColumn();
                    column.setName(colName);
                    column.setOldName(colName);
                    column.setDataType(dataType);
                    column.setColumnType(dataType);
                    column.setDatabaseName(databaseName);
                    column.setSchemaName(schemaName);
                    column.setTableName(tableName);
                    column.setOrdinalPosition(ordinalPosition++);
                    if (StringUtils.isNotBlank(comment) && !"NULL".equalsIgnoreCase(comment)) {
                        column.setComment(comment);
                    }
                    columns.add(column);
                }
                return columns;
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Table> views(Connection connection, String databaseName, String schemaName) {
        try {
            return SQLExecutor.getInstance().execute(connection, "SHOW VIEWS", resultSet -> {
                List<Table> views = new ArrayList<>();
                while (resultSet.next()) {
                    Table view = Table.builder()
                            .name(resultSet.getString(1))
                            .databaseName(databaseName)
                            .schemaName(schemaName)
                            .type("VIEW")
                            .build();
                    views.add(view);
                }
                return views;
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        try {
            String sql = "SHOW CREATE VIEW " + viewName;
            return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                Table table = new Table();
                table.setName(viewName);
                table.setDatabaseName(databaseName);
                table.setSchemaName(schemaName);
                table.setType("VIEW");
                if (resultSet.next()) {
                    table.setDdl(resultSet.getString(2));
                }
                return table;
            });
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        try {
            return SQLExecutor.getInstance().execute(connection, "SHOW FUNCTIONS", resultSet -> {
                List<Function> functions = new ArrayList<>();
                while (resultSet.next()) {
                    String name = resultSet.getString(1);
                    if (StringUtils.isNotBlank(name)) {
                        Function function = new Function();
                        function.setName(name);
                        function.setDatabaseName(databaseName);
                        function.setSchemaName(schemaName);
                        functions.add(function);
                    }
                }
                return functions;
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Function function(Connection connection, String databaseName, String schemaName, String functionName) {
        return null;
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public List<ForeignKey> foreignKeys(Connection connection, String databaseName, String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new DlcSqlBuilder();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names)
                .filter(StringUtils::isNotBlank)
                .map(name -> "`" + name + "`")
                .collect(Collectors.joining("."));
    }
}
