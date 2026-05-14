package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间数据生成器
 */
@Component
public class DateTimeDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String columnName = columnConfig.getColumnName().toLowerCase();
        
        if (columnName.contains("birth") || columnName.contains("生日")) {
            return faker.date().birthday();
        } else if (columnName.contains("create") || columnName.contains("创建")) {
            return faker.date().between(
                new Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000 * 5), // 5年前
                new Date()
            );
        } else if (columnName.contains("update") || columnName.contains("更新") || columnName.contains("修改")) {
            return faker.date().between(
                new Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000), // 30天前
                new Date()
            );
        } else if (columnName.contains("future") || columnName.contains("未来")) {
            return faker.date().future(30, java.util.concurrent.TimeUnit.DAYS);
        } else if (columnName.contains("date") || columnName.contains("日期")) {
            return LocalDate.now().minusDays(faker.number().numberBetween(0, 365))
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        } else if (columnName.contains("time") || columnName.contains("时间")) {
            return LocalDateTime.now().minusHours(faker.number().numberBetween(0, 24))
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else {
            // 默认生成近期日期
            return faker.date().birthday();
        }
    }

    @Override
    public boolean supports(String dataType) {
        return "date".equals(dataType) || 
               "datetime".equals(dataType) || 
               "time".equals(dataType) ||
               "timestamp".equals(dataType) ||
               "birthday".equals(dataType);
    }

    @Override
    public String getGeneratorType() {
        return "datetime";
    }
}
