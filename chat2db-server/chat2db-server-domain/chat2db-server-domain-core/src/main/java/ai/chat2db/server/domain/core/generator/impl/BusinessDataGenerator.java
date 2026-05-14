package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

/**
 * 商业数据生成器
 */
@Component
public class BusinessDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String columnName = columnConfig.getColumnName().toLowerCase();
        
        if (columnName.contains("company") || columnName.contains("公司") || columnName.contains("企业")) {
            return faker.company().name();
        } else if (columnName.contains("department") || columnName.contains("部门")) {
            return faker.commerce().department();
        } else if (columnName.contains("position") || columnName.contains("职位") || columnName.contains("岗位")) {
            return faker.job().position();
        } else if (columnName.contains("title") || columnName.contains("头衔")) {
            return faker.job().title();
        } else if (columnName.contains("industry") || columnName.contains("行业")) {
            return faker.company().industry();
        } else if (columnName.contains("product") || columnName.contains("产品")) {
            return faker.commerce().productName();
        } else if (columnName.contains("price") || columnName.contains("价格")) {
            return faker.commerce().price();
        } else {
            // 默认生成公司名
            return faker.company().name();
        }
    }

    @Override
    public boolean supports(String dataType) {
        return "company".equals(dataType) || 
               "business".equals(dataType) || 
               "department".equals(dataType) ||
               "position".equals(dataType) ||
               "job".equals(dataType) ||
               "industry".equals(dataType) ||
               "product".equals(dataType);
    }

    @Override
    public String getGeneratorType() {
        return "business";
    }
}
