package ai.chat2db.plugin.redis;

import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.redis.RedisKeyMutation;
import ai.chat2db.server.tools.base.excption.BusinessException;
import io.lettuce.core.MapScanCursor;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class RedisHashFieldSearchTest {
    @Test
    void hashFieldPatchDoesNotWatchTheWholeHash() {
        withCommands(commands -> {
            doReturn(future(1L)).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                    any(String[].class), any(String[].class));
            new RedisKeyBrowserService().partialUpdateKey(null, RedisKeyMutation.builder()
                    .keyName("vehicles").updateKey("vehicles").keyType("hash")
                    .hashUpserts(Map.of("VIN123", "new value")).hashDeletes(List.of("oldVin")).build());
            verify(commands).eval(eq(RedisKeyBrowserService.PATCH_HASH_FIELDS_SCRIPT), eq(ScriptOutputType.INTEGER),
                    aryEq(new String[]{"vehicles"}), aryEq(new String[]{"1", "VIN123", "new value", "oldVin"}));
            verifyNoMoreInteractions(commands);
        });
    }

    @Test
    void hashPatchRejectsMissingOrChangedKey() {
        withCommands(commands -> {
            doReturn(future(-1L), future(-2L)).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                    any(String[].class), any(String[].class));
            var mutation = RedisKeyMutation.builder().keyName("vehicles").keyType("hash")
                    .hashUpserts(Map.of("VIN123", "value")).build();
            assertThrows(BusinessException.class, () -> new RedisKeyBrowserService().partialUpdateKey(null, mutation));
            assertThrows(BusinessException.class, () -> new RedisKeyBrowserService().partialUpdateKey(null, mutation));
        });
    }

    @Test
    void literalFieldUsesHgetAndPreservesEmptyValue() {
        withCommands(commands -> {
            doReturn(future("")).when(commands).hget("vehicles", "VIN123");
            var page = new RedisKeyBrowserService().queryHashFields(null, "vehicles", "VIN123", "0");
            assertEquals("", page.getItems().get(0).get("value"));
            assertFalse(page.getHasMore());
            verify(commands).hget("vehicles", "VIN123");
            verifyNoMoreInteractions(commands);
        });
    }

    @Test
    void missingFieldReturnsNoRows() {
        withCommands(commands -> {
            doReturn(future(null)).when(commands).hget("vehicles", "missing");
            var page = new RedisKeyBrowserService().queryHashFields(null, "vehicles", "missing", "0");
            assertTrue(page.getItems().isEmpty());
            assertFalse(page.getHasMore());
        });
    }

    @Test
    void globKeepsCursorEvenWhenBatchHasNoMatches() {
        withCommands(commands -> {
            MapScanCursor<String, String> scan = new MapScanCursor<>();
            scan.setCursor("42");
            doReturn(future(scan)).when(commands).hscan(eq("vehicles"), any(ScanCursor.class), any(ScanArgs.class));
            var page = new RedisKeyBrowserService().queryHashFields(null, "vehicles", "VIN*", "17");
            assertTrue(page.getItems().isEmpty());
            assertTrue(page.getHasMore());
            assertEquals("42", page.getNextCursor());
            var cursor = ArgumentCaptor.forClass(ScanCursor.class);
            verify(commands).hscan(eq("vehicles"), cursor.capture(), any(ScanArgs.class));
            assertEquals("17", cursor.getValue().getCursor());
            verifyNoMoreInteractions(commands);
        });
    }

    @SuppressWarnings("unchecked")
    private void withCommands(java.util.function.Consumer<RedisAsyncCommands<String, String>> test) {
        var commands = (RedisAsyncCommands<String, String>) mock(RedisAsyncCommands.class);
        var connection = (StatefulRedisConnection<String, String>) mock(StatefulRedisConnection.class);
        var context = mock(RedisConnectionProvider.RedisConnectionContext.class);
        when(context.connection()).thenReturn(connection);
        when(connection.async()).thenReturn(commands);
        try (var chat = mockStatic(Chat2DBContext.class); var provider = mockStatic(RedisConnectionProvider.class)) {
            ConnectInfo info = new ConnectInfo();
            chat.when(Chat2DBContext::getConnectInfo).thenReturn(info);
            provider.when(() -> RedisConnectionProvider.open(info)).thenReturn(context);
            test.accept(commands);
            verify(context, atLeastOnce()).close();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisFuture<T> future(T value) {
        RedisFuture<T> future = mock(RedisFuture.class);
        when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(value));
        return future;
    }
}
