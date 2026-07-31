package ai.chat2db.server.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chat2db.server.mcp.config.McpConnectionTracker.McpClientInfo;
import org.junit.jupiter.api.Test;

class McpConnectionTrackerTest {

    @Test
    void shouldExposeClientInformationAndUpdateLastActiveTime() {
        McpConnectionTracker tracker = new McpConnectionTracker();
        tracker.connected("session-1", new McpClientInfo("Codex", "1.2.3", "2025-06-18"));
        tracker.connected("session-1");

        assertThat(tracker.activeConnectionCount()).isEqualTo(1);
        assertThat(tracker.activeConnections()).singleElement().satisfies(connection -> {
            assertThat(connection.getClientInfo().getName()).isEqualTo("Codex");
            assertThat(connection.getClientInfo().getVersion()).isEqualTo("1.2.3");
            assertThat(connection.getClientInfo().getProtocolVersion()).isEqualTo("2025-06-18");
            assertThat(connection.getLastActiveAt()).isAfterOrEqualTo(connection.getConnectedAt());
        });
    }

    @Test
    void shouldRemoveDisconnectedClient() {
        McpConnectionTracker tracker = new McpConnectionTracker();
        tracker.connected("session-1");
        tracker.disconnected("session-1");

        assertThat(tracker.activeConnections()).isEmpty();
    }
}
