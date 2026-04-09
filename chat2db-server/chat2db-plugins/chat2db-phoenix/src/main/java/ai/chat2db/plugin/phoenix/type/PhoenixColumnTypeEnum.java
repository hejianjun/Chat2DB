package ai.chat2db.plugin.phoenix.type;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Maps;

import ai.chat2db.spi.ColumnBuilder;
import ai.chat2db.spi.enums.EditStatus;
import ai.chat2db.spi.model.ColumnType;
import ai.chat2db.spi.model.TableColumn;

public enum PhoenixColumnTypeEnum implements ColumnBuilder {
    INTEGER("INTEGER", false, false, true, false, false, false, false, true, false, false),
    BIGINT("BIGINT", false, false, true, false, false, false, false, true, false, false),
    VARCHAR("VARCHAR", true, false, true, false, false, true, true, true, false, false),
    CHAR("CHAR", true, false, true, false, false, true, true, true, false, false),
    DATE("DATE", false, false, true, false, false, false, false, true, false, false),
    TIMESTAMP("TIMESTAMP", false, false, true, false, false, false, false, true, false, false),
    FLOAT("FLOAT", false, false, true, false, false, false, false, true, false, false),
    DOUBLE("DOUBLE", false, false, true, false, false, false, false, true, false, false),
    BOOLEAN("BOOLEAN", false, false, true, false, false, false, false, true, false, false),
    BINARY("BINARY", false, false, true, false, false, false, false, true, false, false),
    DECIMAL("DECIMAL", true, false, true, false, false, false, false, true, false, false),
    JSON("JSON", false, false, true, false, false, false, false, true, false, false),
    UUID("UUID", false, false, true, false, false, false, false, true, false, false);

    private static Map<String, PhoenixColumnTypeEnum> COLUMN_TYPE_MAP = Maps.newHashMap();

    static {
        for (PhoenixColumnTypeEnum value : PhoenixColumnTypeEnum.values()) {
            COLUMN_TYPE_MAP.put(value.getColumnType().getTypeName(), value);
        }
    }

    private ColumnType columnType;


    PhoenixColumnTypeEnum(String dataTypeName, boolean supportLength, boolean supportScale, boolean supportNullable, boolean supportAutoIncrement, boolean supportCharset, boolean supportCollation, boolean supportComments, boolean supportDefaultValue, boolean supportExtent, boolean supportValue) {
        this.columnType = new ColumnType(dataTypeName, supportLength, supportScale, supportNullable, supportAutoIncrement, supportCharset, supportCollation, supportComments, supportDefaultValue, supportExtent, supportValue, false);
    }

    public static PhoenixColumnTypeEnum getByType(String dataType) {
        return COLUMN_TYPE_MAP.get(dataType.toUpperCase());
    }

    public static List<ColumnType> getTypes() {
        return Arrays.stream(PhoenixColumnTypeEnum.values()).map(columnTypeEnum ->
                columnTypeEnum.getColumnType()
        ).toList();
    }

    public ColumnType getColumnType() {
        return columnType;
    }

    @Override
    public String buildCreateColumnSql(TableColumn column) {
        PhoenixColumnTypeEnum type = COLUMN_TYPE_MAP.get(column.getColumnType().toUpperCase());
        if (type == null) {
            return "";
        }
        StringBuilder script = new StringBuilder();

        script.append("\"").append(column.getName()).append("\"").append(" ");

        script.append(buildDataType(column, type)).append(" ");


        script.append(buildCollation(column, type)).append(" ");

        script.append(buildNullable(column, type)).append(" ");

        script.append(buildDefaultValue(column, type)).append(" ");

        // 添加是否主键
        if (Boolean.TRUE.equals(column.getPrimaryKey())) {
            script.append("PRIMARY KEY ");
        }
        return script.toString();
    }

    private String buildCollation(TableColumn column, PhoenixColumnTypeEnum type) {
        if (!type.getColumnType().isSupportCollation() || StringUtils.isEmpty(column.getCollationName())) {
            return "";
        }
        return StringUtils.join("\"", column.getCollationName(), "\"");
    }

    @Override
    public String buildModifyColumn(TableColumn column) {

        if (EditStatus.DELETE.name().equals(column.getEditStatus())) {
            return StringUtils.join("DROP COLUMN \"", column.getName() + "\"");
        }
        if (EditStatus.ADD.name().equals(column.getEditStatus())) {
            return StringUtils.join("ADD COLUMN ", buildCreateColumnSql(column));
        }
        if (EditStatus.MODIFY.name().equals(column.getEditStatus())) {
            StringBuilder script = new StringBuilder();
            script.append("ALTER COLUMN \"").append(column.getName()).append("\" TYPE ").append(buildDataType(column, this)).append(",\n");
            if (column.getNullable() != null && 1 == column.getNullable()) {
                script.append("\t").append("ALTER COLUMN \"").append(column.getName()).append("\" DROP NOT NULL ,\n");
            } else {
                script.append("\t").append("ALTER COLUMN \"").append(column.getName()).append("\" SET NOT NULL ,\n");

            }
            String defaultValue = buildDefaultValue(column, this);
            if (StringUtils.isNotBlank(defaultValue)) {
                script.append("ALTER COLUMN \"").append(column.getName()).append("\" SET ").append(defaultValue).append(",\n");
            }
            script = new StringBuilder(script.substring(0, script.length() - 2));
            return script.toString();
        }
        return "";
    }

    public String buildComment(TableColumn column, PhoenixColumnTypeEnum type) {
        if (!this.columnType.isSupportComments() || column.getComment() == null
                || EditStatus.DELETE.name().equals(column.getEditStatus())) {
            return "";
        }
        return StringUtils.join("COMMENT ON COLUMN", " \"", column.getTableName(),
                "\".\"", column.getName(), "\" IS '", column.getComment(), "';");
    }

    private String buildDefaultValue(TableColumn column, PhoenixColumnTypeEnum type) {
        if (!type.getColumnType().isSupportDefaultValue() || StringUtils.isEmpty(column.getDefaultValue())) {
            return "";
        }

        if("EMPTY_STRING".equalsIgnoreCase(column.getDefaultValue().trim())){
            return StringUtils.join("DEFAULT ''");
        }

        if("NULL".equalsIgnoreCase(column.getDefaultValue().trim())){
            return StringUtils.join("DEFAULT NULL");
        }

        if (Arrays.asList(CHAR, VARCHAR).contains(type)) {
            return StringUtils.join("DEFAULT '", column.getDefaultValue(), "'");
        }

        if (Arrays.asList(TIMESTAMP, DATE).contains(type)) {
            if ("CURRENT_TIMESTAMP".equalsIgnoreCase(column.getDefaultValue().trim())) {
                return StringUtils.join("DEFAULT ", column.getDefaultValue());
            }
            return StringUtils.join("DEFAULT '", column.getDefaultValue(), "'");
        }

        return StringUtils.join("DEFAULT ", column.getDefaultValue());
    }

    private String buildNullable(TableColumn column, PhoenixColumnTypeEnum type) {
        if (!type.getColumnType().isSupportNullable()) {
            return "";
        }
        if (column.getNullable() != null && 1 == column.getNullable()) {
            return "NULL";
        } else {
            return "NOT NULL";
        }
    }

    private String buildDataType(TableColumn column, PhoenixColumnTypeEnum type) {
        String columnType = type.columnType.getTypeName();
        if (Arrays.asList(VARCHAR, CHAR).contains(type)) {
            if (column.getColumnSize() == null ) {
                return columnType;
            }
            return StringUtils.join(columnType, "(", column.getColumnSize(), ")");
        }


        if (Arrays.asList(TIMESTAMP).contains(type)) {
            if (column.getColumnSize() == null || column.getColumnSize() == 0) {
                return columnType;
            } else {
                return StringUtils.join(columnType, "(", column.getColumnSize(), ")");
            }
        }

        if (Arrays.asList(DECIMAL,INTEGER).contains(type)) {
            if (column.getColumnSize() == null && column.getDecimalDigits() == null) {
                return columnType;
            }
            if (column.getColumnSize() != null && column.getDecimalDigits() == null) {
                return StringUtils.join(columnType, "(", column.getColumnSize() + ")");
            } else {
                return StringUtils.join(columnType, "(", column.getColumnSize() + "," + column.getDecimalDigits() + ")");
            }
        }
        return columnType;
    }
}
