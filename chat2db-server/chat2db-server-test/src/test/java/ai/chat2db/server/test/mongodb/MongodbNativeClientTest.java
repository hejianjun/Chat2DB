package ai.chat2db.server.test.mongodb;

import ai.chat2db.plugin.mongodb.MongodbCommandParser;
import ai.chat2db.plugin.mongodb.MongodbConnectionProvider;
import ai.chat2db.spi.sql.ConnectInfo;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class MongodbNativeClientTest {

    @Test
    void parseNativeMongoUrl() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl("mongodb://localhost:27018/app?authSource=admin");

        var connectionString = MongodbConnectionProvider.parse(connectInfo);

        Assertions.assertEquals(List.of("localhost:27018"), connectionString.getHosts());
        Assertions.assertEquals("app", connectionString.getDatabase());
    }

    @Test
    void rejectJdbcMongoUrl() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl("jdbc:mongodb://localhost:27017/app");

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> MongodbConnectionProvider.parse(connectInfo));
    }

    @Test
    void splitMqlKeepsNestedSemicolonsAndLines() {
        String script = "db.users.find({\"text\": \"a;b\"});\n"
                + "db.users.updateOne({\"_id\": 1}, {\"$set\": {\"active\": true}})";

        List<MongodbCommandParser.StatementPosition> statements = MongodbCommandParser.splitStatements(script);

        Assertions.assertEquals(2, statements.size());
        Assertions.assertEquals("db.users.find({\"text\": \"a;b\"})", statements.get(0).statement());
        Assertions.assertEquals(2, statements.get(1).endLine());
    }

    @Test
    void parseMqlMethodChainAndDocuments() {
        List<MongodbCommandParser.MethodCall> calls = MongodbCommandParser.parseMethodCalls(
                "find({\"active\": true}).sort({\"createdAt\": -1}).limit(20)");

        Assertions.assertEquals(List.of("find", "sort", "limit"),
                calls.stream().map(MongodbCommandParser.MethodCall::name).toList());
        Document filter = MongodbCommandParser.parseDocument(calls.get(0).arguments().get(0));
        Assertions.assertEquals(Boolean.TRUE, filter.getBoolean("active"));
    }

    @Test
    void splitMqlArgumentsKeepsNestedDocuments() {
        List<String> arguments = MongodbCommandParser.splitArguments(
                "{\"_id\": 1}, {\"$set\": {\"name\": \"chat2db\", \"tags\": [\"a\", \"b\"]}}");

        Assertions.assertEquals(2, arguments.size());
        Assertions.assertTrue(arguments.get(1).contains("$set"));
    }

    @Test
    void parseCommonMqlDocumentSyntax() {
        Document document = MongodbCommandParser.parseDocument(
                "{_id: ObjectId('507f1f77bcf86cd799439011'), name: 'Chat2DB', createdAt: ISODate('2024-01-01T00:00:00Z')}");

        Assertions.assertEquals("507f1f77bcf86cd799439011", document.getObjectId("_id").toHexString());
        Assertions.assertEquals("Chat2DB", document.getString("name"));
        Assertions.assertNotNull(document.getDate("createdAt"));
    }
}
