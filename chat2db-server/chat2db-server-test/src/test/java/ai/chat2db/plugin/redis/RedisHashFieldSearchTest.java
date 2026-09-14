package ai.chat2db.plugin.redis;

import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import io.lettuce.core.MapScanCursor;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisHashFieldSearchTest {
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
            verify(context).close();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisFuture<T> future(T value) {
        RedisFuture<T> future = mock(RedisFuture.class);
        when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(value));
        return future;
    }
}
