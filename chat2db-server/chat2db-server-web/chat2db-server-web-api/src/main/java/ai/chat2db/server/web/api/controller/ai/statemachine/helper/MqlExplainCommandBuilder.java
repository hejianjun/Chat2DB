package ai.chat2db.server.web.api.controller.ai.statemachine.helper;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import ai.chat2db.spi.mongodb.MqlSyntaxParser;

/**
 * 将 Chat2DB 支持的只读 MQL 转换为 MongoDB explain 命令。
 */
public final class MqlExplainCommandBuilder {

    private MqlExplainCommandBuilder() {
    }

    public static String build(String mql) {
        ParsedCollectionCommand command = parse(mql);
        if (command.calls().isEmpty()) {
            throw new IllegalArgumentException("Missing MongoDB collection method");
        }

        MqlSyntaxParser.MethodCall operation = command.calls().get(0);
        List<MqlSyntaxParser.MethodCall> modifiers = command.calls().subList(1, command.calls().size());
        // 优化分析默认收集执行统计；输入已带 explain 时，保留其显式模式或默认 queryPlanner。
        String verbosity = "executionStats";
        if (!modifiers.isEmpty() && "explain".equals(modifiers.get(modifiers.size() - 1).name())) {
            MqlSyntaxParser.MethodCall explain = modifiers.get(modifiers.size() - 1);
            if (explain.arguments().size() > 1) {
                throw new IllegalArgumentException("MongoDB explain accepts at most one verbosity argument");
            }
            verbosity = explain.arguments().isEmpty() ? "queryPlanner" : unquote(requiredArgument(explain, 0));
            if (!List.of("queryPlanner", "executionStats", "allPlansExecution").contains(verbosity)) {
                throw new IllegalArgumentException("Unsupported MongoDB explain verbosity");
            }
            // 终结方法不属于 find 命令选项，剥离后由外层 explain 命令统一承载。
            modifiers = modifiers.subList(0, modifiers.size() - 1);
        }
        String explainedCommand = switch (operation.name()) {
            case "find" -> buildFindCommand(command.collectionName(), operation.arguments(), modifiers, false);
            case "findOne" -> buildFindCommand(command.collectionName(), operation.arguments(), modifiers, true);
            case "aggregate" -> buildAggregateCommand(command.collectionName(), operation.arguments(), modifiers);
            default -> throw new IllegalArgumentException(
                    "MongoDB EXPLAIN only supports find, findOne and aggregate");
        };
        return "db.runCommand({ explain: " + explainedCommand + ", verbosity: \"" + verbosity + "\" })";
    }

    public static String extractCollectionName(String mql) {
        return parse(mql).collectionName();
    }

    private static String buildFindCommand(String collectionName, List<String> arguments,
                                           List<MqlSyntaxParser.MethodCall> modifiers, boolean findOne) {
        List<String> fields = new ArrayList<>();
        fields.add("find: \"" + escape(collectionName) + "\"");
        fields.add("filter: " + argumentOrDefault(arguments, 0, "{}"));
        if (arguments.size() > 1 && StringUtils.isNotBlank(arguments.get(1))) {
            fields.add("projection: " + arguments.get(1));
        }
        if (findOne) {
            fields.add("limit: 1");
        }
        for (MqlSyntaxParser.MethodCall modifier : modifiers) {
            switch (modifier.name()) {
                case "sort" -> fields.add("sort: " + requiredArgument(modifier, 0));
                case "projection" -> fields.add("projection: " + requiredArgument(modifier, 0));
                case "skip" -> fields.add("skip: " + requiredArgument(modifier, 0));
                case "limit" -> fields.add("limit: " + requiredArgument(modifier, 0));
                // 将查询超时保留在被解释的 find 命令中，避免优化分析丢失时间限制。
                case "maxTimeMS" -> fields.add("maxTimeMS: " + requiredArgument(modifier, 0));
                default -> throw new IllegalArgumentException(
                        "Unsupported find modifier for EXPLAIN: " + modifier.name());
            }
        }
        return "{ " + String.join(", ", fields) + " }";
    }

    private static String buildAggregateCommand(String collectionName, List<String> arguments,
                                                List<MqlSyntaxParser.MethodCall> modifiers) {
        if (!modifiers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aggregate modifiers are not supported; use pipeline stages");
        }
        return "{ aggregate: \"" + escape(collectionName) + "\", pipeline: "
                + requiredArgument(arguments, 0, "pipeline") + ", cursor: {} }";
    }

    private static ParsedCollectionCommand parse(String mql) {
        List<MqlSyntaxParser.StatementPosition> statements = MqlSyntaxParser.splitStatements(mql);
        if (statements.size() != 1) {
            throw new IllegalArgumentException("MongoDB EXPLAIN requires exactly one MQL statement");
        }
        String statement = statements.get(0).statement();
        if (!statement.startsWith("db.")) {
            throw new IllegalArgumentException("MQL must start with db.");
        }

        String expression = statement.substring(3).trim();
        if (expression.startsWith("getCollection(")) {
            List<MqlSyntaxParser.MethodCall> calls =
                    new ArrayList<>(MqlSyntaxParser.parseMethodCalls(expression));
            MqlSyntaxParser.MethodCall selector = calls.remove(0);
            return new ParsedCollectionCommand(unquote(requiredArgument(selector, 0)), calls);
        }

        int dot = expression.indexOf('.');
        if (dot <= 0) {
            throw new IllegalArgumentException("MQL must include a collection and method");
        }
        return new ParsedCollectionCommand(expression.substring(0, dot).trim(),
                MqlSyntaxParser.parseMethodCalls(expression.substring(dot + 1)));
    }

    private static String argumentOrDefault(List<String> arguments, int index, String defaultValue) {
        return arguments.size() <= index || StringUtils.isBlank(arguments.get(index))
                ? defaultValue
                : arguments.get(index);
    }

    private static String requiredArgument(MqlSyntaxParser.MethodCall call, int index) {
        return requiredArgument(call.arguments(), index, call.name());
    }

    private static String requiredArgument(List<String> arguments, int index, String name) {
        if (arguments.size() <= index || StringUtils.isBlank(arguments.get(index))) {
            throw new IllegalArgumentException("Missing MongoDB argument: " + name);
        }
        return arguments.get(index);
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("MongoDB collection name must be a string");
        }
        char quote = trimmed.charAt(0);
        if ((quote != '\'' && quote != '"' && quote != '`')
                || trimmed.charAt(trimmed.length() - 1) != quote) {
            throw new IllegalArgumentException("MongoDB collection name must be a string");
        }
        return trimmed.substring(1, trimmed.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ParsedCollectionCommand(String collectionName, List<MqlSyntaxParser.MethodCall> calls) {
    }
}
