package ai.chat2db.server.mcp.config;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.domain.repository.Dbutils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        McpServerConfig config = getStoredConfig();
        if (config != null) {
            if (StringUtils.equals(config.getAuthType(), AUTH_TYPE_BEARER)) {
                log.info("MCP auth config loaded from system config: authType={}, tokenConfigured={}, tokenLength={}",
                    config.getAuthType(), StringUtils.isNotBlank(config.getToken()), StringUtils.length(config.getToken()));
                return StringUtils.trimToEmpty(config.getToken());
            }
            log.info("MCP auth disabled by system config: authType={}", config.getAuthType());
            return "";
        }
        log.info("MCP auth config loaded from properties: tokenConfigured={}, tokenLength={}",
            StringUtils.isNotBlank(properties.getToken()), StringUtils.length(properties.getToken()));
        return StringUtils.trimToEmpty(properties.getToken());
    }

    private McpServerConfig getStoredConfig() {
        try {
            AtomicReference<Config> configRef = new AtomicReference<>();
            Dbutils.runWithSession(() -> configRef.set(configService.find(MCP_SERVER_CONFIG_CODE)));
            Config config = configRef.get();
            if (config == null || StringUtils.isBlank(config.getContent())) {
                return null;
            }
            return objectMapper.readValue(config.getContent(), McpServerConfig.class);
        } catch (Exception e) {
            log.warn("Failed to read MCP auth system config, fallback to properties. code={}", MCP_SERVER_CONFIG_CODE, e);
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
