package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import ai.chat2db.server.web.api.config.AiChatConfig;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import ai.chat2db.server.web.api.controller.ai.utils.PromptService;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AutoSelectTablesAction extends BaseChatAction {

    @Autowired
    private PromptService promptService;


    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        if (ctx.isCancelled()) {
            return;
        }

        try {
            sendStateEvent(ctx.getSseEmitter(), ChatState.AUTO_SELECTING_TABLES, "正在选择相关表...");

            List<String> tableNames = selectTables(ctx);

            if (CollectionUtils.isNotEmpty(tableNames)) {
                ctx.getRequest().setTableNames(tableNames);
                ctx.setSelectedTables(tableNames);
                sendTablesSelected(ctx.getSseEmitter(), tableNames);
            }

            context.getStateMachine().sendEvent(MessageBuilder.withPayload(ChatEvent.AUTO_SELECT_DONE).build());
        } catch (Exception e) {
            log.error("Auto select tables failed", e);
            sendError(ctx.getSseEmitter(), "选表失败：" + e.getMessage());
            context.getStateMachine().sendEvent(MessageBuilder.withPayload(ChatEvent.AUTO_SELECT_FAILED).build());
        }
    }

    private List<String> selectTables(ChatContext ctx) {
        String selectPrompt = buildSelectPrompt(ctx);

        ChatResponse chatResponse = ctx.getChatClient().prompt()
            .user(selectPrompt)
            .call()
            .chatResponse();

        String content = extractContent(chatResponse);
        return parseTableNames(content, ctx.getRequest().getTableNames());
    }

    private String buildSelectPrompt(ChatContext ctx) {
        return promptService.buildAutoPrompt(ctx.getRequest())
            + "\n\n请只输出 JSON，格式：{\"table_names\":[\"表名 1\",\"表名 2\"]}，不要输出其他文字。";
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
