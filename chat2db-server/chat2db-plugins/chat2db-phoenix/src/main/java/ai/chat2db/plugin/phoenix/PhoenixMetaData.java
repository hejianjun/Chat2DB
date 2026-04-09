package ai.chat2db.plugin.phoenix;

import java.sql.Connection;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import ai.chat2db.plugin.phoenix.builder.PhoenixSqlBuilder;
import ai.chat2db.plugin.phoenix.type.PhoenixColumnTypeEnum;
import ai.chat2db.plugin.phoenix.type.PhoenixDefaultValueEnum;
import ai.chat2db.plugin.phoenix.type.PhoenixIndexTypeEnum;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableMeta;

public class PhoenixMetaData extends DefaultMetaService implements MetaData {
    @Override
    public SqlBuilder getSqlBuilder() {
        return new PhoenixSqlBuilder();
    }

        @Override
    public TableMeta getTableMeta(Connection connection, String databaseName, String schemaName) {
        return TableMeta.builder()
                .columnTypes(PhoenixColumnTypeEnum.getTypes())
                .indexTypes(PhoenixIndexTypeEnum.getIndexTypes())
                .defaultValues(PhoenixDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        List<TableColumn> columns = super.columns(connection, databaseName, schemaName, tableName);
        if(CollectionUtils.isEmpty(columns)){
            return columns;
        }
        columns.forEach(column-> column.setPrimaryKey(column.getPrimaryKeyOrder()!=0));
        return columns;
    }
}
