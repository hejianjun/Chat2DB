package ai.chat2db.server.web.api.controller.task.biz.doc;

import ai.chat2db.server.domain.api.enums.ExportTypeEnum;
import ai.chat2db.server.web.api.controller.rdb.vo.TableVO;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class SqlSchemaDocExportStrategy extends AbstractSchemaDocExportStrategy {

    @Override
    public boolean supports(String exportType) {
        return ExportTypeEnum.SQL.getCode().equals(exportType);
    }

    @Override
    protected void doExport(OutputStream outputStream, SchemaDocExportContext context) throws Exception {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            for (Table table : context.getTables()) {
                clearDatabaseName(table);
                String ddl = sqlBuilder.buildCreateTableSql(table);
                writer.write(ddl);
                writer.write("\n\n");
            }
            writer.flush();
        }
    }

    private void clearDatabaseName(Table table) {
        if (table == null) {
            return;
        }
        table.setDatabaseName(null);
        clearColumnDatabaseName(table.getColumnList());
        clearIndexDatabaseName(table.getIndexList());
        clearForeignKeyDatabaseName(table.getForeignKeyList());
    }

    private void clearColumnDatabaseName(List<TableColumn> columns) {
        if (columns == null) {
            return;
        }
        for (TableColumn column : columns) {
            column.setDatabaseName(null);
        }
    }

    private void clearIndexDatabaseName(List<TableIndex> indexes) {
        if (indexes == null) {
            return;
        }
        for (TableIndex index : indexes) {
            index.setDatabaseName(null);
        }
    }

    private void clearForeignKeyDatabaseName(List<ForeignKey> foreignKeys) {
        if (foreignKeys == null) {
            return;
        }
        for (ForeignKey foreignKey : foreignKeys) {
            foreignKey.setDatabaseName(null);
        }
    }
}
