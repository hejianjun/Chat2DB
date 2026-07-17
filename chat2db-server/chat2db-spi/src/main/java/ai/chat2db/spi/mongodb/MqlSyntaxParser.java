package ai.chat2db.spi.mongodb;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * MongoDB-driver-independent syntax helpers shared by the MongoDB executor and MCP safety guard.
 */
public final class MqlSyntaxParser {

    private MqlSyntaxParser() {
    }

    public static List<StatementPosition> splitStatements(String script) {
        List<StatementPosition> statements = new ArrayList<>();
        if (StringUtils.isBlank(script)) {
            return statements;
        }
        int start = 0;
        int startLine = 1;
        int line = 1;
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            if (ch == '\n') {
                line++;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == ';' && depth == 0) {
                addStatement(statements, script, start, i, startLine, line);
                start = i + 1;
                startLine = line;
            }
        }
        if (quote != 0 || depth != 0) {
            throw new IllegalArgumentException("Unbalanced MQL expression");
        }
        addStatement(statements, script, start, script.length(), startLine, line);
        return statements;
    }

    public static List<String> splitArguments(String arguments) {
        List<String> values = new ArrayList<>();
        if (StringUtils.isBlank(arguments)) {
            return values;
        }
        int start = 0;
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < arguments.length(); i++) {
            char ch = arguments.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                values.add(arguments.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (quote != 0 || depth != 0) {
            throw new IllegalArgumentException("Unbalanced MQL arguments");
        }
        values.add(arguments.substring(start).trim());
        return values;
    }

    public static List<MethodCall> parseMethodCalls(String expression) {
        List<MethodCall> calls = new ArrayList<>();
        int position = 0;
        while (position < expression.length()) {
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
                position++;
            }
            if (position >= expression.length()) {
                break;
            }
            if (expression.charAt(position) == '.') {
                position++;
            }
            int nameStart = position;
            while (position < expression.length() && isMethodNamePart(expression.charAt(position))) {
                position++;
            }
            String name = expression.substring(nameStart, position);
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
                position++;
            }
            if (StringUtils.isBlank(name) || position >= expression.length() || expression.charAt(position) != '(') {
                throw new IllegalArgumentException("Invalid MQL method expression: " + expression);
            }
            int close = findClosingParenthesis(expression, position);
            calls.add(new MethodCall(name, splitArguments(expression.substring(position + 1, close))));
            position = close + 1;
        }
        return calls;
    }

    /**
     * Finds a document key outside quoted string values. Both quoted and unquoted MQL keys are supported.
     */
    public static boolean containsDocumentKey(String value, String expectedKey) {
        if (StringUtils.isBlank(value) || StringUtils.isBlank(expectedKey)) {
            return false;
        }
        for (int i = 0; i < value.length();) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) || ch == '{' || ch == '[' || ch == ',') {
                i++;
                continue;
            }
            String candidate;
            int end;
            if (ch == '\'' || ch == '"' || ch == '`') {
                end = findClosingQuote(value, i);
                candidate = unescape(value.substring(i + 1, end));
                end++;
            } else if (isKeyPart(ch)) {
                end = i + 1;
                while (end < value.length() && isKeyPart(value.charAt(end))) {
                    end++;
                }
                candidate = value.substring(i, end);
            } else {
                i++;
                continue;
            }
            int colon = end;
            while (colon < value.length() && Character.isWhitespace(value.charAt(colon))) {
                colon++;
            }
            if (colon < value.length() && value.charAt(colon) == ':' && expectedKey.equals(candidate)) {
                return true;
            }
            i = end;
        }
        return false;
    }

    private static int findClosingParenthesis(String value, int open) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = open; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unclosed parenthesis in MQL expression");
    }

    private static int findClosingQuote(String value, int open) {
        char quote = value.charAt(open);
        boolean escaped = false;
        for (int i = open + 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == quote) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unclosed quote in MQL expression");
    }

    private static boolean isMethodNamePart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private static boolean isKeyPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '.' || value == '-';
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
    }

    private static void addStatement(List<StatementPosition> statements, String script, int start, int end,
                                     int startLine, int endLine) {
        String statement = script.substring(start, end).trim();
        if (StringUtils.isNotBlank(statement)) {
            statements.add(new StatementPosition(statement, startLine, endLine));
        }
    }

    public record StatementPosition(String statement, int startLine, int endLine) {
    }

    public record MethodCall(String name, List<String> arguments) {
    }
}
