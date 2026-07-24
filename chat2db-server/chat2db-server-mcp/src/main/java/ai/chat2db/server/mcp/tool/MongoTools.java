package ai.chat2db.server.mcp.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.MongoToolExecutionSupport;
import ai.chat2db.server.mcp.support.MqlGuard;
import ai.chat2db.server.mcp.support.PreparedMongoChangeStore;
import ai.chat2db.server.mcp.support.PreparedMongoChangeStore.PreparedMongoChange;
import ai.chat2db.server.mcp.support.ResultFormatter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MongoDB MCP tools. Queries accept native MQL; all mutations use structured parameters and confirmation.
 */
@Service
public class MongoTools {

    private static final String CATEGORY_WRITE = "WRITE";
    private static final String CATEGORY_ADMIN = "ADMIN";

    private final ConnectionScope connectionScope;
    private final DatabaseService databaseService;
    private final MongoToolExecutionSupport executionSupport;
    private final MqlGuard mqlGuard;
    private final PreparedMongoChangeStore preparedChangeStore;
    private final ResultFormatter resultFormatter;

    public MongoTools(ConnectionScope connectionScope, DatabaseService databaseService,
                      MongoToolExecutionSupport executionSupport,
                      MqlGuard mqlGuard, PreparedMongoChangeStore preparedChangeStore,
                      ResultFormatter resultFormatter) {
        this.connectionScope = connectionScope;
        this.databaseService = databaseService;
        this.executionSupport = executionSupport;
        this.mqlGuard = mqlGuard;
        this.preparedChangeStore = preparedChangeStore;
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

    @McpTool(description = "DESTRUCTIVE MONGODB PREPARE: Prepare one structured MongoDB document write. "
        + "This does not modify MongoDB. Returns an operationId and exact confirmationText for "
        + "executePreparedMongoChange.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String prepareMongoWriteChange(
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
            PreparedInput input = prepareWriteInput(writeOperation, collection, filter, document, documents, update,
                maxAffectedDocuments);
            Long estimatedMatched = input.countMql() == null ? null
                : executionSupport.count(resolvedDataSource.getDataSourceId(), databaseName, input.countMql());
            if (input.isMulti() && estimatedMatched > input.maxAffectedDocuments()) {
                throw new IllegalArgumentException("Matched documents " + estimatedMatched
                    + " exceed maxAffectedDocuments " + input.maxAffectedDocuments() + ".");
            }
            PreparedMongoChange change = preparedChangeStore.prepare(CATEGORY_WRITE, writeOperation.name(),
                datasourceUri, resolvedDataSource, databaseName, collection, input.mql(), input.countMql(),
                estimatedMatched, input.maxAffectedDocuments(), input.riskLevel());
            return formatPreparedChange(change);
        });
    }

    @McpTool(description = "DESTRUCTIVE MONGODB ADMIN PREPARE: Prepare one structured MongoDB collection or index "
        + "change. This does not modify MongoDB. Returns an operationId and exact confirmationText for "
        + "executePreparedMongoChange.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String prepareMongoAdminChange(
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
            PreparedInput input = prepareAdminInput(adminOperation, collection, indexName, indexKeys);
            PreparedMongoChange change = preparedChangeStore.prepare(CATEGORY_ADMIN, adminOperation.name(),
                datasourceUri, resolvedDataSource, databaseName, collection, input.mql(), null, null, null,
                input.riskLevel());
            return formatPreparedChange(change);
        });
    }

    @McpTool(description = "DESTRUCTIVE MONGODB EXECUTE: Execute a previously prepared MongoDB write or admin "
        + "change. Only operationId and the exact confirmationText returned by prepare are accepted.",
        annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String executePreparedMongoChange(
        @McpToolParam(description = "operationId returned by prepareMongoWriteChange or prepareMongoAdminChange")
        String operationId,
        @McpToolParam(description = "Exact confirmationText returned by the MongoDB prepare tool")
        String confirmationText) {
        PreparedMongoChange change = preparedChangeStore.consume(operationId, confirmationText);
        return connectionScope.run(change.getDatasourceUri(), change.getDatabaseName(), null, resolvedDataSource -> {
            mqlGuard.requireMongoDatasource(resolvedDataSource);
            if (CATEGORY_WRITE.equals(change.getCategory())) {
                mqlGuard.requireWriteAllowed(resolvedDataSource.getDataSourceId());
                recheckMultiDocumentLimit(change, resolvedDataSource.getDataSourceId());
            } else if (CATEGORY_ADMIN.equals(change.getCategory())) {
                mqlGuard.requireAdminAllowed(resolvedDataSource.getDataSourceId());
            } else {
                throw new IllegalStateException("Unsupported prepared MongoDB change category.");
            }
            return resultFormatter.format(executionSupport.execute(resolvedDataSource.getDataSourceId(),
                change.getDatabaseName(), change.getMql(), 1, 100));
        });
    }

    private PreparedInput prepareWriteInput(WriteOperation operation, String collection,
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
                yield new PreparedInput(reference + ".insertOne(" + executionSupport.json(document) + ")",
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
                yield new PreparedInput(reference + ".insertMany(" + executionSupport.json(documents) + ")",
                    null, documents.size(), false, "HIGH");
            }
            case UPDATE_ONE -> {
                rejectPresent("document", document);
                rejectPresent("documents", documents);
                rejectPresent("maxAffectedDocuments", maxAffectedDocuments);
                mqlGuard.requireNonEmptyFilter(filter);
                mqlGuard.requireUpdateDocument(update);
                yield new PreparedInput(reference + ".updateOne(" + executionSupport.json(filter) + ","
                    + executionSupport.json(update) + ")", executionSupport.countMql(collection, filter),
                    1, false, "HIGH");
            }
            case UPDATE_MANY -> {
                rejectPresent("document", document);
                rejectPresent("documents", documents);
                mqlGuard.requireNonEmptyFilter(filter);
                mqlGuard.requireUpdateDocument(update);
                int maximum = requirePositiveMaximum(maxAffectedDocuments, operation);
                yield new PreparedInput(reference + ".updateMany(" + executionSupport.json(filter) + ","
                    + executionSupport.json(update) + ")", executionSupport.countMql(collection, filter),
                    maximum, true, "CRITICAL");
            }
            case REPLACE_ONE -> {
                rejectPresent("documents", documents);
                rejectPresent("update", update);
                rejectPresent("maxAffectedDocuments", maxAffectedDocuments);
                mqlGuard.requireNonEmptyFilter(filter);
                mqlGuard.requireReplacementDocument(document);
                yield new PreparedInput(reference + ".replaceOne(" + executionSupport.json(filter) + ","
                    + executionSupport.json(document) + ")", executionSupport.countMql(collection, filter),
                    1, false, "HIGH");
            }
            case DELETE_ONE -> {
                rejectPresent("document", document);
                rejectPresent("documents", documents);
                rejectPresent("update", update);
                rejectPresent("maxAffectedDocuments", maxAffectedDocuments);
                mqlGuard.requireNonEmptyFilter(filter);
                yield new PreparedInput(reference + ".deleteOne(" + executionSupport.json(filter) + ")",
                    executionSupport.countMql(collection, filter), 1, false, "HIGH");
            }
            case DELETE_MANY -> {
                rejectPresent("document", document);
                rejectPresent("documents", documents);
                rejectPresent("update", update);
                mqlGuard.requireNonEmptyFilter(filter);
                int maximum = requirePositiveMaximum(maxAffectedDocuments, operation);
                yield new PreparedInput(reference + ".deleteMany(" + executionSupport.json(filter) + ")",
                    executionSupport.countMql(collection, filter), maximum, true, "CRITICAL");
            }
        };
    }

    private PreparedInput prepareAdminInput(AdminOperation operation, String collection, String indexName,
                                            Map<String, Object> indexKeys) {
        return switch (operation) {
            case CREATE_COLLECTION -> {
                rejectPresent("indexName", indexName);
                rejectPresent("indexKeys", indexKeys);
                requireCollection(collection);
                yield new PreparedInput("db.createCollection(" + executionSupport.json(collection) + ")",
                    null, null, false, "HIGH");
            }
            case CREATE_INDEX -> {
                rejectPresent("indexName", indexName);
                requireCollection(collection);
                requireDocument(indexKeys, "indexKeys");
                yield new PreparedInput(executionSupport.collectionReference(collection) + ".createIndex("
                    + executionSupport.json(indexKeys) + ")", null, null, false, "HIGH");
            }
            case DROP_INDEX -> {
                rejectPresent("indexKeys", indexKeys);
                requireCollection(collection);
                if (StringUtils.isBlank(indexName)) {
                    throw new IllegalArgumentException("indexName is required for DROP_INDEX.");
                }
                yield new PreparedInput(executionSupport.collectionReference(collection) + ".dropIndex("
                    + executionSupport.json(indexName) + ")", null, null, false, "CRITICAL");
            }
            case DROP_COLLECTION -> {
                rejectPresent("indexName", indexName);
                rejectPresent("indexKeys", indexKeys);
                requireCollection(collection);
                yield new PreparedInput(executionSupport.collectionReference(collection) + ".drop()",
                    null, null, false, "CRITICAL");
            }
            case DROP_DATABASE -> {
                rejectPresent("collection", collection);
                rejectPresent("indexName", indexName);
                rejectPresent("indexKeys", indexKeys);
                yield new PreparedInput("db.dropDatabase()", null, null, false, "CRITICAL");
            }
        };
    }

    private void recheckMultiDocumentLimit(PreparedMongoChange change, Long dataSourceId) {
        if (StringUtils.isBlank(change.getCountMql()) || change.getMaxAffectedDocuments() == null
            || !("UPDATE_MANY".equals(change.getOperation()) || "DELETE_MANY".equals(change.getOperation()))) {
            return;
        }
        long matched = executionSupport.count(dataSourceId, change.getDatabaseName(), change.getCountMql());
        if (matched > change.getMaxAffectedDocuments()) {
            throw new IllegalStateException("Matched documents " + matched + " now exceed maxAffectedDocuments "
                + change.getMaxAffectedDocuments() + ". Prepare the MongoDB change again.");
        }
    }

    private String formatPreparedChange(PreparedMongoChange change) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operationId", change.getOperationId());
        response.put("operationType", change.getOperation());
        response.put("riskLevel", change.getRiskLevel());
        response.put("target", change.getTarget());
        response.put("canonicalPreview", change.getMql());
        response.put("estimatedMatchedDocuments", change.getEstimatedMatchedDocuments());
        response.put("maxAffectedDocuments", change.getMaxAffectedDocuments());
        response.put("digest", "sha256:" + change.getDigest());
        response.put("expiresAtEpochSecond", change.getExpiresAt());
        response.put("confirmationText", change.getConfirmationText());
        return executionSupport.json(response);
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

    private record PreparedInput(String mql, String countMql, Integer maxAffectedDocuments, boolean isMulti,
                                 String riskLevel) {
    }
}
