package ai.chat2db.server.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

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

    @Test
    void preparedChangePreservesListPatchAndIncludesItInDigest() {
        PreparedRedisChangeStore store = new PreparedRedisChangeStore();
        PreparedRedisChange first = store.prepare(PreparedRedisChange.builder()
                .operation("PATCH_LIST").datasourceUri("chat2db://datasources/6").databaseName("0")
                .key("mcp:test:list").keyType("list").listUpdates(Map.of(1L, "updated"))
                .listAppends(List.of("tail")).target("redis/0/mcp:test:list").fingerprint("abc").build());
        PreparedRedisChange second = store.prepare(PreparedRedisChange.builder()
                .operation("PATCH_LIST").datasourceUri("chat2db://datasources/6").databaseName("0")
                .key("mcp:test:list").keyType("list").listUpdates(Map.of(1L, "different"))
                .listAppends(List.of("tail")).target("redis/0/mcp:test:list").fingerprint("abc").build());

        assertThat(first.getListUpdates()).containsEntry(1L, "updated");
        assertThat(first.getListAppends()).containsExactly("tail");
        assertThat(first.getDigest()).isNotEqualTo(second.getDigest());
    }
}
