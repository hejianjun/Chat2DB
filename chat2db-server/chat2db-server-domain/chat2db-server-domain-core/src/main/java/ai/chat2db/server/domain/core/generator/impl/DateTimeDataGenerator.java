package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class DateTimeDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String subType = columnConfig.getSubType();
        if (subType == null) {
            subType = "datetime";
        }

        Map<String, Object> params = columnConfig.getCustomParams();

        return switch (subType) {
            case "birthday" -> faker.date().birthday();
            case "date" -> {
                int daysBack = getIntParam(params, "daysBack", 365);
                yield LocalDate.now().minusDays(faker.number().numberBetween(0, daysBack))
                        .format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            case "datetime" -> {
                int yearsBack = getIntParam(params, "yearsBack", 5);
                Date past = new Date(System.currentTimeMillis() - (long) yearsBack * 365 * 24 * 60 * 60 * 1000);
                Date now = new Date();
                Date between = faker.date().between(past, now);
                LocalDateTime ldt = LocalDateTime.ofInstant(between.toInstant(), ZoneId.systemDefault());
                yield ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            case "past" -> {
                int days = getIntParam(params, "days", 30);
                Date past = new Date(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000);
                Date now = new Date();
                Date between = faker.date().between(past, now);
                LocalDateTime ldt = LocalDateTime.ofInstant(between.toInstant(), ZoneId.systemDefault());
                yield ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            case "future" -> {
                int days = getIntParam(params, "days", 30);
                Date future = faker.date().future(days, java.util.concurrent.TimeUnit.DAYS);
                LocalDateTime ldt = LocalDateTime.ofInstant(future.toInstant(), ZoneId.systemDefault());
                yield ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            default -> faker.date().birthday();
        };
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object val = params.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public GeneratorMetadata getMetadata() {
        return GeneratorMetadata.full("datetime", "日期时间", List.of(
                new GeneratorMetadata.SubTypeOption("date", "日期"),
                new GeneratorMetadata.SubTypeOption("datetime", "日期时间"),
                new GeneratorMetadata.SubTypeOption("birthday", "生日"),
                new GeneratorMetadata.SubTypeOption("past", "过去时间"),
                new GeneratorMetadata.SubTypeOption("future", "未来时间")
        ), List.of(
                new GeneratorMetadata.ConfigField("daysBack", "天数回溯", "number", 365),
                new GeneratorMetadata.ConfigField("yearsBack", "年数回溯", "number", 5),
                new GeneratorMetadata.ConfigField("days", "天数", "number", 30)
        ));
    }
}
