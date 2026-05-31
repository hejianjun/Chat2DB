package ai.chat2db.server.web.api.controller.ai.statemachine.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExplainTableNameExtractorTest {

    @Test
    void extractFromMysqlTableColumn() {
        ExplainResult result = ExplainResult.builder()
                .planRows(List.of(
                        List.of("id", "select_type", "table", "type"),
                        List.of("1", "SIMPLE", "users", "ALL"),
                        List.of("1", "SIMPLE", "orders", "ref")))
                .formattedPlan("")
                .build();

        assertEquals(
                List.of("users", "orders"),
                ExplainTableNameExtractor.extractTableNames("select * from users join orders on users.id = orders.user_id", result));
    }

    @Test
    void extractFromPostgresqlFormattedPlan() {
        ExplainResult result = ExplainResult.builder()
                .formattedPlan("""
                        Nested Loop
                          ->  Seq Scan on public.users u
                          ->  Index Scan using orders_user_id_idx on orders o
                        """)
                .build();

        assertEquals(
                List.of("users", "orders"),
                ExplainTableNameExtractor.extractTableNames(null, result));
    }

    @Test
    void fallbackToSqlParser() {
        ExplainResult result = ExplainResult.builder()
                .formattedPlan("explain output without table names")
                .build();

        assertEquals(
                List.of("users", "orders"),
                ExplainTableNameExtractor.extractTableNames(
                        "select * from `users` u join public.orders o on u.id = o.user_id",
                        result));
    }

    @Test
    void returnsEmptyListWhenNoTableFound() {
        ExplainResult result = ExplainResult.builder()
                .formattedPlan("Result  (cost=0.00..0.01 rows=1 width=4)")
                .build();

        assertEquals(List.of(), ExplainTableNameExtractor.extractTableNames("select 1", result));
    }
}
