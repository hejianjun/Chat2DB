package ai.chat2db.server.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import ai.chat2db.server.domain.api.service.RedisKeyPrefixService;
import ai.chat2db.server.mcp.config.McpServerConfigService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.PreparedRedisChangeStore;
import ai.chat2db.server.mcp.support.SqlGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RedisToolsTest {

    @Test
    void mcpSchemaExposesRedisBrowseWriteAndPrefixTools() {
        RedisTools tools = new RedisTools(mock(ConnectionScope.class), mock(RedisKeyPrefixService.class),
                mock(McpServerConfigService.class), new PreparedRedisChangeStore(), mock(SqlGuard.class),
                new ObjectMapper());

        List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> specifications =
                org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders
                        .toolSpecifications(List.of(tools));

        assertThat(specifications).extracting(specification -> specification.tool().name())
                .containsExactlyInAnyOrder("searchRedisKeys", "getRedisKey", "searchRedisKeyPrefixes",
                        "sampleRedisKeyPrefix", "getRedisKeyPrefixStatus", "refreshRedisKeyPrefixes",
                        "updateRedisKeyPrefixComment", "deleteRedisKeyPrefixMetadata", "prepareRedisKeyChange",
                        "executePreparedRedisKeyChange");
    }
}
