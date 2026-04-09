package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import ai.chat2db.server.web.api.controller.ai.converter.ChatConverter;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import ai.chat2db.server.web.api.controller.ai.utils.PromptService;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FetchSchemaAction extends BaseChatAction {

    @Autowired
    private PromptService promptService;

    @Autowired
    private ChatConverter chatConverter;

    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        if (ctx.isCancelled()) {
            return;
        }

        try {
            sendStateEvent(ctx.getSseEmitter(), ChatState.FETCHING_TABLE_SCHEMA, "正在获取表结构...");

            String schemaDdl = fetchSchemaDdl(ctx);
            ctx.setSchemaDdl(schemaDdl);

            if (CollectionUtils.isNotEmpty(ctx.getRequest().getTableNames())) {
                sendSchemaFetched(ctx.getSseEmitter(), schemaDdl);
            }

            context.getStateMachine().sendEvent(ChatEvent.SCHEMA_FETCHED);
        } catch (Exception e) {
            log.error("Fetch schema failed", e);
            sendError(ctx.getSseEmitter(), "获取表结构失败：" + e.getMessage());
            context.getStateMachine().sendEvent(ChatEvent.FETCH_SCHEMA_FAILED);
        }
    }

    private String fetchSchemaDdl(ChatContext ctx) {
        ChatQueryRequest request = ctx.getRequest();
        
        if (isTextGeneration(request)) {
            return "";
        } else if (hasTableNames(request)) {
            return queryTablesWithNames(request);
        } else {
            return queryAllTables(request);
        }
    }

    private boolean isTextGeneration(ChatQueryRequest request) {
        return PromptType.TEXT_GENERATION.getCode().equals(request.getPromptType());
    }

    private boolean hasTableNames(ChatQueryRequest request) {
        return CollectionUtils.isNotEmpty(request.getTableNames());
    }

    private String queryTablesWithNames(ChatQueryRequest request) {
        TableQueryParam queryParam = chatConverter.chat2tableQuery(request);
        return promptService.buildTableColumn(queryParam, request.getTableNames());
    }

    private String queryAllTables(ChatQueryRequest request) {
        return promptService.queryDatabaseTables(request);
    }
}
