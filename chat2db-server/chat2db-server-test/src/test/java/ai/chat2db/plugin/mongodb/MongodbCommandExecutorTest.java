package ai.chat2db.plugin.mongodb;

import ai.chat2db.spi.enums.DataTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class MongodbCommandExecutorTest {

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
    void findResultIsNotDeletableWhenProjectionExcludesId() {
        List<MongodbCommandParser.MethodCall> chain = new ArrayList<>();
        List<Document> documents = List.of(new Document("name", "Chat2DB"));

        Assertions.assertFalse(MongodbCommandExecutor.canDeleteFindResult(
                List.of("{}", "{name: 1, _id: 0}"), chain, documents));
    }
}
