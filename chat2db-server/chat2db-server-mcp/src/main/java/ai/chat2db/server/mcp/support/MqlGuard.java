package ai.chat2db.server.mcp.support;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.chat2db.server.mcp.config.McpProperties.Mongo;
import ai.chat2db.server.mcp.config.McpProperties.Sql;
import ai.chat2db.server.mcp.config.McpServerConfigService;
import ai.chat2db.spi.mongodb.MqlSyntaxParser;
import ai.chat2db.spi.mongodb.MqlSyntaxParser.MethodCall;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * MongoDB-specific MCP safety checks. The console executor remains more permissive.
 */
@Component
public class MqlGuard {

    private static final Set<String> READ_METHODS = Set.of("find", "findOne", "countDocuments", "aggregate");
    private static final Set<String> FIND_MODIFIERS = Set.of("sort", "projection", "skip", "limit");
    private static final Set<String> FORBIDDEN_AGGREGATION_STAGES = Set.of("$out", "$merge");

    private final McpServerConfigService mcpServerConfigService;
    private final SqlGuard sqlGuard;

    public MqlGuard(McpServerConfigService mcpServerConfigService, SqlGuard sqlGuard) {
        this.mcpServerConfigService = mcpServerConfigService;
        this.sqlGuard = sqlGuard;
    }

    public String requireSingleReadOnlyMql(String mql) {
        List<MqlSyntaxParser.StatementPosition> statements = MqlSyntaxParser.splitStatements(mql);
        if (statements.size() != 1) {
            throw new IllegalArgumentException("This MCP MongoDB query tool accepts exactly one MQL statement.");
        }
        String statement = statements.get(0).statement();
        requireReadOnlyMql(statement);
        return statement;
    }

    public void requireReadOnlyMql(String statement) {
        if (!StringUtils.startsWith(StringUtils.trim(statement), "db.")) {
            throw new IllegalArgumentException("MongoDB MQL must start with db.");
        }
        String expression = StringUtils.trim(statement).substring(3).trim();
        if (expression.startsWith("runCommand(")) {
            throw new IllegalArgumentException("db.runCommand() is disabled for all MongoDB MCP tools.");
        }

        List<MethodCall> calls = collectionCalls(expression);
        MethodCall operation = calls.get(0);
        if (!READ_METHODS.contains(operation.name())) {
            throw new IllegalArgumentException("Only find/findOne/countDocuments/aggregate are allowed by this tool.");
        }
        validateArguments(operation);
        validateModifiers(operation.name(), calls.subList(1, calls.size()));
        if ("aggregate".equals(operation.name())) {
            String pipeline = operation.arguments().get(0);
            for (String stage : FORBIDDEN_AGGREGATION_STAGES) {
                if (MqlSyntaxParser.containsDocumentKey(pipeline, stage)) {
                    throw new IllegalArgumentException("Aggregation stage " + stage + " is not read-only.");
                }
            }
        }
    }

    public void requireMongoDatasource(ResolvedDataSource resolvedDataSource) {
        String type = resolvedDataSource.getDataSource() == null ? null : resolvedDataSource.getDataSource().getType();
        if (!"MONGODB".equalsIgnoreCase(StringUtils.trim(type))) {
            throw new IllegalArgumentException("This tool requires a MongoDB datasource.");
        }
    }

    public void requireWriteAllowed(Long dataSourceId) {
        if (sqlGuard.isReleaseDatasource(dataSourceId)) {
            throw new IllegalStateException("MongoDB MCP writes are disabled for Release/production datasources.");
        }
        Sql sharedPolicy = mcpServerConfigService.getEffectiveSql();
        if (!sharedPolicy.isAllowWrite()) {
            throw new IllegalStateException(
                "MCP write operations are disabled. Enable P2 write operations in MCP settings or set "
                    + "chat2db.mcp.sql.allow-write=true.");
        }
    }

    public void requireAdminAllowed(Long dataSourceId) {
        if (sqlGuard.isReleaseDatasource(dataSourceId)) {
            throw new IllegalStateException(
                "MongoDB MCP admin operations are disabled for Release/production datasources.");
        }
        Sql sharedPolicy = mcpServerConfigService.getEffectiveSql();
        if (!sharedPolicy.isAllowDdl()) {
            throw new IllegalStateException(
                "MCP schema/admin operations are disabled. Enable P2 schema management in MCP settings or set "
                    + "chat2db.mcp.sql.allow-ddl=true.");
        }
    }

    public int normalizePageSize(Integer pageSize) {
        return sqlGuard.normalizePageSize(pageSize);
    }

    public int maxBatchDocuments() {
        Mongo mongo = mcpServerConfigService.getEffectiveMongo();
        return Math.max(1, mongo.getMaxBatchDocuments());
    }

    public void requireNonEmptyFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            throw new IllegalArgumentException(
                "A non-empty filter is required for MongoDB update/replace/delete operations.");
        }
    }

    public void requireUpdateDocument(Map<String, Object> update) {
        if (update == null || update.isEmpty()) {
            throw new IllegalArgumentException("update must not be empty.");
        }
        if (update.keySet().stream().anyMatch(key -> !StringUtils.startsWith(key, "$"))) {
            throw new IllegalArgumentException(
                "update must use MongoDB update operators; pipeline and replacement updates are not supported.");
        }
    }

    public void requireReplacementDocument(Map<String, Object> replacement) {
        if (replacement == null || replacement.isEmpty()) {
            throw new IllegalArgumentException("replacement document must not be empty.");
        }
        if (replacement.keySet().stream().anyMatch(key -> StringUtils.startsWith(key, "$"))) {
            throw new IllegalArgumentException("replacement document must not contain top-level update operators.");
        }
    }

    private List<MethodCall> collectionCalls(String expression) {
        if (expression.startsWith("getCollection(")) {
            List<MethodCall> calls = MqlSyntaxParser.parseMethodCalls(expression);
            if (calls.size() < 2 || !"getCollection".equals(calls.get(0).name())
                || calls.get(0).arguments().size() != 1) {
                throw new IllegalArgumentException("MQL must include a collection and one read-only method.");
            }
            return calls.subList(1, calls.size());
        }
        int dot = expression.indexOf('.');
        if (dot <= 0) {
            throw new IllegalArgumentException("MQL must include a collection and one read-only method.");
        }
        return MqlSyntaxParser.parseMethodCalls(expression.substring(dot + 1));
    }

    private void validateArguments(MethodCall operation) {
        int size = CollectionUtils.size(operation.arguments());
        switch (operation.name()) {
            case "find" -> requireArgumentRange(operation.name(), size, 0, 2);
            case "findOne", "countDocuments" -> requireArgumentRange(operation.name(), size, 0, 1);
            case "aggregate" -> requireArgumentRange(operation.name(), size, 1, 1);
            default -> throw new IllegalArgumentException("Unsupported read-only MongoDB method: " + operation.name());
        }
    }

    private void validateModifiers(String operation, List<MethodCall> modifiers) {
        if (!"find".equals(operation) && !modifiers.isEmpty()) {
            throw new IllegalArgumentException(operation + " does not support method chaining in the MCP query tool.");
        }
        for (MethodCall modifier : modifiers) {
            if (!FIND_MODIFIERS.contains(modifier.name())) {
                throw new IllegalArgumentException("Unsupported read-only find modifier: " + modifier.name());
            }
            requireArgumentRange(modifier.name(), modifier.arguments().size(), 1, 1);
        }
    }

    private void requireArgumentRange(String method, int actual, int minimum, int maximum) {
        if (actual < minimum || actual > maximum) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                "%s expects between %d and %d arguments.", method, minimum, maximum));
        }
    }
}
