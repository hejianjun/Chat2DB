package ai.chat2db.server.mcp.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
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

    public McpConnectionTrackingFilter(McpConnectionTracker connectionTracker) {
        this.connectionTracker = connectionTracker;
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
        filterChain.doFilter(request, response);

        String sessionId = StringUtils.defaultIfBlank(requestSessionId, response.getHeader(SESSION_HEADER));
        if (HttpServletResponse.SC_OK <= response.getStatus() && response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
            if ("DELETE".equalsIgnoreCase(request.getMethod())) {
                connectionTracker.disconnected(sessionId);
            } else {
                connectionTracker.connected(sessionId);
            }
        }
    }
}
