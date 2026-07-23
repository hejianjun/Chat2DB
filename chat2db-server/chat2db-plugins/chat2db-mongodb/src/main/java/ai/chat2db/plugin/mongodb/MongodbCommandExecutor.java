package ai.chat2db.plugin.mongodb;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.ValueHandler;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.enums.SqlTypeEnum;
import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import ai.chat2db.spi.sql.Chat2DBContext;
import cn.hutool.core.date.TimeInterval;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class MongodbCommandExecutor implements CommandExecutor {

    private static final int DEFAULT_PAGE_SIZE = 200;

    @Override
    public List<ExecuteResult> execute(Command command) {
        List<ExecuteResult> results = new ArrayList<>();
        List<MongodbCommandParser.StatementPosition> statements =
                MongodbCommandParser.splitStatements(command.getScript());
        try (MongodbConnectionProvider.MongodbConnectionContext context =
                     MongodbConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            String databaseName = StringUtils.defaultIfBlank(command.getDatabaseName(),
                    MongodbConnectionProvider.databaseName(Chat2DBContext.getConnectInfo(),
                            MongodbConnectionProvider.parse(Chat2DBContext.getConnectInfo())));
            for (int i = 0; i < statements.size(); i++) {
                MongodbCommandParser.StatementPosition statement = statements.get(i);
                ExecuteResult result = executeStatement(context.client().getDatabase(databaseName),
                        statement.statement(), command.getPageNo(), command.getPageSize());
                result.setStatementIndex(i + 1);
                result.setStatementStartLine(absoluteLine(command.getScriptStartLine(), statement.startLine()));
                result.setStatementEndLine(absoluteLine(command.getScriptStartLine(), statement.endLine()));
                result.setOriginalSql(statement.statement());
                results.add(result);
            }
        }
        return results;
    }

    @Override
    public ExecuteResult executeUpdate(String sql, Connection connection, int n) throws SQLException {
        return execute(sql, connection, true, null, null, null);
    }

    @Override
    public ExecuteResult execute(String sql, Connection connection, boolean limitRowSize, Integer offset, Integer count,
                                 ValueHandler valueHandler) throws SQLException {
        try (MongodbConnectionProvider.MongodbConnectionContext context =
                     MongodbConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            String databaseName = MongodbConnectionProvider.databaseName(Chat2DBContext.getConnectInfo(),
                    MongodbConnectionProvider.parse(Chat2DBContext.getConnectInfo()));
            int pageSize = count == null ? DEFAULT_PAGE_SIZE : count;
            int pageNo = offset == null || pageSize < 1 ? 1 : offset / pageSize + 1;
            return executeStatement(context.client().getDatabase(databaseName), sql, pageNo, pageSize);
        }
    }

    private ExecuteResult executeStatement(MongoDatabase database, String statement, Integer pageNo,
                                           Integer pageSize) {
        TimeInterval timer = new TimeInterval();
        ExecuteResult result = ExecuteResult.builder()
                .sql(statement)
                .success(Boolean.TRUE)
                .description("Success")
                .sqlType(SqlTypeEnum.UNKNOWN.getCode())
                .build();
        try {
            executeExpression(database, statement.trim(), pageNo, pageSize, result);
        } catch (Exception e) {
            result.setSuccess(Boolean.FALSE);
            result.setMessage(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        } finally {
            result.setDuration(timer.interval());
        }
        return result;
    }

    private void executeExpression(MongoDatabase database, String statement, Integer pageNo, Integer pageSize,
                                   ExecuteResult result) {
        if (!statement.startsWith("db.")) {
            throw new IllegalArgumentException("MQL must start with db.");
        }
        String expression = statement.substring(3).trim();
        if (expression.startsWith("createCollection(") || expression.startsWith("dropDatabase(")
                || expression.startsWith("runCommand(")) {
            executeDatabaseMethod(database, MongodbCommandParser.parseMethodCalls(expression).get(0), result);
            return;
        }

        String collectionName;
        String methodExpression;
        if (expression.startsWith("getCollection(")) {
            List<MongodbCommandParser.MethodCall> calls = MongodbCommandParser.parseMethodCalls(expression);
            MongodbCommandParser.MethodCall selector = calls.remove(0);
            collectionName = requiredString(selector.arguments(), 0, "collection");
            executeCollectionMethods(database.getCollection(collectionName), calls, pageNo, pageSize, result);
            return;
        }
        int dot = expression.indexOf('.');
        if (dot <= 0) {
            throw new IllegalArgumentException("MQL must include a collection and method");
        }
        collectionName = expression.substring(0, dot).trim();
        methodExpression = expression.substring(dot + 1);
        executeCollectionMethods(database.getCollection(collectionName),
                MongodbCommandParser.parseMethodCalls(methodExpression), pageNo, pageSize, result);
    }

    private void executeDatabaseMethod(MongoDatabase database, MongodbCommandParser.MethodCall call,
                                       ExecuteResult result) {
        switch (call.name()) {
            case "createCollection" -> {
                database.createCollection(requiredString(call.arguments(), 0, "collection"));
                updateResult(result, 1);
            }
            case "dropDatabase" -> {
                database.drop();
                updateResult(result, 1);
            }
            case "runCommand" -> commandResult(result,
                    database.runCommand(document(call.arguments(), 0)));
            default -> throw new IllegalArgumentException("Unsupported MongoDB method: " + call.name());
        }
    }

    private void executeCollectionMethods(MongoCollection<Document> collection,
                                          List<MongodbCommandParser.MethodCall> calls, Integer requestedPageNo,
                                          Integer requestedPageSize, ExecuteResult result) {
        if (calls.isEmpty()) {
            throw new IllegalArgumentException("Missing MongoDB collection method");
        }
        MongodbCommandParser.MethodCall call = calls.get(0);
        List<MongodbCommandParser.MethodCall> chain = calls.subList(1, calls.size());
        switch (call.name()) {
            case "find" -> executeFind(collection, call.arguments(), chain, requestedPageNo, requestedPageSize, result);
            case "findOne" -> documentsResult(result,
                    collection.find(optionalDocument(call.arguments(), 0)).limit(1).into(new ArrayList<>()),
                    1, 1, false);
            case "aggregate" -> executeAggregate(collection, call.arguments(), chain, requestedPageNo,
                    requestedPageSize, result);
            case "countDocuments" -> executeCountDocuments(collection, call.arguments(), result);
            case "insertOne" -> insertedResult(result, collection.insertOne(document(call.arguments(), 0)));
            case "insertMany" -> insertedManyResult(result, collection.insertMany(documentList(call.arguments(), 0)));
            case "updateOne" -> updateResult(result, collection.updateOne(document(call.arguments(), 0),
                    document(call.arguments(), 1)));
            case "updateMany" -> updateResult(result, collection.updateMany(document(call.arguments(), 0),
                    document(call.arguments(), 1)));
            case "replaceOne" -> updateResult(result, collection.replaceOne(document(call.arguments(), 0),
                    document(call.arguments(), 1)));
            case "deleteOne" -> deleteResult(result, collection.deleteOne(document(call.arguments(), 0)));
            case "deleteMany" -> deleteResult(result, collection.deleteMany(document(call.arguments(), 0)));
            case "createIndex" -> scalarResult(result, "index", collection.createIndex(document(call.arguments(), 0)));
            case "dropIndex" -> {
                collection.dropIndex(requiredString(call.arguments(), 0, "index"));
                updateResult(result, 1);
            }
            case "drop" -> {
                collection.drop();
                updateResult(result, 1);
            }
            default -> throw new IllegalArgumentException("Unsupported MongoDB collection method: " + call.name());
        }
    }

    private void executeCountDocuments(MongoCollection<Document> collection, List<String> arguments,
                                       ExecuteResult result) {
        Document filter = optionalDocument(arguments, 0);
        log.info("Executing MongoDB countDocuments: namespace={}, filter={}",
                collection.getNamespace().getFullName(), filter.toJson());
        long count = collection.countDocuments(filter);
        log.info("MongoDB countDocuments completed: namespace={}, count={}",
                collection.getNamespace().getFullName(), count);
        scalarResult(result, "count", count);
    }

    private void executeFind(MongoCollection<Document> collection, List<String> arguments,
                             List<MongodbCommandParser.MethodCall> chain, Integer requestedPageNo,
                             Integer requestedPageSize, ExecuteResult result) {
        FindIterable<Document> iterable = collection.find(optionalDocument(arguments, 0));
        if (arguments.size() > 1) {
            iterable.projection(document(arguments, 1));
        }
        Page page = page(chain, requestedPageNo, requestedPageSize);
        for (MongodbCommandParser.MethodCall call : chain) {
            switch (call.name()) {
                case "sort" -> iterable.sort(document(call.arguments(), 0));
                case "projection" -> iterable.projection(document(call.arguments(), 0));
                case "skip", "limit" -> {
                    // applied from normalized page below
                }
                default -> throw new IllegalArgumentException("Unsupported find modifier: " + call.name());
            }
        }
        List<Document> documents = iterable.skip(page.skip()).limit(page.limit() + 1).into(new ArrayList<>());
        boolean hasNext = documents.size() > page.limit();
        if (hasNext) {
            documents.remove(documents.size() - 1);
        }
        documentsResult(result, documents, page.pageNo(), page.limit(), hasNext);
    }

    private void executeAggregate(MongoCollection<Document> collection, List<String> arguments,
                                  List<MongodbCommandParser.MethodCall> chain, Integer requestedPageNo,
                                  Integer requestedPageSize, ExecuteResult result) {
        if (!chain.isEmpty()) {
            throw new IllegalArgumentException("Aggregate modifiers are not supported; use $sort/$skip/$limit stages");
        }
        Page page = page(chain, requestedPageNo, requestedPageSize);
        List<Bson> pipeline = new ArrayList<>(documentList(arguments, 0));
        pipeline.add(new Document("$skip", page.skip()));
        pipeline.add(new Document("$limit", page.limit() + 1));
        AggregateIterable<Document> iterable = collection.aggregate(pipeline);
        List<Document> documents = iterable.into(new ArrayList<>());
        boolean hasNext = documents.size() > page.limit();
        if (hasNext) {
            documents.remove(documents.size() - 1);
        }
        documentsResult(result, documents, page.pageNo(), page.limit(), hasNext);
    }

    private Page page(List<MongodbCommandParser.MethodCall> chain, Integer requestedPageNo, Integer requestedPageSize) {
        int pageNo = requestedPageNo == null || requestedPageNo < 1 ? 1 : requestedPageNo;
        int limit = requestedPageSize == null || requestedPageSize < 1 ? DEFAULT_PAGE_SIZE : requestedPageSize;
        int skip = (pageNo - 1) * limit;
        for (MongodbCommandParser.MethodCall call : chain) {
            if ("skip".equals(call.name())) {
                skip = requiredInt(call.arguments(), 0, "skip");
                pageNo = skip / Math.max(limit, 1) + 1;
            } else if ("limit".equals(call.name())) {
                limit = requiredInt(call.arguments(), 0, "limit");
            }
        }
        if (limit < 1) {
            throw new IllegalArgumentException("MongoDB limit must be greater than zero");
        }
        return new Page(pageNo, skip, limit);
    }

    private void documentsResult(ExecuteResult result, List<Document> documents, int pageNo, int pageSize,
                                 boolean hasNext) {
        Map<String, Header> headers = new LinkedHashMap<>();
        for (Document document : documents) {
            document.forEach((name, value) -> headers.computeIfAbsent(name,
                    key -> Header.builder().name(key).dataType(dataType(value)).build()));
        }
        List<List<String>> rows = new ArrayList<>();
        for (Document document : documents) {
            List<String> row = new ArrayList<>();
            for (String name : headers.keySet()) {
                row.add(formatValue(document.get(name)));
            }
            rows.add(row);
        }
        result.setHeaderList(new ArrayList<>(headers.values()));
        result.setDataList(rows);
        result.setSqlType(SqlTypeEnum.SELECT.getCode());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setHasNextPage(hasNext);
        result.setFuzzyTotal(hasNext ? rows.size() + "+" : Integer.toString(rows.size()));
    }

    private void commandResult(ExecuteResult result, Document response) {
        Document cursor = response.get("cursor", Document.class);
        if (cursor == null) {
            documentsResult(result, List.of(response), 1, 1, false);
            return;
        }
        Object batchValue = cursor.containsKey("firstBatch") ? cursor.get("firstBatch") : cursor.get("nextBatch");
        if (!(batchValue instanceof List<?> batch)) {
            documentsResult(result, List.of(response), 1, 1, false);
            return;
        }
        List<Document> documents = new ArrayList<>();
        for (Object value : batch) {
            if (value instanceof Document document) {
                documents.add(document);
            } else {
                documents.add(new Document("value", value));
            }
        }
        Number cursorId = cursor.get("id", Number.class);
        boolean hasNext = cursorId != null && cursorId.longValue() != 0L;
        documentsResult(result, documents, 1, documents.size(), hasNext);
    }

    private void scalarResult(ExecuteResult result, String name, Object value) {
        result.setHeaderList(List.of(Header.builder().name(name).dataType(dataType(value)).build()));
        result.setDataList(List.of(List.of(formatValue(value))));
        result.setSqlType(SqlTypeEnum.SELECT.getCode());
        result.setPageNo(1);
        result.setPageSize(1);
        result.setHasNextPage(false);
        result.setFuzzyTotal("1");
    }

    private void insertedResult(ExecuteResult result, InsertOneResult insertResult) {
        result.setUpdateCount(insertResult.wasAcknowledged() ? 1 : 0);
        result.setSqlType(SqlTypeEnum.UNKNOWN.getCode());
    }

    private void insertedManyResult(ExecuteResult result, InsertManyResult insertResult) {
        result.setUpdateCount(insertResult.wasAcknowledged() ? insertResult.getInsertedIds().size() : 0);
        result.setSqlType(SqlTypeEnum.UNKNOWN.getCode());
    }

    private void updateResult(ExecuteResult result, UpdateResult updateResult) {
        result.setUpdateCount(Math.toIntExact(updateResult.getModifiedCount()));
        result.setSqlType(SqlTypeEnum.UNKNOWN.getCode());
    }

    private void deleteResult(ExecuteResult result, DeleteResult deleteResult) {
        result.setUpdateCount(Math.toIntExact(deleteResult.getDeletedCount()));
        result.setSqlType(SqlTypeEnum.UNKNOWN.getCode());
    }

    private void updateResult(ExecuteResult result, int updateCount) {
        result.setUpdateCount(updateCount);
        result.setSqlType(SqlTypeEnum.UNKNOWN.getCode());
    }

    private Document optionalDocument(List<String> arguments, int index) {
        return arguments.size() <= index ? new Document() : document(arguments, index);
    }

    private Document document(List<String> arguments, int index) {
        return MongodbCommandParser.parseDocument(required(arguments, index, "document"));
    }

    @SuppressWarnings("unchecked")
    private List<Document> documentList(List<String> arguments, int index) {
        Object value = MongodbCommandParser.parseValue(required(arguments, index, "documents"));
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected a MongoDB document array");
        }
        List<Document> documents = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Document document)) {
                throw new IllegalArgumentException("MongoDB document array contains a non-document value");
            }
            documents.add(document);
        }
        return documents;
    }

    private String requiredString(List<String> arguments, int index, String name) {
        Object value = MongodbCommandParser.parseValue(required(arguments, index, name));
        if (value == null) {
            throw new IllegalArgumentException("Missing MongoDB argument: " + name);
        }
        return String.valueOf(value);
    }

    private int requiredInt(List<String> arguments, int index, String name) {
        Object value = MongodbCommandParser.parseValue(required(arguments, index, name));
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("MongoDB argument must be a number: " + name);
        }
        return number.intValue();
    }

    private String required(List<String> arguments, int index, String name) {
        if (arguments.size() <= index || StringUtils.isBlank(arguments.get(index))) {
            throw new IllegalArgumentException("Missing MongoDB argument: " + name);
        }
        return arguments.get(index);
    }

    private String dataType(Object value) {
        if (value instanceof Number) {
            return DataTypeEnum.NUMERIC.getCode();
        }
        if (value instanceof Boolean) {
            return DataTypeEnum.BOOLEAN.getCode();
        }
        if (value instanceof List<?>) {
            return DataTypeEnum.ARRAY.getCode();
        }
        if (value instanceof Document || value instanceof Map<?, ?>) {
            return DataTypeEnum.DOCUMENT.getCode();
        }
        return DataTypeEnum.STRING.getCode();
    }

    static String formatValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return DateTimeFormatter.ISO_INSTANT.format(date.toInstant());
        }
        if (value instanceof Document document) {
            return document.toJson();
        }
        if (value instanceof List<?> list) {
            return new Document("value", list).toJson().replaceFirst("^\\{\\s*\"value\"\\s*:\\s*", "")
                    .replaceFirst("\\s*}$", "");
        }
        return String.valueOf(value);
    }

    private int absoluteLine(Integer scriptStartLine, int statementLine) {
        return statementLine + Math.max((scriptStartLine == null ? 1 : scriptStartLine) - 1, 0);
    }

    private record Page(int pageNo, int skip, int limit) {
    }
}
