package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String subType = columnConfig.getSubType();
        if (subType == null) {
            subType = "text";
        }

        Integer maxLength = columnConfig.getMaxLength();

        return switch (subType) {
            case "title" -> truncate(faker.lorem().sentence(3), maxLength);
            case "description" -> truncate(faker.lorem().paragraph(2), maxLength);
            case "text" -> truncate(faker.lorem().sentence(), maxLength);
            default -> truncate(faker.lorem().sentence(), maxLength);
        };
    }

    private String truncate(String text, Integer maxLength) {
        if (maxLength != null && text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text;
    }

    @Override
    public GeneratorMetadata getMetadata() {
        return GeneratorMetadata.of("text", "文本", List.of(
                new GeneratorMetadata.SubTypeOption("text", "文本"),
                new GeneratorMetadata.SubTypeOption("title", "标题"),
                new GeneratorMetadata.SubTypeOption("description", "描述")
        ));
    }
}
