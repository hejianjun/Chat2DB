package ai.chat2db.server.mcp.config;

import java.util.List;

import ai.chat2db.server.mcp.tool.DangerousSqlTools;
import ai.chat2db.server.mcp.tool.ForeignKeyTools;
import ai.chat2db.server.mcp.tool.MetadataTools;
import ai.chat2db.server.mcp.tool.MongoTools;
import ai.chat2db.server.mcp.tool.QueryTools;
import ai.chat2db.server.mcp.tool.RedisTools;
import ai.chat2db.server.mcp.tool.SchemaInsightTools;
import ai.chat2db.server.mcp.support.DataSourceResourceService;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;

/**
 * Registers Chat2DB MCP tools.
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(prefix = "chat2db.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerConfiguration {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> chat2dbMcpTools(MetadataTools metadataTools,
        QueryTools queryTools, DangerousSqlTools dangerousSqlTools, MongoTools mongoTools,
        ForeignKeyTools foreignKeyTools, SchemaInsightTools schemaInsightTools, RedisTools redisTools) {
        return SyncMcpAnnotationProviders.toolSpecifications(List.of(metadataTools, queryTools, dangerousSqlTools,
            mongoTools, foreignKeyTools, schemaInsightTools, redisTools));
    }

    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> chat2dbMcpResources(
        DataSourceResourceService dataSourceResourceService) {
        return List.of();
    }

    @Bean
    public List<McpServerFeatures.SyncResourceTemplateSpecification> chat2dbMcpResourceTemplates(
        DataSourceResourceService dataSourceResourceService) {
        return dataSourceResourceService.resourceTemplateSpecifications();
    }
}
