package ai.chat2db.server.web.api.controller.ai.statemachine.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Extracts table names from EXPLAIN output before falling back to SQL parsing.
 */
public final class ExplainTableNameExtractor {

    private static final Set<String> TABLE_HEADERS = Set.of("table", "table_name", "tablename", "relation_name");
    private static final Pattern PLAN_ON_TABLE_PATTERN = Pattern.compile(
            "(?i)\\bon\\s+((?:`[^`]+`|\"[^\"]+\"|\\[[^]]+]|[\\w$]+)(?:\\s*\\.\\s*(?:`[^`]+`|\"[^\"]+\"|\\[[^]]+]|[\\w$]+))*)");

    private ExplainTableNameExtractor() {
    }

    public static List<String> extractTableNames(String sql, ExplainResult result) {
        List<String> tableNames = new ArrayList<>();

        addFromPlanRows(tableNames, result);
        addFromFormattedPlan(tableNames, result);
        addFromSql(tableNames, sql);

        return tableNames;
    }

    private static void addFromPlanRows(List<String> tableNames, ExplainResult result) {
        if (result == null || CollectionUtils.isEmpty(result.getPlanRows()) || result.getPlanRows().size() < 2) {
            return;
        }

        List<String> headers = result.getPlanRows().get(0);
        if (CollectionUtils.isEmpty(headers)) {
            return;
        }

        for (int i = 0; i < headers.size(); i++) {
            if (!TABLE_HEADERS.contains(normalizeHeader(headers.get(i)))) {
                continue;
            }
            for (int rowIndex = 1; rowIndex < result.getPlanRows().size(); rowIndex++) {
                List<String> row = result.getPlanRows().get(rowIndex);
                if (row != null && row.size() > i) {
                    addTableName(tableNames, row.get(i));
                }
            }
        }
    }

    private static void addFromFormattedPlan(List<String> tableNames, ExplainResult result) {
        if (result == null || StringUtils.isBlank(result.getFormattedPlan())) {
            return;
        }

        Matcher matcher = PLAN_ON_TABLE_PATTERN.matcher(result.getFormattedPlan());
        while (matcher.find()) {
            addTableName(tableNames, matcher.group(1));
        }
    }

    private static void addFromSql(List<String> tableNames, String sql) {
        if (StringUtils.isBlank(sql)) {
            return;
        }

        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            TablesNamesFinder finder = new TablesNamesFinder();
            for (Statement statement : statements.getStatements()) {
                finder.getTableList(statement).forEach(tableName -> addTableName(tableNames, tableName));
            }
        } catch (Exception ignored) {
        }
    }

    private static String normalizeHeader(String header) {
        return StringUtils.defaultString(header).trim().toLowerCase(Locale.ROOT).replace(" ", "_");
    }

    private static void addTableName(List<String> tableNames, String rawTableName) {
        String tableName = normalizeTableName(rawTableName);
        if (StringUtils.isBlank(tableName) || tableNames.contains(tableName)) {
            return;
        }
        tableNames.add(tableName);
    }

    private static String normalizeTableName(String rawTableName) {
        if (StringUtils.isBlank(rawTableName)) {
            return null;
        }

        String tableName = rawTableName.trim();
        int whitespaceIndex = StringUtils.indexOfAny(tableName, ' ', '\t', '\r', '\n');
        if (whitespaceIndex > 0) {
            tableName = tableName.substring(0, whitespaceIndex);
        }
        tableName = tableName.replace("`", "").replace("\"", "").replace("[", "").replace("]", "");

        int lastDotIndex = tableName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < tableName.length() - 1) {
            tableName = tableName.substring(lastDotIndex + 1).trim();
        }

        if (StringUtils.startsWith(tableName, "<") || "null".equalsIgnoreCase(tableName)) {
            return null;
        }
        return tableName;
    }
}
