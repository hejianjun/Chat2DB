package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

/**
 * 联系方式数据生成器
 */
@Component
public class ContactDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String columnName = columnConfig.getColumnName().toLowerCase();
        
        if (columnName.contains("email") || columnName.contains("邮箱") || columnName.contains("邮件")) {
            return faker.internet().emailAddress();
        } else if (columnName.contains("phone") || columnName.contains("电话") || columnName.contains("手机")) {
            return faker.phoneNumber().phoneNumber();
        } else if (columnName.contains("mobile") || columnName.contains("手机号")) {
            return faker.phoneNumber().cellPhone();
        } else if (columnName.contains("fax") || columnName.contains("传真")) {
            return faker.phoneNumber().phoneNumber();
        } else {
            // 默认生成邮箱
            return faker.internet().emailAddress();
        }
    }

    @Override
    public boolean supports(String dataType) {
        return "email".equals(dataType) || 
               "phone".equals(dataType) || 
               "mobile".equals(dataType) ||
               "contact".equals(dataType);
    }

    @Override
    public String getGeneratorType() {
        return "contact";
    }
}
