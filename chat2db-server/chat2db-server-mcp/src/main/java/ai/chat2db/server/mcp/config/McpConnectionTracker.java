package ai.chat2db.server.mcp.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Tracks MCP sessions observed by the Streamable HTTP transport.
 */
@Component
public class McpConnectionTracker {

    private static final Duration SESSION_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public void connected(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.put(sessionId, Instant.now());
        }
    }

    public void disconnected(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public int activeConnectionCount() {
        Instant expiredBefore = Instant.now().minus(SESSION_IDLE_TIMEOUT);
        sessions.entrySet().removeIf(entry -> entry.getValue().isBefore(expiredBefore));
        return sessions.size();
    }
}
