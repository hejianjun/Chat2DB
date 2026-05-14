package ai.chat2db.server.domain.core.generator;

import net.datafaker.Faker;

/**
 * 数据生成器接口
 */
public interface DataGenerator {

    /**
     * 生成数据
     *
     * @param faker Faker实例
     * @param columnConfig 列配置
     * @return 生成的数据
     */
    Object generate(Faker faker, ColumnConfig columnConfig);

    /**
     * 是否支持指定的数据类型
     *
     * @param dataType 数据类型
     * @return 是否支持
     */
    boolean supports(String dataType);

    /**
     * 获取生成器类型名称
     *
     * @return 类型名称
     */
    String getGeneratorType();

    /**
     * 列配置内部类
     */
    class ColumnConfig {
        private String columnName;
        private String dataType;
        private String generationType;
        private String comment;
        private Boolean nullable;
        private Integer maxLength;
        private Integer scale;

        // Getters and Setters
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public String getGenerationType() { return generationType; }
        public void setGenerationType(String generationType) { this.generationType = generationType; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public Boolean getNullable() { return nullable; }
        public void setNullable(Boolean nullable) { this.nullable = nullable; }
        public Integer getMaxLength() { return maxLength; }
        public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
        public Integer getScale() { return scale; }
        public void setScale(Integer scale) { this.scale = scale; }
    }
}
