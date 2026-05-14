package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NameDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String subType = columnConfig.getSubType();
        if (subType == null) {
            subType = "fullName";
        }

        return switch (subType) {
            case "firstName" -> faker.name().firstName();
            case "lastName" -> faker.name().lastName();
            case "fullName" -> faker.name().fullName();
            default -> faker.name().fullName();
        };
    }

    @Override
    public GeneratorMetadata getMetadata() {
        return GeneratorMetadata.of("name", "姓名", List.of(
                new GeneratorMetadata.SubTypeOption("firstName", "名"),
                new GeneratorMetadata.SubTypeOption("lastName", "姓"),
                new GeneratorMetadata.SubTypeOption("fullName", "全名")
        ));
    }
}
