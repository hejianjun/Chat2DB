package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import ai.chat2db.server.domain.api.model.AiMessage;
import ai.chat2db.server.domain.api.service.AiConversationMemoryStore;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptBuilderImpl;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptTemplateRegistry;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptValidator;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SelectTablesActionTest {

    private final SelectTablesAction action = new SelectTablesAction();
    private final AiConversationMemoryStore memoryStore = mock(AiConversationMemoryStore.class);
    private final DataSourceService dataSourceService = mock(DataSourceService.class);
    private final ChatQueryRequest request = new ChatQueryRequest();

    @BeforeEach
    void setUp() {
        PromptTemplateRegistry registry = new PromptTemplateRegistry();
        registry.init();
        ReflectionTestUtils.setField(action, "promptBuilder", new PromptBuilderImpl(registry, new PromptValidator()));
        ReflectionTestUtils.setField(action, "memoryStore", memoryStore);
        ReflectionTestUtils.setField(action, "databaseService", mock(DatabaseService.class));
        ReflectionTestUtils.setField(action, "dataSourceService", dataSourceService);
        when(dataSourceService.queryDatabaseType(any())).thenReturn("MYSQL");
        request.setConversationId("conversation-1");
        request.setMessage("只看今年的");
    }

    @Test
    void firstTurnDoesNotAddHistory() {
        when(memoryStore.get("conversation-1")).thenReturn(List.of());

        String prompt = buildPrompt();

        assertFalse(prompt.contains("最近对话历史"));
        assertFalse(prompt.contains("上一版 SQL"));
        verify(memoryStore).get("conversation-1");
        verifyNoMoreInteractions(memoryStore);
    }

    @Test
    void followUpReadsOnlyRecentMessagesAndLastQueryBlock() {
        when(memoryStore.get("conversation-1")).thenReturn(List.of(
                message("user", "不应保留的早期问题"),
                message("assistant", "早期回答"),
                message("user", "查询各车型销量"),
                message("assistant", "```sql\nSELECT * FROM old_sales;\n```"),
                message("user", "按车型汇总"),
                message("assistant", "```SQL\nSELECT * FROM sales;\n```\n"
                        + "```sql\nSELECT model, COUNT(*) FROM sales GROUP BY model;\n```"),
                message("user", "说明统计规则"),
                message("assistant", "按订单数量统计"),
                message("system", "不应保留的系统消息")));

        String prompt = buildPrompt();

        assertTrue(prompt.contains("user: 查询各车型销量"));
        assertTrue(prompt.contains("assistant: 按订单数量统计"));
        assertTrue(prompt.contains("### 上一版 SQL\n```sql\n"
                + "SELECT model, COUNT(*) FROM sales GROUP BY model;"));
        assertFalse(prompt.contains("不应保留"));
        verify(memoryStore).get("conversation-1");
        verifyNoMoreInteractions(memoryStore);
    }

    @Test
    void followUpWithoutSqlStillIncludesBusinessContext() {
        when(memoryStore.get("conversation-1")).thenReturn(List.of(
                message("user", "查询各车型销量"), message("assistant", "请补充统计时间范围")));

        String prompt = buildPrompt();

        assertTrue(prompt.contains("user: 查询各车型销量"));
        assertFalse(prompt.contains("上一版 SQL"));
    }

    @Test
    void mongoFollowUpExtractsJavascriptQuery() {
        when(dataSourceService.queryDatabaseType(any())).thenReturn("MONGODB");
        when(memoryStore.get("conversation-1")).thenReturn(List.of(
                message("assistant", "```javascript\ndb.orders.find({status: 'paid'})\n```")));

        String prompt = buildPrompt();

        assertTrue(prompt.contains("### 上一版 MQL\n```javascript\ndb.orders.find({status: 'paid'})"));
    }

    private String buildPrompt() {
        return action.buildSelectPrompt(ChatContext.builder().request(request).build());
    }

    private AiMessage message(String role, String content) {
        AiMessage message = new AiMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
