package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import ai.chat2db.server.domain.api.model.RedisKeyPrefix;
import ai.chat2db.server.domain.api.param.RedisKeyPrefixQueryParam;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.RedisKeyPrefixService;
import ai.chat2db.server.tools.base.enums.DataSourceTypeEnum;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 获取表结构动作
 */
@Component
@Slf4j
public class FetchSchemaAction extends BaseChatAction {

    private static final int MAX_REDIS_PREFIX_CONTEXT_SIZE = 80;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private RedisKeyPrefixService redisKeyPrefixService;

    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        restoreTrace(ctx);
        log.info("[FetchSchemaAction] execute called");
        log.info("[FetchSchemaAction] uid: {}, cancelled: {}", ctx.getUid(), ctx.isCancelled());
        if (ctx.isCancelled()) {
            log.info("[FetchSchemaAction] cancelled, returning");
            removeContext();
            return;
        }

        sendStateEvent(ctx.getSseEmitter(),
                ChatState.FETCHING_TABLE_SCHEMA, "正在获取表结构...");

        buildContext(ctx);
        try {
            log.info("[FetchSchemaAction] Starting schema fetch for uid: {}, tableNames: {}",
                    ctx.getUid(), ctx.getRequest().getTableNames());
            String schemaDdl = fetchSchemaDdl(ctx);
            log.info("[FetchSchemaAction] Schema DDL length: {}", schemaDdl != null ? schemaDdl.length() : 0);
            ctx.setSchemaDdl(schemaDdl);

            if (CollectionUtils.isNotEmpty(ctx.getRequest().getTableNames())) {
                sendSchemaFetched(ctx.getSseEmitter(), schemaDdl);
            }

            log.info("[FetchSchemaAction] Sending SCHEMA_FETCHED event for uid: {}", ctx.getUid());
            propagateTrace(context.getStateMachine().sendEvent(
                    Mono.just(MessageBuilder.withPayload(ChatEvent.SCHEMA_FETCHED).build())
            ), ctx).subscribe();
        } catch (Exception e) {
            log.error("[FetchSchemaAction] Fetch schema failed for uid: {}", ctx.getUid(), e);
            sendError(ctx.getSseEmitter(), "获取表结构失败：" + e.getMessage());
            propagateTrace(context.getStateMachine().sendEvent(
                    Mono.just(MessageBuilder.withPayload(ChatEvent.FETCH_SCHEMA_FAILED).build())
            ), ctx).subscribe();
        } finally {
            removeContext();
        }
    }

    private String fetchSchemaDdl(ChatContext ctx) {
        ChatQueryRequest request = ctx.getRequest();
        String dataSourceType = queryDataSourceType(request);

        if (isRedisCommandPrompt(request, dataSourceType)) {
            return queryRedisKeyPrefixContext(request);
        }

        if (isDataSourceType(dataSourceType, DataSourceTypeEnum.REDIS)) {
            return "";
        }

        if (isDataSourceType(dataSourceType, DataSourceTypeEnum.MONGODB)) {
            return databaseService.queryMongoSchema(
                    request.getDataSourceId(),
                    request.getDatabaseName(),
                    request.getTableNames()
            );
        }

        if (hasTableNames(request)) {
            return queryTablesWithNames(request);
        } else {
            return queryAllTables(request);
        }
    }

    private String queryDataSourceType(ChatQueryRequest request) {
        try {
            return dataSourceService.queryDatabaseType(request.getDataSourceId());
        } catch (Exception e) {
            log.warn("[FetchSchemaAction] Query database type failed, dataSourceId: {}, error: {}",
                    request.getDataSourceId(), e.getMessage());
            return null;
        }
    }

    private boolean isDataSourceType(String dataSourceType, DataSourceTypeEnum expectedType) {
        return expectedType.getCode().equalsIgnoreCase(StringUtils.defaultString(dataSourceType));
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

    private boolean isRedisCommandPrompt(ChatQueryRequest request, String dataSourceType) {
        String promptType = StringUtils.defaultIfBlank(request.getPromptType(), PromptType.NL_2_SQL.getCode());
        return PromptType.NL_2_SQL.getCode().equals(promptType)
                && isDataSourceType(dataSourceType, DataSourceTypeEnum.REDIS);
    }

    private String queryRedisKeyPrefixContext(ChatQueryRequest request) {
        try {
            RedisKeyPrefixQueryParam param = RedisKeyPrefixQueryParam.builder()
                    .dataSourceId(request.getDataSourceId())
                    .databaseName(request.getDatabaseName())
                    .schemaName(request.getSchemaName())
                    .searchKey("")
                    .pageNo(1)
                    .pageSize(MAX_REDIS_PREFIX_CONTEXT_SIZE)
                    .build();
            List<RedisKeyPrefix> prefixes = redisKeyPrefixService.search(param);
            if (CollectionUtils.isEmpty(prefixes)) {
                return "暂无";
            }

            String context = prefixes.stream()
                    .map(prefix -> new RedisPrefixPromptItem(
                            prefix.getName(),
                            StringUtils.defaultIfBlank(prefix.getComment(), prefix.getAiComment()),
                            prefix.getKeyCount()))
                    .filter(item -> StringUtils.isNotBlank(item.prefix()) && StringUtils.isNotBlank(item.comment()))
                    .sorted(Comparator.comparing(
                            RedisPrefixPromptItem::keyCount,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(MAX_REDIS_PREFIX_CONTEXT_SIZE)
                    .map(item -> "- " + item.prefix() + ": " + item.comment()
                            + (item.keyCount() == null ? "" : " (" + item.keyCount() + " keys)"))
                    .collect(Collectors.joining("\n"));

            return StringUtils.defaultIfBlank(context, "暂无");
        } catch (Exception e) {
            log.warn("[FetchSchemaAction] Failed to query Redis key prefix context, dataSourceId: {}, error: {}",
                    request.getDataSourceId(), e.getMessage());
            return "暂无";
        }
    }

    private record RedisPrefixPromptItem(String prefix, String comment, Long keyCount) {
    }
}
