package ai.chat2db.server.domain.core.generator.impl;

import ai.chat2db.server.domain.core.generator.DataGenerator;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

/**
 * 文本数据生成器
 */
@Component
public class TextDataGenerator implements DataGenerator {

    @Override
    public Object generate(Faker faker, ColumnConfig columnConfig) {
        String columnName = columnConfig.getColumnName().toLowerCase();
        String dataType = columnConfig.getDataType().toLowerCase();
        
        if (columnName.contains("title") || columnName.contains("标题")) {
            return generateTitle(faker, columnConfig.getMaxLength());
        } else if (columnName.contains("desc") || columnName.contains("描述") || columnName.contains("description")) {
            return generateDescription(faker, columnConfig.getMaxLength());
        } else if (columnName.contains("content") || columnName.contains("内容")) {
            return generateContent(faker, columnConfig.getMaxLength());
        } else if (columnName.contains("comment") || columnName.contains("评论") || columnName.contains("备注")) {
            return generateComment(faker, columnConfig.getMaxLength());
        } else if (columnName.contains("name") || columnName.contains("名称")) {
            return generateName(faker, columnConfig.getMaxLength());
        } else if (dataType.contains("text") || dataType.contains("varchar") || dataType.contains("char")) {
            return generateGenericText(faker, columnConfig.getMaxLength());
        } else {
            // 默认生成通用文本
            return generateGenericText(faker, columnConfig.getMaxLength());
        }
    }

    private String generateTitle(Faker faker, Integer maxLength) {
        String title = faker.lorem().sentence(3); // 3个词的句子
        return truncateIfNeeded(title, maxLength);
    }

    private String generateDescription(Faker faker, Integer maxLength) {
        String desc = faker.lorem().paragraph(2); // 2段描述
        return truncateIfNeeded(desc, maxLength);
    }

    private String generateContent(Faker faker, Integer maxLength) {
        String content = faker.lorem().paragraph(5); // 5段内容
        return truncateIfNeeded(content, maxLength);
    }

    private String generateComment(Faker faker, Integer maxLength) {
        String comment = faker.lorem().sentence();
        return truncateIfNeeded(comment, maxLength);
    }

    private String generateName(Faker faker, Integer maxLength) {
        String name = faker.lorem().words(2).toString().replace("[", "").replace("]", "").replace(",", "");
        return truncateIfNeeded(name, maxLength);
    }

    private String generateGenericText(Faker faker, Integer maxLength) {
        String text = faker.lorem().sentence();
        return truncateIfNeeded(text, maxLength);
    }

    private String truncateIfNeeded(String text, Integer maxLength) {
        if (maxLength != null && text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text;
    }

    @Override
    public boolean supports(String dataType) {
        return "text".equals(dataType) || 
               "string".equals(dataType) || 
               "varchar".equals(dataType) ||
               "char".equals(dataType) ||
               "content".equals(dataType) ||
               "description".equals(dataType) ||
               "comment".equals(dataType) ||
               "title".equals(dataType);
    }

    @Override
    public String getGeneratorType() {
        return "text";
    }
}
