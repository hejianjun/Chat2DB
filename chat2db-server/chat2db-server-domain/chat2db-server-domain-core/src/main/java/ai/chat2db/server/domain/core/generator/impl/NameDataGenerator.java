package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 姓名数据生成器
 */
@Component
public class NameDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        // 根据列名判断生成什么类型的姓名
        String columnName = columnConfig.getColumnName().toLowerCase();
        
        if (columnName.contains("first") || columnName.contains("名")) {
            return faker.name().firstName();
        } else if (columnName.contains("last") || columnName.contains("姓")) {
            return faker.name().lastName();
        } else if (columnName.contains("full") || columnName.contains("全名")) {
            return faker.name().fullName();
        } else {
            // 默认生成全名
            return faker.name().fullName();
        }
    }

    @Override
    public boolean supports(String dataType) {
        return "name".equals(dataType) || 
               "firstname".equals(dataType) || 
               "lastname".equals(dataType) ||
               "fullname".equals(dataType);
    }

    @Override
    public String getGeneratorType() {
        return "name";
    }
}
