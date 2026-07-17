package ai.chat2db.server.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.mcp.support.PreparedMongoChangeStore.PreparedMongoChange;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PreparedMongoChangeStoreTest {

    @Test
    void preparedChangeIsBoundToPayloadAndConsumedOnce() {
        PreparedMongoChangeStore store = new PreparedMongoChangeStore();
        ResolvedDataSource dataSource = ResolvedDataSource.builder()
            .dataSourceId(1L)
            .dataSource(new DataSource())
            .summary("id=1;name=mongo;env=dev")
            .build();
        PreparedMongoChange change = store.prepare("WRITE", "UPDATE_MANY", "chat2db://datasources/1",
            dataSource, "app", "orders", "db.orders.updateMany({\"x\":1},{\"$set\":{\"y\":2}})",
            "db.orders.countDocuments({\"x\":1})", 2L, 5, "CRITICAL");

        assertThat(change.getConfirmationText()).contains("UPDATE_MANY").contains("sha256:");
        assertThat(store.consume(change.getOperationId(), change.getConfirmationText()).getMql())
            .isEqualTo(change.getMql());
        assertThatThrownBy(() -> store.consume(change.getOperationId(), change.getConfirmationText()))
            .hasMessageContaining("not found or expired");
    }

    @Test
    void confirmationTextMustMatchExactly() {
        PreparedMongoChangeStore store = new PreparedMongoChangeStore();
        ResolvedDataSource dataSource = ResolvedDataSource.builder().summary("mongo").build();
        PreparedMongoChange change = store.prepare("ADMIN", "DROP_DATABASE", "chat2db://datasources/1",
            dataSource, "app", null, "db.dropDatabase()", null, null, null, "CRITICAL");

        assertThatThrownBy(() -> store.consume(change.getOperationId(), "EXECUTE SOMETHING ELSE"))
            .hasMessageContaining("does not match");
    }

    @Test
    void preparedChangeIsBoundToMcpSession() {
        PreparedMongoChangeStore store = new PreparedMongoChangeStore();
        ResolvedDataSource dataSource = ResolvedDataSource.builder().summary("mongo").build();
        try {
            setSession("session-a");
            PreparedMongoChange change = store.prepare("ADMIN", "DROP_DATABASE", "chat2db://datasources/1",
                dataSource, "app", null, "db.dropDatabase()", null, null, null, "CRITICAL");

            setSession("session-b");
            assertThatThrownBy(() -> store.consume(change.getOperationId(), change.getConfirmationText()))
                .hasMessageContaining("another user/session");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void preparedChangeExpiresAfterTenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        PreparedMongoChangeStore store = new PreparedMongoChangeStore(clock);
        PreparedMongoChange change = store.prepare("ADMIN", "DROP_DATABASE", "chat2db://datasources/1",
            ResolvedDataSource.builder().summary("mongo").build(), "app", null, "db.dropDatabase()",
            null, null, null, "CRITICAL");

        clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> store.consume(change.getOperationId(), change.getConfirmationText()))
            .hasMessageContaining("not found or expired");
    }

    private void setSession(String sessionId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Mcp-Session-Id", sessionId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
