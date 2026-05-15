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
            if (result instanceof String str && config.maxLength() != null && str.length() > config.maxLength()) {
                return truncate(str, config.maxLength());
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to evaluate expression: {}", expression, e);
            return "[表达式错误: " + expression + "]";
        }
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
