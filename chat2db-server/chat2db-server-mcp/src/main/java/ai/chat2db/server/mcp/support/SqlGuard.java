package ai.chat2db.server.mcp.support;

import java.util.Locale;

import ai.chat2db.server.domain.api.enums.EnvironmentEnum;
import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.mcp.config.McpProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class SqlGuard {

    private final McpProperties properties;
    private final DataSourceService dataSourceService;

    public SqlGuard(McpProperties properties, DataSourceService dataSourceService) {
        this.properties = properties;
        this.dataSourceService = dataSourceService;
    }

    public void requireReadOnlySql(String sql) {
        if (!isReadOnlySql(sql)) {
            throw new IllegalArgumentException("Only SELECT/SHOW/DESC/DESCRIBE/EXPLAIN SQL is allowed by this tool.");
        }
    }

    public void requireWriteAllowed(Long dataSourceId, Boolean confirm) {
        if (isReleaseDatasource(dataSourceId)) {
            throw new IllegalStateException("P2 write operations are disabled for Release/production datasources.");
        }
        if (!properties.getSql().isAllowWrite()) {
            throw new IllegalStateException("P2 write operations are disabled. Set chat2db.mcp.sql.allow-write=true to enable.");
        }
        requireConfirm(confirm);
    }

    public void requireDdlAllowed(Long dataSourceId, Boolean confirm) {
        if (isReleaseDatasource(dataSourceId)) {
            throw new IllegalStateException("P2 DDL operations are disabled for Release/production datasources.");
        }
        if (!properties.getSql().isAllowDdl()) {
            throw new IllegalStateException("P2 DDL operations are disabled. Set chat2db.mcp.sql.allow-ddl=true to enable.");
        }
        requireConfirm(confirm);
    }

    public int normalizePageSize(Integer pageSize) {
        int value = pageSize == null || pageSize <= 0 ? properties.getSql().getDefaultPageSize() : pageSize;
        return Math.min(value, properties.getSql().getMaxRows());
    }

    public String firstKeyword(String sql) {
        String cleaned = StringUtils.trimToEmpty(sql).replaceFirst("(?s)^/\\*.*?\\*/", "").trim();
        int index = cleaned.indexOf(' ');
        return (index < 0 ? cleaned : cleaned.substring(0, index)).toLowerCase(Locale.ROOT);
    }

    public boolean isReadOnlySql(String sql) {
        String keyword = firstKeyword(sql);
        return "select".equals(keyword) || "show".equals(keyword) || "desc".equals(keyword)
            || "describe".equals(keyword) || "explain".equals(keyword) || "with".equals(keyword);
    }

    public boolean isReleaseDatasource(Long dataSourceId) {
        DataSource dataSource = dataSourceService.queryExistent(dataSourceId,
            DataSourceSelector.builder().environment(true).build());
        if (dataSource.getEnvironmentId() != null && EnvironmentEnum.RELEASE.getCode().equals(dataSource.getEnvironmentId())) {
            return true;
        }
        if (dataSource.getEnvironment() == null) {
            return isReleaseText(dataSource.getEnvType());
        }
        return isReleaseText(dataSource.getEnvType()) || isReleaseText(dataSource.getEnvironment().getName())
            || isReleaseText(dataSource.getEnvironment().getShortName());
    }

    private boolean isReleaseText(String text) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(text), Locale.ROOT);
        return StringUtils.containsAny(normalized, "release", "prod", "production", "正式", "生产");
    }

    private void requireConfirm(Boolean confirm) {
        if (properties.getSql().isRequireConfirmParam() && !Boolean.TRUE.equals(confirm)) {
            throw new IllegalArgumentException("This P2 operation requires user confirmation. Call again with confirm=true after the MCP client confirmation dialog.");
        }
    }
}
