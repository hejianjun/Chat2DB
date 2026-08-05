package ai.chat2db.server.web.api.controller.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;

class PromptBuilderImplTest {

    private final PromptBuilderImpl promptBuilder = createPromptBuilder();

    private static PromptBuilderImpl createPromptBuilder() {
        PromptTemplateRegistry templateRegistry = new PromptTemplateRegistry();
        templateRegistry.init();
        return new PromptBuilderImpl(templateRegistry, new PromptValidator());
    }

    @Test
    void buildNl2SqlWithoutHistoryKeepsInitialTemplate() {
        String prompt = promptBuilder.context(PromptContext.builder()
                .promptType(PromptType.NL_2_SQL)
                .message("查询最近 7 天订单")
                .schemaDdl("CREATE TABLE orders(id bigint, created_at datetime)")
                .dataSourceType("MYSQL")
                .build()).build();

        assertTrue(prompt.contains("### MYSQL SQL tables, with their properties:"));
        assertTrue(prompt.contains("CREATE TABLE orders(id bigint, created_at datetime)"));
        assertFalse(prompt.contains("连续对话修正要求"));
        assertFalse(prompt.contains("上一版 SQL"));
    }

    @Test
    void buildNl2SqlDoesNotInjectLegacyHistoryAndPreviousSql() {
        String prompt = promptBuilder.context(PromptContext.builder()
                .promptType(PromptType.NL_2_SQL)
                .message("按金额倒序，限制 100 条")
                .schemaDdl("CREATE TABLE orders(id bigint, amount decimal(10, 2))")
                .dataSourceType("MYSQL")
                .isRevision(true)
                .previousSql("select id, amount from orders")
                .history("[{\"role\":\"user\",\"content\":\"查询订单\"},"
                        + "{\"role\":\"assistant\",\"content\":\"```sql\\nselect id, amount from orders\\n```\"}]")
                .build()).build();

        assertFalse(prompt.contains("连续对话修正要求"));
        assertFalse(prompt.contains("最近对话历史"));
        assertFalse(prompt.contains("user: 查询订单"));
        assertFalse(prompt.contains("上一版 SQL"));
    }

    @Test
    void mongoNl2SqlUsesMqlTemplateAndJsonSchema() {
        String prompt = promptBuilder.context(PromptContext.builder()
                .promptType(PromptType.NL_2_SQL)
                .message("查询待支付订单")
                .schemaDdl("[{\"collection\":\"orders\",\"schema\":{\"$jsonSchema\":{}}}]")
                .dataSourceType("MONGODB")
                .build()).build();

        assertTrue(prompt.contains("Collection JSON Schema"));
        assertTrue(prompt.contains("db.getCollection(\"collectionName\")"));
        assertTrue(prompt.contains("\"collection\":\"orders\""));
        assertTrue(prompt.contains("不要输出 SQL"));
    }

    @Test
    void mongoTableSelectionUsesDatabaseObjectTerminology() {
        String prompt = promptBuilder.context(PromptContext.builder()
                .promptType(PromptType.SELECT_TABLES)
                .message("查询待处理的位置回填任务")
                .schemaDdl("location_backfill_checkpoint")
                .dataSourceType("MONGODB")
                .build()).build();

        assertTrue(prompt.contains("MONGODB database objects (tables or collections)"));
        assertTrue(prompt.contains("User input: 查询待处理的位置回填任务"));
        assertTrue(prompt.contains("table_names key is also used for MongoDB collection names"));
        assertFalse(prompt.contains("SQL tables"));
        assertFalse(prompt.contains("SQL input"));
    }

    @Test
    void mongoExplainUsesMqlTerminology() {
        String prompt = promptBuilder.context(PromptContext.builder()
                .promptType(PromptType.SQL_EXPLAIN)
                .message("db.location_backfill_checkpoint.find({})")
                .schemaDdl("[{\"collection\":\"location_backfill_checkpoint\"}]")
                .explainPlan("{\"winningPlan\":{\"stage\":\"COLLSCAN\"}}")
                .dataSourceType("MONGODB")
                .build()).build();

        assertTrue(prompt.contains("collection properties 和 MQL input，解释MQL"));
        assertTrue(prompt.contains("### MONGODB collections, with their properties:"));
        assertTrue(prompt.contains("### MQL Execution Plan (EXPLAIN):"));
        assertFalse(prompt.contains("SQL input"));
        assertFalse(prompt.contains("SQL tables"));
    }

    @Test
    void relationalExplainKeepsSqlTerminology() {
        String prompt = promptBuilder.context(PromptContext.builder()
                .promptType(PromptType.SQL_EXPLAIN)
                .message("select * from orders")
                .schemaDdl("CREATE TABLE orders(id bigint)")
                .explainPlan("Table scan on orders")
                .dataSourceType("MYSQL")
                .build()).build();

        assertTrue(prompt.contains("table properties 和 SQL input，解释SQL"));
        assertTrue(prompt.contains("### MYSQL tables, with their properties:"));
        assertTrue(prompt.contains("### SQL Execution Plan (EXPLAIN):"));
    }

    @Test
    void buildDataExpressionPromptIncludesEnumMockGuidance() {
        String prompt = promptBuilder.context(PromptContext.builder()
                .promptType(PromptType.NL_2_DATA_EXPRESSION)
                .message("为字段推荐 datafaker 表达式")
                .schemaDdl("CREATE TABLE user(status tinyint COMMENT '0-禁用，1-启用', name varchar(32))")
                .dataSourceType("MYSQL")
                .build()).build();

        assertTrue(prompt.contains("#{Options.option '启用','禁用','待定'}"));
        assertTrue(prompt.contains("将其中明确给出的候选值原样放入 Options.option"));
        assertTrue(prompt.contains("#{Options.option '0','1'}"));
        assertTrue(prompt.contains("#{Name.first_name}"));
        assertTrue(prompt.contains("#{Date.past '30','DAYS'}"));
        assertTrue(prompt.contains("#{Number.number_between '1','1000'}"));
    }
}
