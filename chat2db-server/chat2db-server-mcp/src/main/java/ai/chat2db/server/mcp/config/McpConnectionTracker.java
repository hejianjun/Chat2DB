package ai.chat2db.server.mcp.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * Tracks MCP sessions observed by the Streamable HTTP transport.
 */
@Component
public class McpConnectionTracker {

    private static final Duration SESSION_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, McpClientConnection> sessions = new ConcurrentHashMap<>();

    public void connected(String sessionId) {
        connected(sessionId, null);
    }

    public void connected(String sessionId, McpClientInfo clientInfo) {
        if (sessionId != null && !sessionId.isBlank()) {
            Instant now = Instant.now();
            sessions.compute(sessionId, (key, existing) -> {
                if (existing == null) {
                    return new McpClientConnection(clientInfo, now, now);
                }
                if (clientInfo != null) {
                    existing.setClientInfo(clientInfo);
                }
                existing.setLastActiveAt(now);
                return existing;
            });
        }
    }

    public void disconnected(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public int activeConnectionCount() {
        removeExpiredSessions();
        return sessions.size();
    }

    public List<McpClientConnection> activeConnections() {
        removeExpiredSessions();
        return sessions.values().stream()
            .sorted(Comparator.comparing(McpClientConnection::getConnectedAt))
            .toList();
    }

    private void removeExpiredSessions() {
        Instant expiredBefore = Instant.now().minus(SESSION_IDLE_TIMEOUT);
        sessions.entrySet().removeIf(entry -> entry.getValue().getLastActiveAt().isBefore(expiredBefore));
    }

    @Data
    @AllArgsConstructor
    public static class McpClientInfo {
        private String name;
        private String version;
        private String protocolVersion;
    }

    @Data
    @AllArgsConstructor
    public static class McpClientConnection {
        private McpClientInfo clientInfo;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant connectedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant lastActiveAt;
    }
}
