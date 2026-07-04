package ai.chat2db.server.mcp.config;

import java.util.concurrent.atomic.AtomicReference;

import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.domain.repository.Dbutils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Reads runtime MCP settings saved by the desktop/web settings UI.
 */
@Slf4j
@Service
public class McpServerConfigService {

    private static final String MCP_SERVER_CONFIG_CODE = "mcp.server.config";
    private static final String AUTH_TYPE_BEARER = "bearer";

    private final McpProperties properties;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    public McpServerConfigService(McpProperties properties, ConfigService configService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    public String getEffectiveToken() {
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

    public McpProperties.Sql getEffectiveSql() {
        McpProperties.Sql effective = copySql(properties.getSql());
        McpServerConfig config = getStoredConfig();
        if (config == null || config.getSql() == null) {
            return effective;
        }

        McpSqlConfig sql = config.getSql();
        if (sql.getReadOnly() != null) {
            effective.setReadOnly(sql.getReadOnly());
        }
        if (sql.getAllowWrite() != null) {
            effective.setAllowWrite(sql.getAllowWrite());
        }
        if (sql.getAllowDdl() != null) {
            effective.setAllowDdl(sql.getAllowDdl());
        }
        if (sql.getDefaultPageSize() != null) {
            effective.setDefaultPageSize(sql.getDefaultPageSize());
        }
        if (sql.getMaxRows() != null) {
            effective.setMaxRows(sql.getMaxRows());
        }
        return effective;
    }

    private McpProperties.Sql copySql(McpProperties.Sql source) {
        McpProperties.Sql copy = new McpProperties.Sql();
        copy.setReadOnly(source.isReadOnly());
        copy.setAllowWrite(source.isAllowWrite());
        copy.setAllowDdl(source.isAllowDdl());
        copy.setDefaultPageSize(source.getDefaultPageSize());
        copy.setMaxRows(source.getMaxRows());
        return copy;
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
            log.warn("Failed to read MCP system config, fallback to properties. code={}", MCP_SERVER_CONFIG_CODE, e);
            return null;
        }
    }

    @Data
    public static class McpServerConfig {
        private String authType;
        private String token;
        private McpSqlConfig sql;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpSqlConfig {
        private Boolean readOnly;
        private Boolean allowWrite;
        private Boolean allowDdl;
        private Integer defaultPageSize;
        private Integer maxRows;
    }
}
