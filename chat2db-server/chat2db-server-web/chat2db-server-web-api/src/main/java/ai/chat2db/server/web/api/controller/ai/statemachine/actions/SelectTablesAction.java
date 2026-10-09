package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.chat2db.server.domain.api.model.AiMessage;
import ai.chat2db.server.domain.api.service.AiConversationMemoryStore;
import ai.chat2db.server.web.api.config.AiChatConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptBuilder;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 自动选择表动作
 */
@Component
@Slf4j
public class SelectTablesAction extends BaseChatAction {

    private static final int MAX_HISTORY_MESSAGES = 6;
    private static final Pattern QUERY_CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:sql|mql|javascript|js)\\s+([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private AiChatConfig aiChatConfig;

    @Autowired
    private AiConversationMemoryStore memoryStore;

    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        restoreTrace(ctx);
        log.info("[SelectTablesAction] execute called");
        log.info("[SelectTablesAction] uid: {}, cancelled: {}", ctx.getUid(), ctx.isCancelled());
        if (ctx.isCancelled()) {
            log.info("[SelectTablesAction] cancelled, returning");
            removeContext();
            return;
        }

        sendStateEvent(ctx.getSseEmitter(),
                ChatState.AUTO_SELECTING_TABLES, "正在选择相关表...");

        buildContext(ctx);
        try {
            log.info("[SelectTablesAction] Starting table selection for uid: {}", ctx.getUid());
            List<String> tableNames = selectTables(ctx);
            log.info("[SelectTablesAction] Selected tables: {}", tableNames);

            if (CollectionUtils.isNotEmpty(tableNames)) {
                ctx.getRequest().setTableNames(tableNames);
                ctx.setSelectedTables(tableNames);
                sendTablesSelected(ctx.getSseEmitter(), tableNames);
            }

            log.info("[SelectTablesAction] Sending AUTO_SELECT_DONE event for uid: {}", ctx.getUid());
            propagateTrace(context.getStateMachine().sendEvent(
                    Mono.just(MessageBuilder.withPayload(ChatEvent.AUTO_SELECT_DONE).build())
            ), ctx).subscribe();
        } catch (Exception e) {
            log.error("[SelectTablesAction] Auto select tables failed for uid: {}", ctx.getUid(), e);
            sendError(ctx.getSseEmitter(), "选表失败：" + e.getMessage());
            propagateTrace(context.getStateMachine().sendEvent(
                    Mono.just(MessageBuilder.withPayload(ChatEvent.AUTO_SELECT_FAILED).build())
            ), ctx).subscribe();
        } finally {
            removeContext();
        }
    }

    private List<String> selectTables(ChatContext ctx) {
        String selectPrompt = buildSelectPrompt(ctx);
        log.info("[SelectTablesAction] Select prompt for uid: {}:\n{}", ctx.getUid(), selectPrompt);
        ChatResponse chatResponse = aiChatConfig.createFastChatClient()
                .prompt(selectPrompt)
                .call()
                .chatResponse();

        String content = extractContent(chatResponse);
        log.info("[SelectTablesAction] AI response for uid: {}:\n{}", ctx.getUid(), content);
        return parseTableNames(content, ctx.getRequest().getTableNames());
    }

    String buildSelectPrompt(ChatContext ctx) {
        String schemaDdl = databaseService.queryDatabaseTables(
                ctx.getRequest().getDataSourceId(),
                ctx.getRequest().getDatabaseName(),
                ctx.getRequest().getSchemaName()
        );

        PromptContext promptContext = PromptContext.builder()
                .promptType(PromptType.SELECT_TABLES)
                .message(ctx.getRequest().getMessage())
                .schemaDdl(schemaDdl)
                .dataSourceType(dataSourceService.queryDatabaseType(ctx.getRequest().getDataSourceId()))
                .build();

        String conversationId = ctx.getRequest().getConversationId();
        if (StringUtils.isNotBlank(conversationId)) {
            // 选表只读取正式对话，避免将选表 JSON 写入会话记忆。
            List<AiMessage> history = memoryStore.get(conversationId).stream()
                    .filter(message -> "user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                    .toList();
            int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            promptContext.setHistory(JSONArray.toJSONString(history.subList(start, history.size())));
            promptContext.setPreviousSql(extractPreviousQuery(history));
        }

        return promptBuilder.context(promptContext).build();
    }

    private String extractPreviousQuery(List<AiMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage message = history.get(i);
            if (!"assistant".equals(message.getRole()) || StringUtils.isBlank(message.getContent())) {
                continue;
            }
            Matcher matcher = QUERY_CODE_BLOCK_PATTERN.matcher(message.getContent());
            String query = null;
            // 同一条回复含多个代码块时，优先使用最后一个查询。
            while (matcher.find()) {
                if (StringUtils.isNotBlank(matcher.group(1))) {
                    query = matcher.group(1).trim();
                }
            }
            if (query != null) {
                return query;
            }
        }
        return null;
    }

    private String extractContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }

    private List<String> parseTableNames(String content, List<String> existingTableNames) {
        if (StrUtil.isBlank(content)) {
            return null;
        }

        String json = normalizeJson(content);

        try {
            JSONObject obj = JSONObject.parseObject(json);
            JSONArray tableNames = obj.getJSONArray("table_names");
            if (tableNames == null || tableNames.isEmpty()) {
                return null;
            }
            return tableNames.toJavaList(String.class);
        } catch (Exception ignored) {
        }

        return fallbackParseTableNames(content, existingTableNames);
    }

    private String normalizeJson(String content) {
        String json = content.trim();
        if (!json.startsWith("{") && json.contains("{") && json.contains("}")) {
            json = json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1);
        }
        return json;
    }

    private List<String> fallbackParseTableNames(String content, List<String> existingTableNames) {
        if (existingTableNames == null || existingTableNames.isEmpty()) {
            return null;
        }
        return existingTableNames.stream()
                .filter(name -> StringUtils.containsIgnoreCase(content, name))
                .toList();
    }
}
