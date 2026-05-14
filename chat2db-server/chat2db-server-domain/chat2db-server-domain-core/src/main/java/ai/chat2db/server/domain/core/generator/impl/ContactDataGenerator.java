package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContactDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String subType = columnConfig.getSubType();
        if (subType == null) {
            subType = "email";
        }

        return switch (subType) {
            case "email" -> faker.internet().emailAddress();
            case "phone" -> faker.phoneNumber().phoneNumber();
            case "mobile" -> faker.phoneNumber().cellPhone();
            default -> faker.internet().emailAddress();
        };
    }

    @Override
    public GeneratorMetadata getMetadata() {
        return GeneratorMetadata.of("contact", "联系方式", List.of(
                new GeneratorMetadata.SubTypeOption("email", "邮箱"),
                new GeneratorMetadata.SubTypeOption("phone", "电话"),
                new GeneratorMetadata.SubTypeOption("mobile", "手机号")
        ));
    }
}
