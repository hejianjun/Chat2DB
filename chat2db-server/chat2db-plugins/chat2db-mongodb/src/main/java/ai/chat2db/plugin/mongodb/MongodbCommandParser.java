package ai.chat2db.plugin.mongodb;

import ai.chat2db.spi.mongodb.MqlSyntaxParser;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MongodbCommandParser {

    private static final Pattern OBJECT_ID = Pattern.compile("ObjectId\\(\\s*(['\"])([0-9a-fA-F]{24})\\1\\s*\\)");
    private static final Pattern ISO_DATE = Pattern.compile("ISODate\\(\\s*(['\"])([^'\"]+)\\1\\s*\\)");
    private static final Pattern NUMBER_LONG = Pattern.compile("NumberLong\\(\\s*(['\"]?)(-?\\d+)\\1\\s*\\)");

    private MongodbCommandParser() {
    }

    public static List<StatementPosition> splitStatements(String script) {
        return MqlSyntaxParser.splitStatements(script).stream()
                .map(value -> new StatementPosition(value.statement(), value.startLine(), value.endLine()))
                .toList();
    }

    public static List<String> splitArguments(String arguments) {
        return MqlSyntaxParser.splitArguments(arguments);
    }

    public static Object parseValue(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (isQuoted(trimmed)) {
            return unquote(trimmed);
        }
        return Document.parse("{\"value\":" + toExtendedJson(trimmed) + "}").get("value");
    }

    public static Document parseDocument(String value) {
        if (StringUtils.isBlank(value)) {
            return new Document();
        }
        Object parsed = parseValue(value);
        if (parsed instanceof Document document) {
            return document;
        }
        throw new IllegalArgumentException("Expected a MongoDB document: " + value);
    }

    public static List<MethodCall> parseMethodCalls(String expression) {
        List<MethodCall> calls = new ArrayList<>();
        MqlSyntaxParser.parseMethodCalls(expression)
                .forEach(value -> calls.add(new MethodCall(value.name(), value.arguments())));
        return calls;
    }

    private static boolean isQuoted(String value) {
        return value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'' || value.charAt(0) == '`')
                && value.charAt(value.length() - 1) == value.charAt(0);
    }

    private static String unquote(String value) {
        return value.substring(1, value.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\");
    }

    private static String toExtendedJson(String value) {
        String converted = replaceConstructor(value, OBJECT_ID, "$oid");
        converted = replaceConstructor(converted, ISO_DATE, "$date");
        converted = replaceConstructor(converted, NUMBER_LONG, "$numberLong");
        StringBuilder json = new StringBuilder(converted.length() + 16);
        char quote = 0;
        boolean escaped = false;
        boolean expectKey = false;
        for (int i = 0; i < converted.length(); i++) {
            char ch = converted.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    json.append(ch);
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    json.append(ch);
                    escaped = true;
                    continue;
                }
                if (ch == quote) {
                    json.append('"');
                    quote = 0;
                } else {
                    if (quote == '\'' && ch == '"') {
                        json.append("\\\"");
                    } else {
                        json.append(ch);
                    }
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                json.append('"');
                expectKey = false;
                continue;
            }
            if (ch == '{' || ch == ',') {
                json.append(ch);
                expectKey = true;
                continue;
            }
            if (expectKey && Character.isWhitespace(ch)) {
                json.append(ch);
                continue;
            }
            if (expectKey && isIdentifierStart(ch)) {
                int end = i + 1;
                while (end < converted.length() && isIdentifierPart(converted.charAt(end))) {
                    end++;
                }
                int colon = end;
                while (colon < converted.length() && Character.isWhitespace(converted.charAt(colon))) {
                    colon++;
                }
                if (colon < converted.length() && converted.charAt(colon) == ':') {
                    json.append('"').append(converted, i, end).append('"');
                    i = end - 1;
                    expectKey = false;
                    continue;
                }
            }
            if (!Character.isWhitespace(ch)) {
                expectKey = false;
            }
            json.append(ch);
        }
        return json.toString();
    }

    private static String replaceConstructor(String value, Pattern pattern, String extendedJsonKey) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = "{\"" + extendedJsonKey + "\":\"" + matcher.group(2) + "\"}";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '.';
    }

    public record StatementPosition(String statement, int startLine, int endLine) {
    }

    public record MethodCall(String name, List<String> arguments) {
    }
}
