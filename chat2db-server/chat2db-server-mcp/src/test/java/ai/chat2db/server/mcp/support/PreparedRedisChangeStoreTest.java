package ai.chat2db.server.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.chat2db.server.mcp.support.PreparedRedisChangeStore.PreparedRedisChange;
import org.junit.jupiter.api.Test;

class PreparedRedisChangeStoreTest {

    @Test
    void preparedChangeRequiresExactConfirmationAndIsSingleUse() {
        PreparedRedisChangeStore store = new PreparedRedisChangeStore();
        PreparedRedisChange change = store.prepare(PreparedRedisChange.builder()
                .operation("DELETE").datasourceUri("chat2db://datasources/6").databaseName("0")
                .key("mcp:test:key").target("redis/0/mcp:test:key").fingerprint("abc").build());

        PreparedRedisChange consumed = store.consume(change.getOperationId(), change.getConfirmationText());
        assertThat(consumed.getDigest()).isNotBlank();
        assertThatThrownBy(() -> store.consume(change.getOperationId(), change.getConfirmationText()))
                .hasMessageContaining("not found or expired");
    }
}
