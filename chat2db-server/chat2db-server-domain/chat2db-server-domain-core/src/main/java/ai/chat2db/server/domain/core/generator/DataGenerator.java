package ai.chat2db.server.domain.core.generator;

import lombok.Data;
import net.datafaker.Faker;

/**
 * 数据生成器接口
 */
public interface DataGenerator {

    /**
     * 生成数据
     *
     * @param faker        Faker实例
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
    @Data
    class ColumnConfig {
        private String columnName;
        private String dataType;
        private String generationType;
        private String comment;
        private Boolean nullable;
        private Integer maxLength;
        private Integer scale;
    }
}
