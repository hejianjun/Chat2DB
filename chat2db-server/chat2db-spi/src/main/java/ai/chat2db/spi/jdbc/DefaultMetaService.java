package ai.chat2db.spi.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueHandler;
import ai.chat2db.spi.model.ColumnType;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Function;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.model.TableMeta;
import ai.chat2db.spi.model.Trigger;
import ai.chat2db.spi.model.Type;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;

/**
 * @author jipengfei
 * @version : DefaultMetaService.java
 */
public class DefaultMetaService implements MetaData {

    @Override
    public List<Database> databases(Connection connection) {
        return SQLExecutor.getInstance().databases(connection);
    }

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        if (StringUtils.isNotBlank(databaseName) && CollectionUtils.isNotEmpty(schemas)) {
            for (Schema schema : schemas) {
                if (StringUtils.isBlank(schema.getDatabaseName())) {
                    schema.setDatabaseName(databaseName);
                }
            }
        }
        return schemas;
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        List<Table> tables = this.tables(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName);
        if(CollectionUtils.isEmpty(tables)){
            return null;
        }
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        for (Table table : tables) {
            table.setColumnList(columns(connection, databaseName, schemaName, tableName));
            table.setIndexList(indexes(connection, databaseName, schemaName, tableName));
            return sqlBuilder.buildCreateTableSql(table);
        }
        return null;
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().tables(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, new String[]{"TABLE", "SYSTEM TABLE"});
    }

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        return null;
    }

    @Override
    public List<Table> views(Connection connection, String databaseName, String schemaName) {
        return SQLExecutor.getInstance().tables(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, null, new String[]{"VIEW"});
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        List<Function> functions = SQLExecutor.getInstance().functions(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName);
        if (CollectionUtils.isEmpty(functions)) {
            return functions;
        }
        return functions.stream().filter(function -> StringUtils.isNotBlank(function.getName())).collect(Collectors.toList());
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        return null;
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        List<Procedure> procedures = SQLExecutor.getInstance().procedures(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName);

        if (CollectionUtils.isEmpty(procedures)) {
            return procedures;
        }
        return procedures.stream().filter(function -> StringUtils.isNotBlank(function.getName())).collect(Collectors.toList());
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().columns(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, null);
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName,
                                     String columnName) {
        return SQLExecutor.getInstance().columns(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, columnName);
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().indexes(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName);
    }

    @Override
    public Function function(Connection connection, String databaseName, String schemaName, String functionName) {
        return null;
    }

    @Override
    public Trigger trigger(Connection connection, String databaseName, String schemaName, String triggerName) {
        return null;
    }

    @Override
    public Procedure procedure(Connection connection, String databaseName, String schemaName, String procedureName) {
        return null;
    }

    @Override
    public List<Type> types(Connection connection) {
        return SQLExecutor.getInstance().types(connection);
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new DefaultSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(Connection connection, String databaseName, String schemaName) {
        List<Type> types = SQLExecutor.getInstance().types(connection);

        return TableMeta.builder()
                .columnTypes(types.stream()
                        .map(type -> new ColumnType(
                                type.getTypeName(), // 列类型名称
                                true,               // 假设支持长度
                                true,               // 假设支持小数位数
                                type.getNullable() != 0, // 根据 nullable 字段判断是否支持可为空
                                type.getAutoIncrement() != null && type.getAutoIncrement(), // 根据 autoIncrement 字段判断
                                true,               // 假设支持字符集
                                true,               // 假设支持排序规则
                                true,               // 假设支持注释
                                true,               // 假设支持默认值
                                false,              // 假设不支持扩展特性
                                true,               // 假设支持值的特性
                                false               // 假设不支持单位的特性
                        ))
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(StringUtils::isNotBlank).collect(Collectors.joining("."));
    }

    @Override
    public ValueHandler getValueHandler() {
        return new DefaultValueHandler();
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return SQLExecutor.getInstance();
    }

    @Override
    public List<ForeignKey> foreignKeys(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().foreignKeys(connection, databaseName, schemaName, tableName);
    }
}