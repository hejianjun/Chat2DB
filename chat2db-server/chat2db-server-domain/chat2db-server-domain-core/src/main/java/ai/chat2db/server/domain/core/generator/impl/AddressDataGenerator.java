package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

/**
 * 地址数据生成器
 */
@Component
public class AddressDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String columnName = columnConfig.getColumnName().toLowerCase();
        
        if (columnName.contains("street") || columnName.contains("街道") || columnName.contains("街")) {
            return faker.address().streetAddress();
        } else if (columnName.contains("city") || columnName.contains("城市")) {
            return faker.address().city();
        } else if (columnName.contains("state") || columnName.contains("省份") || columnName.contains("州")) {
            return faker.address().state();
        } else if (columnName.contains("zip") || columnName.contains("postal") || columnName.contains("邮编")) {
            return faker.address().zipCode();
        } else if (columnName.contains("country") || columnName.contains("国家")) {
            return faker.address().country();
        } else if (columnName.contains("full") || columnName.contains("完整") || columnName.contains("全部")) {
            return faker.address().fullAddress();
        } else {
            // 默认生成完整地址
            return faker.address().fullAddress();
        }
    }

    @Override
    public boolean supports(String dataType) {
        return "address".equals(dataType) || 
               "street".equals(dataType) || 
               "city".equals(dataType) ||
               "state".equals(dataType) ||
               "zip".equals(dataType) ||
               "country".equals(dataType);
    }

    @Override
    public String getGeneratorType() {
        return "address";
    }
}
