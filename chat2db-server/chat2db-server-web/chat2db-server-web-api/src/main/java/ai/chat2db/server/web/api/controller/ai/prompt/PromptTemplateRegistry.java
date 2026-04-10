package ai.chat2db.server.web.api.controller.ai.prompt;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 提示词模板注册表
 */
@Component
public class PromptTemplateRegistry {

    private final Map<PromptType, PromptTemplate> templates = new EnumMap<>(PromptType.class);

    @PostConstruct
    public void init() {
        register(PromptType.NL_2_SQL, buildNl2SqlTemplate());
        register(PromptType.SQL_EXPLAIN, buildSqlExplainTemplate());
        register(PromptType.SQL_OPTIMIZER, buildSqlOptimizerTemplate());
        register(PromptType.SQL_2_SQL, buildSql2SqlTemplate());
        register(PromptType.SELECT_TABLES, buildSelectTablesTemplate());
        register(PromptType.TEXT_GENERATION, buildTextGenerationTemplate());
        register(PromptType.TITLE_GENERATION, buildTitleGenerationTemplate());
        register(PromptType.NL_2_COMMENT, buildNl2CommentTemplate());
    }

    private void register(PromptType type, PromptTemplate template) {
        templates.put(type, template);
    }

    /**
     * 根据类型获取模板
     *
     * @param type 提示词类型
     * @return 模板
     */
    public PromptTemplate getTemplate(PromptType type) {
        return templates.getOrDefault(type, getDefaultTemplate());
    }

    /**
     * 根据代码获取模板
     *
     * @param code 类型代码
     * @return 模板
     */
    public PromptTemplate getTemplate(String code) {
        PromptType type = PromptType.valueOf(code);
        return getTemplate(type);
    }

    private PromptTemplate getDefaultTemplate() {
        return templates.get(PromptType.NL_2_SQL);
    }

    private PromptTemplate buildNl2SqlTemplate() {
        return PromptTemplate.builder()
                .name("nl_2_sql")
                .promptType(PromptType.NL_2_SQL)
                .template("### 请根据以下 table properties 和 SQL input{description}. {ext}\n" +
                        "#\n" +
                        "### {db_type} SQL tables, with their properties:\n" +
                        "#\n" +
                        "# {schema}\n" +
                        "#\n" +
                        "#\n" +
                        "### SQL input: {message}")
                .build();
    }

    private PromptTemplate buildSqlExplainTemplate() {
        return PromptTemplate.builder()
                .name("sql_explain")
                .promptType(PromptType.SQL_EXPLAIN)
                .template("### 请根据以下 table properties 和 SQL input{description}. {ext}\n" +
                        "#\n" +
                        "### {db_type} SQL tables, with their properties:\n" +
                        "#\n" +
                        "# {schema}\n" +
                        "#\n" +
                        "#\n" +
                        "### SQL input: {message}")
                .build();
    }

    private PromptTemplate buildSqlOptimizerTemplate() {
        return PromptTemplate.builder()
                .name("sql_optimizer")
                .promptType(PromptType.SQL_OPTIMIZER)
                .template("### 请根据以下 table properties 和 SQL input{description}. {ext}\n" +
                        "#\n" +
                        "### {db_type} SQL tables, with their properties:\n" +
                        "#\n" +
                        "# {schema}\n" +
                        "#\n" +
                        "#\n" +
                        "### SQL input: {message}")
                .build();
    }

    private PromptTemplate buildSql2SqlTemplate() {
        return PromptTemplate.builder()
                .name("sql_2_sql")
                .promptType(PromptType.SQL_2_SQL)
                .template("### 请根据以下 table properties 和 SQL input{description}. {ext}\n" +
                        "#\n" +
                        "### {db_type} SQL tables, with their properties:\n" +
                        "#\n" +
                        "# {schema}\n" +
                        "#\n" +
                        "#\n" +
                        "### SQL input: {message}\n" +
                        "#\n" +
                        "### 目标 SQL 类型：{target_sql_type}")
                .build();
    }

    private PromptTemplate buildSelectTablesTemplate() {
        return PromptTemplate.builder()
                .name("select_tables")
                .promptType(PromptType.SELECT_TABLES)
                .template("### 请根据以下 table properties 和 SQL input{description}. {ext}\n" +
                        "#\n" +
                        "### {db_type} SQL tables, with their properties:\n" +
                        "#\n" +
                        "# {schema}\n" +
                        "#\n" +
                        "#\n" +
                        "### SQL input: {message}")
                .build();
    }

    private PromptTemplate buildTextGenerationTemplate() {
        return PromptTemplate.builder()
                .name("text_generation")
                .promptType(PromptType.TEXT_GENERATION)
                .template("{message}")
                .build();
    }

    private PromptTemplate buildTitleGenerationTemplate() {
        return PromptTemplate.builder()
                .name("title_generation")
                .promptType(PromptType.TITLE_GENERATION)
                .template("{message}")
                .build();
    }

    private PromptTemplate buildNl2CommentTemplate() {
        return PromptTemplate.builder()
                .name("nl_2_comment")
                .promptType(PromptType.NL_2_COMMENT)
                .template("### 请根据以下 table properties 和 SQL input{description}. {ext}\n" +
                        "#\n" +
                        "### {db_type} SQL tables, with their properties:\n" +
                        "#\n" +
                        "# {schema}\n" +
                        "#\n" +
                        "#\n" +
                        "### SQL input: {message}")
                .build();
    }
}
