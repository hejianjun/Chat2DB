package ai.chat2db.server.web.api.controller.ai.prompt;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 提示词构建器实现
 */
@Component
public class PromptBuilderImpl implements PromptBuilder {

    private final PromptTemplateRegistry templateRegistry;
    private final PromptValidator validator;

    private PromptContext context;

    @Autowired
    public PromptBuilderImpl(PromptTemplateRegistry templateRegistry, PromptValidator validator) {
        this.templateRegistry = templateRegistry;
        this.validator = validator;
    }

    @Override
    public PromptBuilder context(PromptContext context) {
        this.context = context;
        return this;
    }

    @Override
    public PromptBuilder message(String message) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setMessage(message);
        return this;
    }

    @Override
    public PromptBuilder ext(String ext) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setExt(ext);
        return this;
    }

    @Override
    public PromptBuilder schema(String schemaDdl) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setSchemaDdl(schemaDdl);
        return this;
    }

    @Override
    public PromptBuilder dataSourceType(String dataSourceType) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setDataSourceType(dataSourceType);
        return this;
    }

    @Override
    public PromptBuilder targetSqlType(String targetSqlType) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setTargetSqlType(targetSqlType);
        return this;
    }

    @Override
    public PromptBuilder forTableSelection(Boolean forTableSelection) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setForTableSelection(forTableSelection);
        return this;
    }

    @Override
    public String build() {
        validateContext();

        PromptType type = context.getPromptType();
        if (type == null) {
            type = PromptType.NL_2_SQL;
        }

        PromptTemplate template = templateRegistry.getTemplate(type);
        String builtPrompt = fillTemplate(template, context);

        if (Boolean.TRUE.equals(context.getForTableSelection())) {
            builtPrompt = appendTableSelectionInstruction(builtPrompt);
        }

        return validator.cleanPrompt(builtPrompt);
    }

    @Override
    public boolean validate() {
        String builtPrompt = build();
        return validator.isValidLength(builtPrompt);
    }

    private void validateContext() {
        if (context == null) {
            throw new IllegalStateException("PromptContext is null");
        }
        if (StringUtils.isBlank(context.getMessage())) {
            throw new IllegalArgumentException("Message is required");
        }
    }

    private String fillTemplate(PromptTemplate template, PromptContext context) {
        String templateStr = template.getTemplate();
        String description = context.getPromptType() != null 
            ? context.getPromptType().getDescription() 
            : "将自然语言转换成 SQL 查询";

        return templateStr
                .replace("{description}", description)
                .replace("{ext}", StringUtils.defaultString(context.getExt(), ""))
                .replace("{db_type}", StringUtils.defaultString(context.getDataSourceType(), "MYSQL"))
                .replace("{schema}", StringUtils.defaultString(context.getSchemaDdl(), ""))
                .replace("{message}", context.getMessage())
                .replace("{target_sql_type}", StringUtils.defaultString(context.getTargetSqlType(), 
                        StringUtils.defaultString(context.getDataSourceType(), "MYSQL")));
    }

    private String appendTableSelectionInstruction(String prompt) {
        return prompt + "\n\n请只输出 JSON，格式：{\"table_names\":[\"表名 1\",\"表名 2\"]}，不要输出其他文字。";
    }
}
