package ai.chat2db.server.mcp.config;

import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp-server")
@ConditionalOnProperty(prefix = "chat2db.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpStatusController {

    private final McpConnectionTracker connectionTracker;

    public McpStatusController(McpConnectionTracker connectionTracker) {
        this.connectionTracker = connectionTracker;
    }

    @GetMapping("/status")
    public DataResult<McpStatus> status() {
        List<McpConnectionTracker.McpClientConnection> clients = connectionTracker.activeConnections();
        return DataResult.of(new McpStatus(!clients.isEmpty(), clients.size(), clients));
    }

    @Data
    @AllArgsConstructor
    public static class McpStatus {
        private boolean connected;
        private int connectionCount;
        private List<McpConnectionTracker.McpClientConnection> clients;
    }
}
