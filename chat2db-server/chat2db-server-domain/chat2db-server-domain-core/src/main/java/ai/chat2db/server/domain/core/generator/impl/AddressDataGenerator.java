package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AddressDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String subType = columnConfig.getSubType();
        if (subType == null) {
            subType = "fullAddress";
        }

        return switch (subType) {
            case "street" -> faker.address().streetAddress();
            case "city" -> faker.address().city();
            case "state" -> faker.address().state();
            case "zip" -> faker.address().zipCode();
            case "country" -> faker.address().country();
            case "fullAddress" -> faker.address().fullAddress();
            default -> faker.address().fullAddress();
        };
    }

    @Override
    public GeneratorMetadata getMetadata() {
        return GeneratorMetadata.of("address", "地址", List.of(
                new GeneratorMetadata.SubTypeOption("street", "街道"),
                new GeneratorMetadata.SubTypeOption("city", "城市"),
                new GeneratorMetadata.SubTypeOption("state", "省份"),
                new GeneratorMetadata.SubTypeOption("zip", "邮编"),
                new GeneratorMetadata.SubTypeOption("country", "国家"),
                new GeneratorMetadata.SubTypeOption("fullAddress", "完整地址")
        ));
    }
}
