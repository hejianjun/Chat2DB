package ai.chat2db.server.web.api.controller.sql;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chat2db.server.web.api.controller.sql.request.SqlFormatRequest;
import org.junit.jupiter.api.Test;

class SqlControllerTest {

    private final SqlController controller = new SqlController();

    @Test
    void formatsMongoMqlWithoutBreakingDollarOperators() {
        String mql = "db.location.find({ deviceId: \"LMXA14AF1KZ421049\","
                + "acquisitionTime: {$gt: 0},status: {$in: [\"OPEN\", \"DONE\"]} })";

        String formatted = controller.list(SqlFormatRequest.builder().sql(mql).dbType("MONGODB").build()).getData();

        assertThat(formatted)
                .contains("$gt: 0")
                .contains("$in: [")
                .doesNotContain("$ gt", "$ in")
                .isEqualTo("""
                        db.location.find({
                          deviceId: \"LMXA14AF1KZ421049\",
                          acquisitionTime: {
                            $gt: 0
                          },
                          status: {
                            $in: [
                              \"OPEN\",
                              \"DONE\"
                            ]
                          }
                        })""");
    }

    @Test
    void preservesMongoStringsCommentsAndRegularExpressions() {
        String mql = "db.logs.find({message: '$gt,{x}', name: /^a{1,2},b$/i, /* $in */ score: {$gte: 1}})";

        String formatted = controller.list(SqlFormatRequest.builder().sql(mql).dbType("mongodb").build()).getData();

        assertThat(formatted)
                .contains("message: '$gt,{x}'")
                .contains("/^a{1,2},b$/i")
                .contains("/* $in */ score")
                .contains("$gte: 1");
    }

    @Test
    void continuesToUseSqlFormatterForRelationalDatabases() {
        String formatted = controller.list(SqlFormatRequest.builder()
                .sql("select id,name from users where id=1")
                .dbType("MYSQL")
                .build()).getData();

        assertThat(formatted.toLowerCase())
                .contains("select\n")
                .contains("from\n")
                .contains("users")
                .contains("where\n")
                .contains("id = 1");
    }
}
