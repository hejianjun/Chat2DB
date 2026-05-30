package ai.chat2db.server.domain.core.generator;

import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExpressionDataGenerator {

    public Object generate(Faker faker, String expression, ColumnGenerationConfig config) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Object result = faker.expression(expression);
            result = convertByDataType(result, config);
            if (result instanceof String str && config.maxLength() != null && str.length() > config.maxLength()) {
                return truncate(str, config.maxLength());
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to evaluate expression: {}", expression, e);
            return "[表达式错误: " + expression + "]";
        }
    }

    private Object convertByDataType(Object value, ColumnGenerationConfig config) {
        if (value == null || config.dataType() == null) {
            return value;
        }
        String dataType = config.dataType().toLowerCase();
        if (isIntegerType(dataType)) {
            Boolean boolValue = parseBoolean(value);
            if (boolValue != null) {
                return boolValue ? 1 : 0;
            }
        }
        if (isBooleanType(dataType)) {
            Boolean boolValue = parseBoolean(value);
            if (boolValue != null) {
                return boolValue;
            }
        }
        return value;
    }

    private boolean isIntegerType(String dataType) {
        return dataType.contains("tinyint") || dataType.contains("smallint") || dataType.contains("mediumint")
                || dataType.equals("int") || dataType.contains("integer") || dataType.contains("bigint");
    }

    private boolean isBooleanType(String dataType) {
        return dataType.contains("boolean") || dataType.contains("bool");
    }

    private Boolean parseBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String str) {
            String normalized = str.trim().toLowerCase();
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
        }
        return null;
    }

    private String truncate(String text, int maxLength) {
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    public record ColumnGenerationConfig(
            String columnName,
            String dataType,
            Boolean nullable,
            Integer maxLength,
            Integer scale
    ) {
    }
}
