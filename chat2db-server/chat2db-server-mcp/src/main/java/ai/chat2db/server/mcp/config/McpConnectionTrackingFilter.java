package ai.chat2db.server.mcp.config;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.chat2db.server.mcp.config.McpConnectionTracker.McpClientInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Observes MCP transport requests without changing their protocol handling.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "chat2db.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpConnectionTrackingFilter extends OncePerRequestFilter {

    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final McpConnectionTracker connectionTracker;
    private final ObjectMapper objectMapper;

    public McpConnectionTrackingFilter(McpConnectionTracker connectionTracker, ObjectMapper objectMapper) {
        this.connectionTracker = connectionTracker;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = StringUtils.removeStart(request.getRequestURI(), request.getContextPath());
        return !StringUtils.equals(path, "/api/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String requestSessionId = request.getHeader(SESSION_HEADER);
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        filterChain.doFilter(requestWrapper, response);

        String sessionId = StringUtils.defaultIfBlank(requestSessionId, response.getHeader(SESSION_HEADER));
        if (HttpServletResponse.SC_OK <= response.getStatus() && response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
            if ("DELETE".equalsIgnoreCase(request.getMethod())) {
                connectionTracker.disconnected(sessionId);
            } else {
                connectionTracker.connected(sessionId, parseClientInfo(requestWrapper));
            }
        }
    }

    private McpClientInfo parseClientInfo(ContentCachingRequestWrapper request) {
        try {
            JsonNode root = objectMapper.readTree(request.getContentAsByteArray());
            if (!"initialize".equals(root.path("method").asText())) {
                return null;
            }
            JsonNode params = root.path("params");
            JsonNode clientInfo = params.path("clientInfo");
            return new McpClientInfo(
                StringUtils.defaultIfBlank(clientInfo.path("name").asText(), null),
                StringUtils.defaultIfBlank(clientInfo.path("version").asText(), null),
                StringUtils.defaultIfBlank(params.path("protocolVersion").asText(), null)
            );
        } catch (Exception ignored) {
            // Connection tracking must never affect MCP protocol handling.
            return null;
        }
    }
}
