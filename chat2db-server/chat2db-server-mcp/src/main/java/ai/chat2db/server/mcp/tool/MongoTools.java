package ai.chat2db.server.mcp.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.McpApprovalSupport;
import ai.chat2db.server.mcp.support.MongoToolExecutionSupport;
import ai.chat2db.server.mcp.support.MqlGuard;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.tools.common.util.I18nUtils;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * MongoDB MCP tools. Queries accept native MQL; all mutations use structured parameters and confirmation.
 */
@Service
public class MongoTools {

    private final ConnectionScope connectionScope;
    private final DatabaseService databaseService;
    private final MongoToolExecutionSupport executionSupport;
    private final MqlGuard mqlGuard;
    private final McpApprovalSupport approvalSupport;
    private final ResultFormatter resultFormatter;

    public MongoTools(ConnectionScope connectionScope, @Lazy DatabaseService databaseService,
                      MongoToolExecutionSupport executionSupport,
                      MqlGuard mqlGuard, McpApprovalSupport approvalSupport,
                      ResultFormatter resultFormatter) {
        this.connectionScope = connectionScope;
        this.databaseService = databaseService;
        this.executionSupport = executionSupport;
        this.mqlGuard = mqlGuard;
        this.approvalSupport = approvalSupport;
        this.resultFormatter = resultFormatter;
    }

    @McpTool(description = "Execute one read-only MongoDB MQL query. Allows find/findOne/countDocuments/aggregate; "
        + "rejects writes, db.runCommand, multiple statements, and $out/$merge aggregation stages.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String executeMongoQuery(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "MongoDB database name") String databaseName,
        @McpToolParam(description = "One native read-only MQL statement beginning with db.") String mql,
        @McpToolParam(description = "Page number, default 1", required = false) Integer pageNo,
        @McpToolParam(description = "Page size, default 100, capped by server config", required = false)
        Integer pageSize) {
        return connectionScope.run(datasourceUri, requireDatabaseName(databaseName), null, resolvedDataSource -> {
            mqlGuard.requireMongoDatasource(resolvedDataSource);
            String statement = mqlGuard.requireSingleReadOnlyMql(mql);
            return resultFormatter.formatCsv(executionSupport.execute(resolvedDataSource.getDataSourceId(),
                databaseName, statement, pageNo == null ? 1 : pageNo, mqlGuard.normalizePageSize(pageSize)));
        });
    }

    @McpTool(description = "Get MongoDB collection structures as MongoDB-style JSON Schema inferred from sampled "
        + "documents. Omit collectionNames to inspect all collections. Read-only metadata operation.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String getMongoCollectionSchema(
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "MongoDB database name") String databaseName,
        @McpToolParam(description = "Optional collection names; omit to inspect all collections", required = false)
        List<String> collectionNames) {
        return connectionScope.run(datasourceUri, requireDatabaseName(databaseName), null, resolvedDataSource -> {
            mqlGuard.requireMongoDatasource(resolvedDataSource);
            return databaseService.queryMongoSchema(resolvedDataSource.getDataSourceId(), databaseName,
                collectionNames);
        });
    }

    @McpTool(description = "DESTRUCTIVE MONGODB WRITE: Execute one structured MongoDB document write after "
        + "client approval. Shows the exact MQL and rechecks permissions and limits before execution.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String executeMongoWrite(McpSyncServerExchange exchange,
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "MongoDB database name") String databaseName,
        @McpToolParam(description = "INSERT_ONE, INSERT_MANY, UPDATE_ONE, UPDATE_MANY, REPLACE_ONE, "
            + "DELETE_ONE, or DELETE_MANY") String operation,
        @McpToolParam(description = "Target collection") String collection,
        @McpToolParam(description = "Structured MongoDB filter; required and non-empty for update/replace/delete",
            required = false) Map<String, Object> filter,
        @McpToolParam(description = "Structured document for INSERT_ONE or REPLACE_ONE", required = false)
        Map<String, Object> document,
        @McpToolParam(description = "Structured documents for INSERT_MANY", required = false)
        List<Map<String, Object>> documents,
        @McpToolParam(description = "Structured update-operator document for UPDATE_ONE or UPDATE_MANY",
            required = false) Map<String, Object> update,
        @McpToolParam(description = "Required positive upper bound for UPDATE_MANY and DELETE_MANY",
            required = false) Integer maxAffectedDocuments) {
        WriteOperation writeOperation = enumValue(WriteOperation.class, operation,
            "Unsupported MongoDB write operation: ");
        return connectionScope.run(datasourceUri, requireDatabaseName(databaseName), null, resolvedDataSource -> {
            mqlGuard.requireMongoDatasource(resolvedDataSource);
            mqlGuard.requireWriteAllowed(resolvedDataSource.getDataSourceId());
            // 确认等待期间不再读取调用方可变参数，确保用户看到的 MQL 与实际执行内容一致。
            Map<String, Object> filterSnapshot = snapshotDocument(filter);
            Map<String, Object> documentSnapshot = snapshotDocument(document);
            List<Map<String, Object>> documentsSnapshot = documents == null ? null
                : documents.stream().map(this::snapshotDocument).toList();
            Map<String, Object> updateSnapshot = snapshotDocument(update);
            ValidatedInput input = validateWriteInput(writeOperation, collection, filterSnapshot, documentSnapshot,
                documentsSnapshot, updateSnapshot, maxAffectedDocuments);
            Long estimatedMatched = input.countMql() == null ? null
                : executionSupport.count(resolvedDataSource.getDataSourceId(), databaseName, input.countMql());
            if (input.isMulti() && estimatedMatched > input.maxAffectedDocuments()) {
                throw new IllegalArgumentException("Matched documents " + estimatedMatched
                    + " exceed maxAffectedDocuments " + input.maxAffectedDocuments() + ".");
            }
            String target = resolvedDataSource.getSummary() + "/" + databaseName + "." + collection;
            var approval = approvalSupport.confirm(exchange, "MONGODB " + writeOperation.name(), target,
                formatChangePreview(input, estimatedMatched));
            if (!approval.approved()) {
                return approval.message();
            }
            // 用户确认不冻结运行时权限与批量限制，执行前必须重新检查。
            mqlGuard.requireMongoDatasource(resolvedDataSource);
            mqlGuard.requireWriteAllowed(resolvedDataSource.getDataSourceId());
            validateWriteInput(writeOperation, collection, filterSnapshot, documentSnapshot, documentsSnapshot,
                updateSnapshot, maxAffectedDocuments);
            recheckMultiDocumentLimit(input, resolvedDataSource.getDataSourceId(), databaseName);
            return resultFormatter.format(executionSupport.execute(resolvedDataSource.getDataSourceId(),
                databaseName, input.mql(), 1, 100));
        });
    }

    @McpTool(description = "DESTRUCTIVE MONGODB ADMIN: Execute one structured MongoDB collection, index or "
        + "database change after client approval. Shows the exact MQL and rechecks permissions before execution.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String executeMongoAdmin(McpSyncServerExchange exchange,
        @McpToolParam(description = "Datasource resource URI, e.g. chat2db://datasources/123") String datasourceUri,
        @McpToolParam(description = "MongoDB database name") String databaseName,
        @McpToolParam(description = "CREATE_COLLECTION, CREATE_INDEX, DROP_INDEX, DROP_COLLECTION, or "
            + "DROP_DATABASE") String operation,
        @McpToolParam(description = "Collection required except for DROP_DATABASE", required = false) String collection,
        @McpToolParam(description = "Index name required for DROP_INDEX", required = false) String indexName,
        @McpToolParam(description = "Structured index key specification required for CREATE_INDEX", required = false)
        Map<String, Object> indexKeys) {
        AdminOperation adminOperation = enumValue(AdminOperation.class, operation,
            "Unsupported MongoDB admin operation: ");
        return connectionScope.run(datasourceUri, requireDatabaseName(databaseName), null, resolvedDataSource -> {
            mqlGuard.requireMongoDatasource(resolvedDataSource);
            mqlGuard.requireAdminAllowed(resolvedDataSource.getDataSourceId());
            Map<String, Object> indexKeysSnapshot = snapshotDocument(indexKeys);
            ValidatedInput input = validateAdminInput(adminOperation, collection, indexName, indexKeysSnapshot);
            String target = resolvedDataSource.getSummary() + "/" + databaseName
                + (StringUtils.isBlank(collection) ? "" : "." + collection);
            var approval = approvalSupport.confirm(exchange, "MONGODB " + adminOperation.name(), target,
                formatChangePreview(input, null));
            if (!approval.approved()) {
                return approval.message();
            }
            mqlGuard.requireMongoDatasource(resolvedDataSource);
            mqlGuard.requireAdminAllowed(resolvedDataSource.getDataSourceId());
            validateAdminInput(adminOperation, collection, indexName, indexKeysSnapshot);
            return resultFormatter.format(executionSupport.execute(resolvedDataSource.getDataSourceId(),
                databaseName, input.mql(), 1, 100));
        });
    }

    private ValidatedInput validateWriteInput(WriteOperation operation, String collection,
                                            Map<String, Object> filter, Map<String, Object> document,
                                            List<Map<String, Object>> documents, Map<String, Object> update,
                                            Integer maxAffectedDocuments) {
        String reference = executionSupport.collectionReference(collection);
        return switch (operation) {
            case INSERT_ONE -> {
                rejectPresent("filter", filter);
                rejectPresent("documents", documents);
                rejectPresent("update", update);
                rejectPresent("maxAffectedDocuments", maxAffectedDocuments);
                requireDocument(document, "document");
                yield new ValidatedInput(reference + ".insertOne(" + executionSupport.json(document) + ")",
                    null, 1, false, "HIGH");
            }
            case INSERT_MANY -> {
                rejectPresent("filter", filter);
                rejectPresent("document", document);
                rejectPresent("update", update);
                rejectPresent("maxAffectedDocuments", maxAffectedDocuments);
                if (CollectionUtils.isEmpty(documents)) {
                    throw new IllegalArgumentException("documents must not be empty for INSERT_MANY.");
                }
                if (documents.size() > mqlGuard.maxBatchDocuments()) {
                    throw new IllegalArgumentException("INSERT_MANY exceeds max-batch-documents "
                        + mqlGuard.maxBatchDocuments() + ".");
                }
                documents.forEach(value -> requireDocument(value, "documents item"));
                yield new ValidatedInput(reference + ".insertMany(" + executionSupport.json(documents) + ")",
                    null, documents.size(), false, "HIGH");
            }
            case UPDATE_ONE -> {
                rejectPresent("document", document);
                rejectPresent("documents", documents);
                rejectPresent("maxAffectedDocuments", maxAffectedDocuments);
                mqlGuard.requireNonEmptyFilter(filter);
                mqlGuard.requireUpdateDocument(update);
                yield new ValidatedInput(reference + ".updateOne(" + executionSupport.json(filter) + ","
                    + executionSupport.json(update) + ")", executionSupport.countMql(collection, filter),
                    1, false, "HIGH");
            }
            case UPDATE_MANY -> {
                rejectPresent("document", document);
                rejectPresent("documents", documents);
                mqlGuard.requireNonEmptyFilter(filter);
                mqlGuard.requireUpdateDocument(update);
                int maximum = requirePositiveMaximum(maxAffectedDocuments, operation);
                yield new ValidatedInput(reference + ".updateMany(" + executionSupport.json(filter) + ","
                    + executionSupport.json(update) + ")", executionSupport.countMql(collection, filter),
                    maximum, true, "CRITICAL");
            }
            case REPLACE_ONE -> {
                rejectPresent("documents", documents);
                rejectPresent("update", update);
                rejectPresent("maxAffectedDocuments", maxAffectedDocuments);
                mqlGuard.requireNonEmptyFilter(filter);
                mqlGuard.requireReplacementDocument(document);
                yield new ValidatedInput(reference + ".replaceOne(" + executionSupport.json(filter) + ","
                    + executionSupport.json(document) + ")", executionSupport.countMql(collection, filter),
                    1, false, "HIGH");
            }
            case DELETE_ONE -> {
                rejectPresent("document", document);
                rejectPresent("documents", documents);
                rejectPresent("update", update);
                rejectPresent("maxAffectedDocuments", maxAffectedDocuments);
                mqlGuard.requireNonEmptyFilter(filter);
                yield new ValidatedInput(reference + ".deleteOne(" + executionSupport.json(filter) + ")",
                    executionSupport.countMql(collection, filter), 1, false, "HIGH");
            }
            case DELETE_MANY -> {
                rejectPresent("document", document);
                rejectPresent("documents", documents);
                rejectPresent("update", update);
                mqlGuard.requireNonEmptyFilter(filter);
                int maximum = requirePositiveMaximum(maxAffectedDocuments, operation);
                yield new ValidatedInput(reference + ".deleteMany(" + executionSupport.json(filter) + ")",
                    executionSupport.countMql(collection, filter), maximum, true, "CRITICAL");
            }
        };
    }

    private ValidatedInput validateAdminInput(AdminOperation operation, String collection, String indexName,
                                            Map<String, Object> indexKeys) {
        return switch (operation) {
            case CREATE_COLLECTION -> {
                rejectPresent("indexName", indexName);
                rejectPresent("indexKeys", indexKeys);
                requireCollection(collection);
                yield new ValidatedInput("db.createCollection(" + executionSupport.json(collection) + ")",
                    null, null, false, "HIGH");
            }
            case CREATE_INDEX -> {
                rejectPresent("indexName", indexName);
                requireCollection(collection);
                requireDocument(indexKeys, "indexKeys");
                yield new ValidatedInput(executionSupport.collectionReference(collection) + ".createIndex("
                    + executionSupport.json(indexKeys) + ")", null, null, false, "HIGH");
            }
            case DROP_INDEX -> {
                rejectPresent("indexKeys", indexKeys);
                requireCollection(collection);
                if (StringUtils.isBlank(indexName)) {
                    throw new IllegalArgumentException("indexName is required for DROP_INDEX.");
                }
                yield new ValidatedInput(executionSupport.collectionReference(collection) + ".dropIndex("
                    + executionSupport.json(indexName) + ")", null, null, false, "CRITICAL");
            }
            case DROP_COLLECTION -> {
                rejectPresent("indexName", indexName);
                rejectPresent("indexKeys", indexKeys);
                requireCollection(collection);
                yield new ValidatedInput(executionSupport.collectionReference(collection) + ".drop()",
                    null, null, false, "CRITICAL");
            }
            case DROP_DATABASE -> {
                rejectPresent("collection", collection);
                rejectPresent("indexName", indexName);
                rejectPresent("indexKeys", indexKeys);
                yield new ValidatedInput("db.dropDatabase()", null, null, false, "CRITICAL");
            }
        };
    }

    private void recheckMultiDocumentLimit(ValidatedInput input, Long dataSourceId, String databaseName) {
        if (!input.isMulti()) {
            return;
        }
        long matched = executionSupport.count(dataSourceId, databaseName, input.countMql());
        if (matched > input.maxAffectedDocuments()) {
            throw new IllegalStateException(I18nUtils.getMessage("mcp.mongo.maxAffectedExceededAfterApproval",
                new Object[] {matched, input.maxAffectedDocuments()}));
        }
    }

    private String formatChangePreview(ValidatedInput input, Long estimatedMatchedDocuments) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("riskLevel", input.riskLevel());
        metadata.put("estimatedMatchedDocuments", estimatedMatchedDocuments);
        metadata.put("maxAffectedDocuments", input.maxAffectedDocuments());
        return executionSupport.json(metadata) + "\n" + input.mql();
    }

    private Map<String, Object> snapshotDocument(Map<String, Object> document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        document.forEach((key, value) -> copy.put(key, snapshotValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private Object snapshotValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), snapshotValue(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(snapshotValue(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    private int requirePositiveMaximum(Integer maximum, WriteOperation operation) {
        if (maximum == null || maximum <= 0) {
            throw new IllegalArgumentException("maxAffectedDocuments must be positive for " + operation + ".");
        }
        return maximum;
    }

    private void requireDocument(Map<String, Object> document, String name) {
        if (document == null || document.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty.");
        }
    }

    private void requireCollection(String collection) {
        if (StringUtils.isBlank(collection)) {
            throw new IllegalArgumentException("collection is required for this MongoDB admin operation.");
        }
    }

    private void rejectPresent(String parameterName, Object value) {
        if (value != null) {
            throw new IllegalArgumentException(parameterName + " is not supported for this MongoDB operation.");
        }
    }

    private String requireDatabaseName(String databaseName) {
        if (StringUtils.isBlank(databaseName)) {
            throw new IllegalArgumentException("databaseName must not be blank.");
        }
        return databaseName;
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, String messagePrefix) {
        try {
            return Enum.valueOf(type, StringUtils.upperCase(StringUtils.trim(value), Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(messagePrefix + value, e);
        }
    }

    private enum WriteOperation {
        INSERT_ONE,
        INSERT_MANY,
        UPDATE_ONE,
        UPDATE_MANY,
        REPLACE_ONE,
        DELETE_ONE,
        DELETE_MANY
    }

    private enum AdminOperation {
        CREATE_COLLECTION,
        CREATE_INDEX,
        DROP_INDEX,
        DROP_COLLECTION,
        DROP_DATABASE
    }

    private record ValidatedInput(String mql, String countMql, Integer maxAffectedDocuments, boolean isMulti,
                                 String riskLevel) {
    }
}
