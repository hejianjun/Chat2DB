package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import ai.chat2db.server.domain.core.cache.LuceneIndexManager;
import ai.chat2db.server.domain.core.cache.LuceneIndexManagerFactory;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SaveAiCommentAction extends BaseChatAction {

    @Autowired
    private LuceneIndexManagerFactory managerFactory;

    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        log.info("[SaveAiCommentAction] execute called for uid: {}", ctx.getUid());

        String promptType = ctx.getRequest().getPromptType();
        if (!PromptType.NL_2_COMMENT.getCode().equals(promptType)
                && !PromptType.NL_2_COMMENT_BATCH.getCode().equals(promptType)) {
            log.debug("[SaveAiCommentAction] Not comment type, skip");
            return;
        }

        String content = ctx.getCurrentContent();
        if (StringUtils.isBlank(content)) {
            log.warn("[SaveAiCommentAction] No content to save for uid: {}", ctx.getUid());
            return;
        }

        Long dataSourceId = ctx.getRequest().getDataSourceId();
        String databaseName = ctx.getRequest().getDatabaseName();
        String schemaName = ctx.getRequest().getSchemaName();

        try {
            if (PromptType.NL_2_COMMENT_BATCH.getCode().equals(promptType)) {
                executeBatch(ctx, dataSourceId, databaseName, schemaName, content);
            } else {
                executeSingle(ctx, dataSourceId, databaseName, schemaName, content);
            }
        } catch (Exception e) {
            log.error("[SaveAiCommentAction] Failed to save aiComment for uid: {}", ctx.getUid(), e);
        }
    }

    private void executeSingle(ChatContext ctx, Long dataSourceId, String databaseName,
                               String schemaName, String content) {
        AiCommentResult result = parseCommentResult(content);
        if (result == null) {
            log.warn("[SaveAiCommentAction] Failed to parse comment result for uid: {}", ctx.getUid());
            return;
        }

        List<String> tableNames = ctx.getRequest().getTableNames();
        if (CollectionUtils.isEmpty(tableNames)) {
            log.warn("[SaveAiCommentAction] No table names for uid: {}", ctx.getUid());
            return;
        }

        String tableName = tableNames.get(0);

        if (StringUtils.isNotBlank(result.getTableComment())) {
            saveTableAiComment(dataSourceId, databaseName, schemaName, tableName, result.getTableComment());
        }

        if (CollectionUtils.isNotEmpty(result.getColumnComments())) {
            saveColumnAiComments(dataSourceId, databaseName, schemaName, tableName, result.getColumnComments());
        }

        log.info("[SaveAiCommentAction] Successfully saved aiComment for uid: {}, table: {}", ctx.getUid(), tableName);
    }

    private void executeBatch(ChatContext ctx, Long dataSourceId, String databaseName,
                              String schemaName, String content) {
        List<BatchTableComment> batchResult = parseBatchCommentResult(content);
        if (CollectionUtils.isEmpty(batchResult)) {
            log.warn("[SaveAiCommentAction] Failed to parse batch comment result for uid: {}", ctx.getUid());
            return;
        }

        saveBatchTableAiComments(dataSourceId, databaseName, schemaName, batchResult);
        log.info("[SaveAiCommentAction] Successfully saved batch aiComment for uid: {}, count: {}",
                ctx.getUid(), batchResult.size());
    }

    private List<BatchTableComment> parseBatchCommentResult(String content) {
        String json = extractBatchJson(content);
        if (StringUtils.isBlank(json)) {
            return null;
        }

        try {
            JSONObject obj = JSONObject.parseObject(json);
            JSONArray tables = obj.getJSONArray("tables");
            if (tables == null || tables.isEmpty()) {
                return null;
            }
            return tables.toJavaList(BatchTableComment.class);
        } catch (Exception e) {
            log.error("[SaveAiCommentAction] Failed to parse batch JSON: {}", content, e);
            return null;
        }
    }

    private String extractBatchJson(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }

        Pattern pattern = Pattern.compile("\\{[\\s\\S]*\"tables\"[\\s\\S]*\\}");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return null;
    }

    private void saveBatchTableAiComments(Long dataSourceId, String databaseName, String schemaName,
                                          List<BatchTableComment> batchComments) {
        for (BatchTableComment btc : batchComments) {
            if (StringUtils.isBlank(btc.getTableName()) || StringUtils.isBlank(btc.getTableComment())) {
                continue;
            }
            saveTableAiComment(dataSourceId, databaseName, schemaName, btc.getTableName(), btc.getTableComment());
        }
    }

    private AiCommentResult parseCommentResult(String content) {
        String json = extractJson(content);
        if (StringUtils.isBlank(json)) {
            return null;
        }

        try {
            return JSONObject.parseObject(json, AiCommentResult.class);
        } catch (Exception e) {
            log.error("[SaveAiCommentAction] Failed to parse JSON: {}", content, e);
            return null;
        }
    }

    private String extractJson(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }

        Pattern pattern = Pattern.compile("\\{[\\s\\S]*\"table_comment\"[\\s\\S]*\"column_comments\"[\\s\\S]*\\}");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return null;
    }

    private void saveTableAiComment(Long dataSourceId, String databaseName, String schemaName,
                                    String tableName, String aiComment) {
        try {
            LuceneIndexManager<Table> manager = managerFactory.getManager(dataSourceId);

            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(tableName);
            table.setAiComment(aiComment);

            manager.updateDocument(table);
            log.info("[SaveAiCommentAction] Saved table aiComment: {}.{}", tableName, aiComment);
        } catch (Exception e) {
            log.error("[SaveAiCommentAction] Failed to save table aiComment: {}", tableName, e);
        }
    }

    private void saveColumnAiComments(Long dataSourceId, String databaseName, String schemaName,
                                      String tableName, List<ColumnComment> columnComments) {
        try {
            LuceneIndexManager<TableColumn> manager = managerFactory.getManager(dataSourceId);

            for (ColumnComment col : columnComments) {
                if (StringUtils.isBlank(col.getColumnName()) || StringUtils.isBlank(col.getComment())) {
                    continue;
                }

                TableColumn column = new TableColumn();
                column.setDatabaseName(databaseName);
                column.setSchemaName(schemaName);
                column.setTableName(tableName);
                column.setName(col.getColumnName());
                column.setAiComment(col.getComment());

                manager.updateDocument(column);
                log.info("[SaveAiCommentAction] Saved column aiComment: {}.{} = {}",
                        tableName, col.getColumnName(), col.getComment());
            }
        } catch (Exception e) {
            log.error("[SaveAiCommentAction] Failed to save column aiComments", e);
        }
    }

    @Data
    private static class AiCommentResult {
        @JSONField(name = "table_comment")
        private String tableComment;
        @JSONField(name = "column_comments")
        private List<ColumnComment> columnComments;
    }

    @Data
    private static class ColumnComment {
        @JSONField(name = "column_name")
        private String columnName;
        @JSONField(name = "comment")
        private String comment;
    }

    @Data
    private static class BatchTableComment {
        @JSONField(name = "table_name")
        private String tableName;
        @JSONField(name = "table_comment")
        private String tableComment;
    }
}