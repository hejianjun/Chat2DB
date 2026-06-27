package ai.chat2db.server.web.api.controller.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;

class PromptBuilderImplTest {

    private final PromptBuilderImpl promptBuilder = new PromptBuilderImpl(
            new PromptTemplateRegistry(),
            new PromptValidator());

    @Test
    void buildNl2SqlWithoutHistoryKeepsInitialTemplate() {
        String prompt = promptBuilder.context(PromptContext.builder()
                .promptType(PromptType.NL_2_SQL)
                .message("查询最近 7 天订单")
                .schemaDdl("CREATE TABLE orders(id bigint, created_at datetime)")
                .dataSourceType("MYSQL")
                .build()).build();

        assertTrue(prompt.contains("### SQL input: 查询最近 7 天订单"));
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
}
