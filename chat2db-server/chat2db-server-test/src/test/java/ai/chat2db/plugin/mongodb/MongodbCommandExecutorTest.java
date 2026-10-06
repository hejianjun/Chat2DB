package ai.chat2db.plugin.mongodb;

import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.model.ExecuteResult;
import com.mongodb.ExplainVerbosity;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MongodbCommandExecutorTest {

    @Test
    @SuppressWarnings("unchecked")
    void explainsLocationQueryWithoutAddingUiPagination() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> iterable = mock(FindIterable.class, RETURNS_SELF);
        when(collection.find(any(Document.class))).thenReturn(iterable);
        Document plan = new Document("executionStats", new Document("nReturned", 12));
        when(iterable.explain(ExplainVerbosity.EXECUTION_STATS)).thenReturn(plan);
        List<MongodbCommandParser.MethodCall> calls = MongodbCommandParser.parseMethodCalls(
                "find({'metadata.deviceId': 'LMXD12BJ0RZ359917', acquisitionTime: "
                        + "{$gte: 1788192000000, $lt: 1788278400000}}, {_id: 1, acquisitionTime: 1})"
                        + ".sort({acquisitionTime: 1}).limit(10001).maxTimeMS(30000).explain('executionStats')");
        ExecuteResult result = new ExecuteResult();

        new MongodbCommandExecutor().executeFind(collection, calls.get(0).arguments(),
                calls.subList(1, calls.size()), 3, 200, result);

        verify(collection).find(new Document("metadata.deviceId", "LMXD12BJ0RZ359917")
                .append("acquisitionTime", new Document("$gte", 1788192000000L).append("$lt", 1788278400000L)));
        verify(iterable).projection(new Document("_id", 1).append("acquisitionTime", 1));
        verify(iterable).sort(new Document("acquisitionTime", 1));
        verify(iterable).limit(10001);
        verify(iterable, never()).limit(10002);
        verify(iterable, never()).skip(anyInt());
        verify(iterable).maxTime(30000L, TimeUnit.MILLISECONDS);
        verify(iterable).explain(ExplainVerbosity.EXECUTION_STATS);
        verify(iterable, never()).into(any());
        Assertions.assertEquals(List.of(List.of("{\"nReturned\": 12}")), result.getDataList());
        Assertions.assertFalse(result.isCanEdit());
        Assertions.assertFalse(result.isCanDelete());
    }

    @Test
    @SuppressWarnings("unchecked")
    void appliesTimeoutToRegularFindAndPreservesPagination() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> iterable = mock(FindIterable.class, RETURNS_SELF);
        when(collection.find(any(Document.class))).thenReturn(iterable);
        when(iterable.into(any())).thenReturn(new ArrayList<Document>());
        when(collection.getNamespace()).thenReturn(new com.mongodb.MongoNamespace("location", "location"));
        List<MongodbCommandParser.MethodCall> chain = MongodbCommandParser.parseMethodCalls("maxTimeMS(30000)");

        new MongodbCommandExecutor().executeFind(collection, List.of("{}"), chain, 2, 200, new ExecuteResult());

        verify(iterable).maxTime(30000L, TimeUnit.MILLISECONDS);
        verify(iterable).skip(200);
        verify(iterable).limit(201);
        verify(iterable, never()).explain(any(ExplainVerbosity.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsLongTimeoutAndRejectsInvalidTimeouts() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> iterable = mock(FindIterable.class, RETURNS_SELF);
        when(collection.find(any(Document.class))).thenReturn(iterable);
        MongodbCommandExecutor executor = new MongodbCommandExecutor();
        executor.configureFind(collection, List.of("{}"),
                MongodbCommandParser.parseMethodCalls("maxTimeMS(3000000000)"));
        verify(iterable).maxTime(3000000000L, TimeUnit.MILLISECONDS);
        for (String value : List.of("-1", "1.5", "'30000'", "", "1, 2")) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> executor.configureFind(collection,
                    List.of("{}"), MongodbCommandParser.parseMethodCalls("maxTimeMS(" + value + ")")));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsExplainToQueryPlannerAndRejectsUnsupportedChains() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> iterable = mock(FindIterable.class, RETURNS_SELF);
        when(collection.find(any(Document.class))).thenReturn(iterable);
        when(iterable.explain(ExplainVerbosity.QUERY_PLANNER)).thenReturn(new Document("ok", 1));
        MongodbCommandExecutor executor = new MongodbCommandExecutor();
        executor.executeFind(collection, List.of("{}"), MongodbCommandParser.parseMethodCalls("explain()"),
                1, 200, new ExecuteResult());
        verify(iterable).explain(ExplainVerbosity.QUERY_PLANNER);
        for (String chain : List.of("explain('bad')", "explain('queryPlanner', 'executionStats')",
                "explain().limit(2)")) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> executor.executeFind(collection,
                    List.of("{}"), MongodbCommandParser.parseMethodCalls(chain), 1, 200, new ExecuteResult()));
        }
    }

    @Test
    void formatDateAsUtcIsoInstant() {
        Date date = Date.from(Instant.parse("2026-04-21T16:00:00.123Z"));

        Assertions.assertEquals("2026-04-21T16:00:00.123Z", MongodbCommandExecutor.formatValue(date));
        Assertions.assertEquals(DataTypeEnum.DATETIME.getCode(), MongodbCommandExecutor.dataType(date));
    }

    @Test
    void buildCollectionStatsCommand() {
        Document command = MongodbCommandExecutor.collectionStatsCommand("order-items",
                List.of("{scale: 1024, indexDetails: true}"));

        Assertions.assertEquals("order-items", command.getString("collStats"));
        Assertions.assertEquals(1024, command.getInteger("scale"));
        Assertions.assertEquals(Boolean.TRUE, command.getBoolean("indexDetails"));
    }

    @Test
    void buildCollectionStatsCommandWithNumericScale() {
        Document command = MongodbCommandExecutor.collectionStatsCommand("orders", List.of("2048"));

        Assertions.assertEquals("orders", command.getString("collStats"));
        Assertions.assertEquals(2048, command.getInteger("scale"));
    }

    @Test
    void buildDeleteFilterPreservesObjectIdType() {
        String filter = MongodbCommandExecutor.buildDeleteFilter(
                new Document("_id", new ObjectId("507f1f77bcf86cd799439011")));

        Document parsed = Document.parse(filter);
        Assertions.assertEquals("507f1f77bcf86cd799439011", parsed.getObjectId("_id").toHexString());
    }

    @Test
    void findResultIsNotModifiableWhenProjectionExcludesId() {
        List<Document> documents = List.of(new Document("name", "Chat2DB"));

        Assertions.assertFalse(MongodbCommandExecutor.canModifyFindResult(
                List.of("{}", "{name: 1, _id: 0}"), List.of(), documents));
    }

    @Test
    void findResultCanBeEditedAndDeletedWhenIdIsVisible() {
        List<Document> documents = List.of(
                new Document("_id", new ObjectId("507f1f77bcf86cd799439011"))
                        .append("name", "Chat2DB"));

        Assertions.assertTrue(MongodbCommandExecutor.canModifyFindResult(List.of("{}"), List.of(), documents));
    }
}
