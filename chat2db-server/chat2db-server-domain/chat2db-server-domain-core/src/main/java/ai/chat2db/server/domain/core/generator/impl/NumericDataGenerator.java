package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Component
public class NumericDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String subType = columnConfig.getSubType();
        if (subType == null) {
            subType = "integer";
        }

        Map<String, Object> params = columnConfig.getCustomParams();
        int min = getIntParam(params, "min", 0);
        int max = getIntParam(params, "max", 10000);

        return switch (subType) {
            case "integer" -> faker.number().numberBetween(min, max);
            case "decimal" -> {
                int scale = columnConfig.getScale() != null ? columnConfig.getScale() : 2;
                yield new BigDecimal(faker.number().randomDouble(scale, min, max))
                        .setScale(scale, RoundingMode.HALF_UP);
            }
            case "price" -> new BigDecimal(faker.number().randomDouble(2, 10, 10000))
                    .setScale(2, RoundingMode.HALF_UP);
            case "age" -> faker.number().numberBetween(18, 80);
            case "score" -> faker.number().numberBetween(0, 100);
            default -> faker.number().numberBetween(min, max);
        };
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object val = params.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public GeneratorMetadata getMetadata() {
        return GeneratorMetadata.full("numeric", "数值", List.of(
                new GeneratorMetadata.SubTypeOption("integer", "整数"),
                new GeneratorMetadata.SubTypeOption("decimal", "小数"),
                new GeneratorMetadata.SubTypeOption("price", "价格"),
                new GeneratorMetadata.SubTypeOption("age", "年龄"),
                new GeneratorMetadata.SubTypeOption("score", "评分")
        ), List.of(
                new GeneratorMetadata.ConfigField("min", "最小值", "number", 0),
                new GeneratorMetadata.ConfigField("max", "最大值", "number", 10000)
        ));
    }
}
