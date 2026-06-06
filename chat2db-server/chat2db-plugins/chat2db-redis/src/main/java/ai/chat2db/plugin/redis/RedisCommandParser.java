package ai.chat2db.plugin.redis;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class RedisCommandParser {

    private RedisCommandParser() {
    }

    public static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        if (StringUtils.isBlank(script)) {
            return statements;
        }
        StringBuilder current = new StringBuilder();
        Character quote = null;
        boolean escaped = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            if (quote != null) {
                current.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = null;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                current.append(ch);
                continue;
            }
            if (ch == ';' || ch == '\n' || ch == '\r') {
                addStatement(statements, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        addStatement(statements, current);
        return statements;
    }

    public static List<String> tokenize(String statement) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character quote = null;
        boolean escaped = false;
        for (int i = 0; i < statement.length(); i++) {
            char ch = statement.charAt(i);
            if (quote != null) {
                if (escaped) {
                    current.append(ch);
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = null;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (Character.isWhitespace(ch)) {
                addToken(tokens, current);
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            current.append(ch);
        }
        if (quote != null) {
            throw new IllegalArgumentException("Unclosed quoted string");
        }
        addToken(tokens, current);
        return tokens;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (StringUtils.isNotBlank(statement)) {
            statements.add(statement);
        }
    }

    private static void addToken(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }
}
