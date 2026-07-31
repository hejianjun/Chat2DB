package ai.chat2db.server.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpConnectionTrackingFilterTest {

    @Test
    void shouldCaptureClientInfoFromInitializeRequest() throws Exception {
        McpConnectionTracker tracker = new McpConnectionTracker();
        McpConnectionTrackingFilter filter = new McpConnectionTrackingFilter(tracker, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        request.setContentType("application/json");
        request.setContent("""
            {
              "jsonrpc": "2.0",
              "method": "initialize",
              "params": {
                "protocolVersion": "2025-06-18",
                "clientInfo": {"name": "Codex", "version": "1.2.3"}
              }
            }
            """.getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Mcp-Session-Id", "session-1");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
            servletRequest.getInputStream().readAllBytes());

        assertThat(tracker.activeConnections()).singleElement().satisfies(connection -> {
            assertThat(connection.getClientInfo().getName()).isEqualTo("Codex");
            assertThat(connection.getClientInfo().getVersion()).isEqualTo("1.2.3");
            assertThat(connection.getClientInfo().getProtocolVersion()).isEqualTo("2025-06-18");
        });
    }
}
