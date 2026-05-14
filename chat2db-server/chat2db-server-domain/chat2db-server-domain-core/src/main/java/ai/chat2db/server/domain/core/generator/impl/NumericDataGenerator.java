package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数值数据生成器
 */
@Component
public class NumericDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String columnName = columnConfig.getColumnName().toLowerCase();
        String dataType = columnConfig.getDataType().toLowerCase();
        
        if (dataType.contains("int") || dataType.contains("integer")) {
            if (columnName.contains("age") || columnName.contains("年龄")) {
                return faker.number().numberBetween(18, 80);
            } else if (columnName.contains("score") || columnName.contains("分数") || columnName.contains("评分")) {
                return faker.number().numberBetween(0, 100);
            } else if (columnName.contains("count") || columnName.contains("数量") || columnName.contains("计数")) {
                return faker.number().numberBetween(1, 1000);
            } else {
                return faker.number().numberBetween(1, Integer.MAX_VALUE / 1000);
            }
        } else if (dataType.contains("decimal") || dataType.contains("numeric") || dataType.contains("money")) {
            if (columnName.contains("price") || columnName.contains("价格") || columnName.contains("金额")) {
                return new BigDecimal(faker.number().randomDouble(2, 10, 10000))
                    .setScale(2, RoundingMode.HALF_UP);
            } else if (columnName.contains("salary") || columnName.contains("薪资") || columnName.contains("工资")) {
                return new BigDecimal(faker.number().randomDouble(2, 3000, 50000))
                    .setScale(2, RoundingMode.HALF_UP);
            } else {
                int scale = columnConfig.getScale() != null ? columnConfig.getScale() : 2;
                return new BigDecimal(faker.number().randomDouble(scale, 0, 1000000))
                    .setScale(scale, RoundingMode.HALF_UP);
            }
        } else if (dataType.contains("float") || dataType.contains("double")) {
            if (columnName.contains("rate") || columnName.contains("比率") || columnName.contains("百分比")) {
                return faker.number().randomDouble(4, 0, 1);
            } else {
                return faker.number().randomDouble(6, 0, 1000000);
            }
        } else {
            // 默认生成整数
            return faker.number().numberBetween(1, 1000);
        }
    }

    @Override
    public boolean supports(String dataType) {
        return "number".equals(dataType) || 
               "numeric".equals(dataType) || 
               "integer".equals(dataType) ||
               "decimal".equals(dataType) ||
               "float".equals(dataType) ||
               "double".equals(dataType) ||
               "money".equals(dataType) ||
               "age".equals(dataType) ||
               "score".equals(dataType);
    }

    @Override
    public String getGeneratorType() {
        return "numeric";
    }
}
