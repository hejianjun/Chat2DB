package ai.chat2db.server.mcp.config;

import ai.chat2db.server.mcp.tool.DataSourceTools;
import ai.chat2db.server.mcp.tool.DangerousSqlTools;
import ai.chat2db.server.mcp.tool.ForeignKeyTools;
import ai.chat2db.server.mcp.tool.MetadataTools;
import ai.chat2db.server.mcp.tool.QueryTools;
import ai.chat2db.server.mcp.tool.SchemaInsightTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Chat2DB service methods as Spring AI tool callbacks for the MCP server.
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(prefix = "chat2db.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerConfiguration {

    /**
     * Exposes all MCP tool groups through a single callback provider consumed by Spring AI MCP.
     */
    @Bean
    public ToolCallbackProvider chat2dbMcpTools(DataSourceTools dataSourceTools, MetadataTools metadataTools,
        QueryTools queryTools, SchemaInsightTools schemaInsightTools, ForeignKeyTools foreignKeyTools,
        DangerousSqlTools dangerousSqlTools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(dataSourceTools, metadataTools, queryTools, schemaInsightTools, foreignKeyTools,
                dangerousSqlTools)
            .build();
    }
}
