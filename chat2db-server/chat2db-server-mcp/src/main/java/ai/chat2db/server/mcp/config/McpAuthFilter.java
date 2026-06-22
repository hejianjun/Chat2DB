package ai.chat2db.server.mcp.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects MCP endpoints with an optional bearer token.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "chat2db.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final McpServerConfigService mcpServerConfigService;

    public McpAuthFilter(McpServerConfigService mcpServerConfigService) {
        this.mcpServerConfigService = mcpServerConfigService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = StringUtils.removeStart(request.getRequestURI(), request.getContextPath());
        String effectiveToken = getEffectiveToken();
        boolean mcpPath = isMcpPath(path);
        boolean skip = !mcpPath || StringUtils.isBlank(effectiveToken);
        if (mcpPath) {
            log.info("MCP auth check: uri={}, contextPath={}, path={}, configuredToken={}, tokenLength={}, skipFilter={}",
                request.getRequestURI(), request.getContextPath(), path, StringUtils.isNotBlank(effectiveToken),
                StringUtils.length(effectiveToken), skip);
        }
        // Leave non-MCP routes untouched and allow tokenless local setups when no token is configured.
        return skip;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = StringUtils.removeStart(authorization, BEARER_PREFIX);
        String effectiveToken = getEffectiveToken();
        boolean authorized = StringUtils.equals(token, effectiveToken);
        log.info("MCP auth result: uri={}, authorizationHeader={}, bearerPrefix={}, providedTokenLength={}, authorized={}",
            request.getRequestURI(), StringUtils.isNotBlank(authorization),
            StringUtils.startsWith(authorization, BEARER_PREFIX), StringUtils.length(token), authorized);
        if (!authorized) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid MCP token\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isMcpPath(String path) {
        return StringUtils.equals(path, "/api/mcp") || StringUtils.startsWith(path, "/api/mcp/");
    }

    private String getEffectiveToken() {
        return mcpServerConfigService.getEffectiveToken();
    }
}
