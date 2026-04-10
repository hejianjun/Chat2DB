package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;

/**
 * 获取表结构动作
 */
@Component
@Slf4j
public class FetchSchemaAction extends BaseChatAction {

    @Autowired
    private DatabaseService databaseService;

    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        if (ctx.isCancelled()) {
            return;
        }

        sendStateEvent(ctx.getSseEmitter(),
                ChatState.FETCHING_TABLE_SCHEMA, "正在获取表结构...");

        CompletableFuture.runAsync(() -> {
            buildContext(ctx);
            try {
                String schemaDdl = fetchSchemaDdl(ctx);
                ctx.setSchemaDdl(schemaDdl);

                if (CollectionUtils.isNotEmpty(ctx.getRequest().getTableNames())) {
                    sendSchemaFetched(ctx.getSseEmitter(), schemaDdl);
                }

                context.getStateMachine().sendEvent(
                        MessageBuilder.withPayload(ChatEvent.SCHEMA_FETCHED).build()
                );
            } catch (Exception e) {
                log.error("Fetch schema failed", e);
                sendError(ctx.getSseEmitter(), "获取表结构失败：" + e.getMessage());
                context.getStateMachine().sendEvent(
                        MessageBuilder.withPayload(ChatEvent.FETCH_SCHEMA_FAILED).build()
                );
            } finally {
                removeContext();
            }
        });
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
        return databaseService.buildTableColumn(
                request.getDataSourceId(),
                request.getDatabaseName(),
                request.getSchemaName(),
                request.getTableNames()
        );
    }

    private String queryAllTables(ChatQueryRequest request) {
        return databaseService.queryDatabaseTables(
                request.getDataSourceId(),
                request.getDatabaseName(),
                request.getSchemaName()
        );
    }
}