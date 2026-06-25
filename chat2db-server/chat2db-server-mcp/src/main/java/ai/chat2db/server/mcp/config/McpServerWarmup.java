package ai.chat2db.server.mcp.config;

import ai.chat2db.server.mcp.support.DataSourceResourceService;
import io.modelcontextprotocol.server.McpSyncServer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Eagerly initializes the MCP server after application startup.
 */
@Component
@Lazy(false)
@ConditionalOnProperty(prefix = "chat2db.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerWarmup implements ApplicationRunner {

    private final McpSyncServer mcpSyncServer;
    private final DataSourceResourceService dataSourceResourceService;

    public McpServerWarmup(McpSyncServer mcpSyncServer, DataSourceResourceService dataSourceResourceService) {
        this.mcpSyncServer = mcpSyncServer;
        this.dataSourceResourceService = dataSourceResourceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Force MCP server creation so the SSE transport receives its session factory.
        mcpSyncServer.getServerCapabilities();
        dataSourceResourceService.resourceSpecifications().forEach(mcpSyncServer::addResource);
        mcpSyncServer.notifyResourcesListChanged();
    }
}
