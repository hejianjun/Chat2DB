package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String subType = columnConfig.getSubType();
        if (subType == null) {
            subType = "company";
        }

        return switch (subType) {
            case "company" -> faker.company().name();
            case "department" -> faker.commerce().department();
            case "position" -> faker.job().title();
            case "industry" -> faker.company().industry();
            case "product" -> faker.commerce().productName();
            default -> faker.company().name();
        };
    }

    @Override
    public GeneratorMetadata getMetadata() {
        return GeneratorMetadata.of("business", "商业", List.of(
                new GeneratorMetadata.SubTypeOption("company", "公司"),
                new GeneratorMetadata.SubTypeOption("department", "部门"),
                new GeneratorMetadata.SubTypeOption("position", "职位"),
                new GeneratorMetadata.SubTypeOption("industry", "行业"),
                new GeneratorMetadata.SubTypeOption("product", "产品")
        ));
    }
}
