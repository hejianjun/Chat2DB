package ai.chat2db.server.mcp.support;

import java.util.List;
import java.util.Locale;

import ai.chat2db.server.domain.api.enums.EnvironmentEnum;
import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.mcp.config.McpProperties.Sql;
import ai.chat2db.server.mcp.config.McpServerConfigService;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.SqlUtils;
import com.alibaba.druid.DbType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Centralizes MCP SQL safety checks before any tool reaches the execution service.
 */
@Component
public class SqlGuard {

    private final McpServerConfigService mcpServerConfigService;
    private final DataSourceService dataSourceService;

    public SqlGuard(McpServerConfigService mcpServerConfigService, DataSourceService dataSourceService) {
        this.mcpServerConfigService = mcpServerConfigService;
        this.dataSourceService = dataSourceService;
    }

    public void requireReadOnlySql(String sql) {
        if (!isReadOnlySql(sql)) {
            throw new IllegalArgumentException("Only SELECT/SHOW/DESC/DESCRIBE/EXPLAIN/WITH SQL is allowed by this tool.");
        }
    }

    public void requireSingleReadOnlySql(String sql) {
        List<String> sqlList = parseSql(sql);
        if (sqlList.size() != 1) {
            throw new IllegalArgumentException("This MCP query tool accepts exactly one read-only SQL statement.");
        }
        requireReadOnlySql(sqlList.get(0));
    }

    public void requireWriteSqlList(List<String> sqlList) {
        if (CollectionUtils.isEmpty(sqlList)) {
            throw new IllegalArgumentException("sqlList must not be empty.");
        }
        for (String sql : sqlList) {
            List<String> parsedSqlList = parseSql(sql);
            if (parsedSqlList.size() != 1) {
                throw new IllegalArgumentException("Each sqlList item must contain exactly one SQL statement.");
            }
            if (!isWriteSql(parsedSqlList.get(0))) {
                throw new IllegalArgumentException("Only INSERT/UPDATE/DELETE SQL is allowed by this tool.");
            }
        }
    }

    public void requireWriteAllowed(Long dataSourceId, Boolean confirm) {
        // Production datasources remain protected even if write tools are enabled in configuration.
        if (isReleaseDatasource(dataSourceId)) {
            throw new IllegalStateException("P2 write operations are disabled for Release/production datasources.");
        }
        Sql sqlConfig = mcpServerConfigService.getEffectiveSql();
        if (!sqlConfig.isAllowWrite()) {
            throw new IllegalStateException("P2 write operations are disabled. Set chat2db.mcp.sql.allow-write=true to enable.");
        }
        requireConfirm(confirm, sqlConfig);
    }

    public void requireDdlAllowed(Long dataSourceId, Boolean confirm) {
        // DDL is guarded separately from DML because schema changes are higher risk.
        if (isReleaseDatasource(dataSourceId)) {
            throw new IllegalStateException("P2 DDL operations are disabled for Release/production datasources.");
        }
        Sql sqlConfig = mcpServerConfigService.getEffectiveSql();
        if (!sqlConfig.isAllowDdl()) {
            throw new IllegalStateException("P2 DDL operations are disabled. Set chat2db.mcp.sql.allow-ddl=true to enable.");
        }
        requireConfirm(confirm, sqlConfig);
    }

    public int normalizePageSize(Integer pageSize) {
        Sql sqlConfig = mcpServerConfigService.getEffectiveSql();
        int value = pageSize == null || pageSize <= 0 ? sqlConfig.getDefaultPageSize() : pageSize;
        return Math.min(value, sqlConfig.getMaxRows());
    }

    /**
     * Returns the first SQL keyword after trimming leading whitespace and one leading block comment.
     */
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

    public boolean isWriteSql(String sql) {
        String keyword = firstKeyword(sql);
        return "insert".equals(keyword) || "update".equals(keyword) || "delete".equals(keyword);
    }

    public boolean isReleaseDatasource(Long dataSourceId) {
        DataSource dataSource = dataSourceService.queryExistent(dataSourceId,
            DataSourceSelector.builder().environment(true).build());
        // Prefer the typed environment code, then fall back to free-text fields used by older records.
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

    private void requireConfirm(Boolean confirm, Sql sqlConfig) {
        if (sqlConfig.isRequireConfirmParam() && !Boolean.TRUE.equals(confirm)) {
            throw new IllegalArgumentException("This P2 operation requires user confirmation. Call again with confirm=true after the MCP client confirmation dialog.");
        }
    }

    private List<String> parseSql(String sql) {
        if (StringUtils.isBlank(sql)) {
            throw new IllegalArgumentException("SQL must not be blank.");
        }
        DbType dbType = JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        return SqlUtils.parse(sql, dbType);
    }
}
