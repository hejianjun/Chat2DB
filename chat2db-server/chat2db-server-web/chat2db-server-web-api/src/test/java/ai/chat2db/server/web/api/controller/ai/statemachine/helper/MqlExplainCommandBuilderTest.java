package ai.chat2db.server.web.api.controller.ai.statemachine.helper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MqlExplainCommandBuilderTest {

    @Test
    void preservesTimeoutAndExplicitExplainVerbosity() {
        Assertions.assertEquals(
                "db.runCommand({ explain: { find: \"location\", filter: {}, "
                        + "sort: { acquisitionTime: 1 }, limit: 10001, maxTimeMS: 30000 }, "
                        + "verbosity: \"executionStats\" })",
                MqlExplainCommandBuilder.build("db.getCollection('location').find({})"
                        + ".sort({ acquisitionTime: 1 }).limit(10001).maxTimeMS(30000).explain('executionStats')"));
        Assertions.assertTrue(MqlExplainCommandBuilder.build("db.location.find({}).maxTimeMS(30000)")
                .contains("maxTimeMS: 30000"));
        Assertions.assertTrue(MqlExplainCommandBuilder.build("db.location.find({}).explain()")
                .contains("verbosity: \"queryPlanner\""));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> MqlExplainCommandBuilder.build("db.location.find({}).explain('invalid')"));
    }

    @Test
    void buildsFindExplainWithModifiers() {
        String mql = "db.orders.find({ status: 'PAID' }, { total: 1 })"
                + ".sort({ createdAt: -1 }).skip(10).limit(20)";

        String explain = MqlExplainCommandBuilder.build(mql);

        Assertions.assertEquals(
                "db.runCommand({ explain: { find: \"orders\", filter: { status: 'PAID' }, "
                        + "projection: { total: 1 }, sort: { createdAt: -1 }, skip: 10, limit: 20 }, "
                        + "verbosity: \"executionStats\" })",
                explain);
        Assertions.assertEquals("orders", MqlExplainCommandBuilder.extractCollectionName(mql));
    }

    @Test
    void buildsAggregateExplainForGetCollectionSyntax() {
        String mql = "db.getCollection('order-items').aggregate(["
                + "{ $match: { amount: { $gt: 100 } } }, { $group: { _id: '$customerId' } }])";

        String explain = MqlExplainCommandBuilder.build(mql);

        Assertions.assertEquals(
                "db.runCommand({ explain: { aggregate: \"order-items\", pipeline: "
                        + "[{ $match: { amount: { $gt: 100 } } }, { $group: { _id: '$customerId' } }], "
                        + "cursor: {} }, verbosity: \"executionStats\" })",
                explain);
        Assertions.assertEquals("order-items", MqlExplainCommandBuilder.extractCollectionName(mql));
    }

    @Test
    void mapsFindOneToFindWithLimit() {
        String explain = MqlExplainCommandBuilder.build("db.users.findOne({ name: \"Ada\" })");

        Assertions.assertEquals(
                "db.runCommand({ explain: { find: \"users\", filter: { name: \"Ada\" }, limit: 1 }, "
                        + "verbosity: \"executionStats\" })",
                explain);
    }

    @Test
    void rejectsWriteOperationsAndMultipleStatements() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> MqlExplainCommandBuilder.build("db.users.updateOne({}, { $set: { active: true } })"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> MqlExplainCommandBuilder.build("db.users.find({}); db.orders.find({})"));
    }
}
