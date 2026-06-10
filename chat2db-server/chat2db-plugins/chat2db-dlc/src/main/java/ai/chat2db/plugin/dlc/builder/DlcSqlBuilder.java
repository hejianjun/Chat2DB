package ai.chat2db.plugin.dlc.builder;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.model.TableIndexColumn;

public class DlcSqlBuilder extends DefaultSqlBuilder {

    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();
        if (StringUtils.isNotBlank(table.getAiComment())) {
            script.append(" -- ").append(table.getAiComment()).append("\n");
        }
        script.append("CREATE TABLE ");

        if (StringUtils.isNotBlank(table.getDatabaseName())) {
            script.append("`").append(table.getDatabaseName()).append("`").append(".");
        }
        script.append("`").append(table.getName()).append("`").append(" (\n");

        appendColumns(script, table.getColumnList());
        appendIndexes(script, table.getIndexList());

        if (script.length() > 2) {
            script.setLength(script.length() - 2);
        }
        script.append("\n)");

        appendTableComment(script, table);
        appendPartition(script, table);

        script.append(";");
        return script.toString();
    }

    protected void appendTableComment(StringBuilder script, Table table) {
        if (table.getComment() != null) {
            script.append(" COMMENT '").append(table.getComment()).append("'");
        }
    }

    protected void appendPartition(StringBuilder script, Table table) {
        if (table.getPartition() != null) {
            script.append(" PARTITIONED BY (").append(table.getPartition()).append(")");
        }
    }

    @Override
    protected void appendColumns(StringBuilder script, List<TableColumn> columnList) {
        for (TableColumn column : columnList) {
            script.append("    `").append(column.getName()).append("` ")
                    .append(column.getDataType());

            if (column.getColumnSize() != null && column.getColumnSize() > 0) {
                script.append("(").append(column.getColumnSize());
                if (column.getDecimalDigits() != null && column.getDecimalDigits() > 0) {
                    script.append(", ").append(column.getDecimalDigits());
                }
                script.append(")");
            }

            if (Boolean.TRUE.equals(column.getAutoIncrement())) {
                script.append(" AUTO_INCREMENT");
            }

            if (column.getDefaultValue() != null) {
                script.append(" DEFAULT '").append(column.getDefaultValue()).append("'");
            }

            if (Boolean.TRUE.equals(column.getPrimaryKey())) {
                script.append(" PRIMARY KEY");
            }

            if (StringUtils.isNotBlank(column.getComment())) {
                script.append(" COMMENT '").append(column.getComment()).append("'");
            }

            if (StringUtils.isNotBlank(column.getAiComment())) {
                script.append(" -- ").append(column.getAiComment());
            }
            script.append(",\n");
        }
    }

    @Override
    protected void appendIndexes(StringBuilder script, List<TableIndex> indexList) {
        if (indexList == null) {
            return;
        }
        for (TableIndex index : indexList) {
            if (Boolean.TRUE.equals(index.getUnique())) {
                script.append("    UNIQUE INDEX `").append(index.getName()).append("` (");
            } else {
                script.append("    INDEX `").append(index.getName()).append("` (");
            }

            List<TableIndexColumn> columnList = index.getColumnList();
            if (columnList != null) {
                for (TableIndexColumn column : columnList) {
                    script.append("`").append(column.getColumnName()).append("`, ");
                }
                if (!columnList.isEmpty()) {
                    script.setLength(script.length() - 2);
                }
            }

            script.append(")");

            if (StringUtils.isNotBlank(index.getComment())) {
                script.append(" COMMENT '").append(index.getComment()).append("'");
            }
            script.append(",\n");
        }
    }
}
