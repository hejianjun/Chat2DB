package ai.chat2db.server.mcp.config;

import java.io.IOException;

import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects MCP endpoints with an optional bearer token.
 */
@Component
@ConditionalOnProperty(prefix = "chat2db.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MCP_SERVER_CONFIG_CODE = "mcp.server.config";
    private static final String AUTH_TYPE_BEARER = "bearer";

    private final McpProperties properties;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    public McpAuthFilter(McpProperties properties, ConfigService configService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Leave non-MCP routes untouched and allow tokenless local setups when no token is configured.
        return !isMcpPath(path) || StringUtils.isBlank(getEffectiveToken());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = StringUtils.removeStart(authorization, BEARER_PREFIX);
        if (!StringUtils.equals(token, getEffectiveToken())) {
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
        McpServerConfig config = getStoredConfig();
        if (config != null) {
            if (StringUtils.equals(config.getAuthType(), AUTH_TYPE_BEARER)) {
                return StringUtils.trimToEmpty(config.getToken());
            }
            return "";
        }
        return StringUtils.trimToEmpty(properties.getToken());
    }

    private McpServerConfig getStoredConfig() {
        try {
            Config config = configService.find(MCP_SERVER_CONFIG_CODE);
            if (config == null || StringUtils.isBlank(config.getContent())) {
                return null;
            }
            return objectMapper.readValue(config.getContent(), McpServerConfig.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static class McpServerConfig {
        private String authType;
        private String token;

        public String getAuthType() {
            return authType;
        }

        public void setAuthType(String authType) {
            this.authType = authType;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
