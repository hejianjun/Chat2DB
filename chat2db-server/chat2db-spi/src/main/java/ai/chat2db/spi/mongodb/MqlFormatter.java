package ai.chat2db.spi.mongodb;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.commons.lang3.StringUtils;

/**
 * Formats MongoDB shell MQL without interpreting MongoDB operators as SQL tokens.
 */
public final class MqlFormatter {

    private static final String INDENT = "  ";

    private MqlFormatter() {
    }

    public static String format(String mql) {
        if (StringUtils.isBlank(mql)) {
            return mql;
        }
        StringBuilder result = new StringBuilder(mql.length() + 32);
        Deque<Container> containers = new ArrayDeque<>();
        int indent = 0;
        boolean pendingSpace = false;
        for (int i = 0; i < mql.length(); i++) {
            char ch = mql.charAt(i);
            if (Character.isWhitespace(ch)) {
                pendingSpace = true;
                continue;
            }
            if (isQuote(ch)) {
                appendPendingSpace(result, pendingSpace, ch);
                i = appendQuoted(mql, i, result);
                pendingSpace = false;
                continue;
            }
            if (ch == '/' && i + 1 < mql.length() && mql.charAt(i + 1) == '/') {
                appendPendingSpace(result, pendingSpace, ch);
                i = appendLineComment(mql, i, result);
                appendNewline(result, indent);
                pendingSpace = false;
                continue;
            }
            if (ch == '/' && i + 1 < mql.length() && mql.charAt(i + 1) == '*') {
                appendPendingSpace(result, pendingSpace, ch);
                i = appendBlockComment(mql, i, result);
                pendingSpace = true;
                continue;
            }
            if (ch == '/') {
                int regexEnd = findRegexEnd(mql, i);
                if (regexEnd > i) {
                    appendPendingSpace(result, pendingSpace, ch);
                    result.append(mql, i, regexEnd + 1);
                    i = regexEnd;
                    while (i + 1 < mql.length() && Character.isLetter(mql.charAt(i + 1))) {
                        result.append(mql.charAt(++i));
                    }
                    pendingSpace = false;
                    continue;
                }
            }

            switch (ch) {
                case '{', '[' -> {
                    appendPendingSpace(result, pendingSpace, ch);
                    result.append(ch);
                    boolean multiline = nextNonWhitespace(mql, i + 1) != matchingClose(ch);
                    containers.push(new Container(ch, multiline));
                    if (multiline) {
                        indent++;
                        appendNewline(result, indent);
                    }
                    pendingSpace = false;
                }
                case '(' -> {
                    trimSpaces(result);
                    result.append(ch);
                    containers.push(new Container(ch, false));
                    pendingSpace = false;
                }
                case '}', ']' -> {
                    Container container = popContainer(containers, matchingOpen(ch));
                    if (container.multiline()) {
                        indent = Math.max(0, indent - 1);
                        appendNewline(result, indent);
                    } else {
                        trimSpaces(result);
                    }
                    result.append(ch);
                    pendingSpace = false;
                }
                case ')' -> {
                    popContainer(containers, '(');
                    trimSpaces(result);
                    result.append(ch);
                    pendingSpace = false;
                }
                case ',' -> {
                    trimSpaces(result);
                    result.append(ch);
                    if (!containers.isEmpty() && containers.peek().multiline()) {
                        appendNewline(result, indent);
                    } else {
                        result.append(' ');
                    }
                    pendingSpace = false;
                }
                case ':' -> {
                    trimSpaces(result);
                    result.append(": ");
                    pendingSpace = false;
                }
                case '.' -> {
                    trimSpaces(result);
                    result.append(ch);
                    pendingSpace = false;
                }
                case ';' -> {
                    trimSpaces(result);
                    result.append(ch);
                    appendNewline(result, 0);
                    pendingSpace = false;
                }
                default -> {
                    appendPendingSpace(result, pendingSpace, ch);
                    result.append(ch);
                    pendingSpace = false;
                }
            }
        }
        if (!containers.isEmpty()) {
            throw new IllegalArgumentException("Unbalanced MQL expression");
        }
        trimWhitespace(result);
        return result.toString();
    }

    private static int appendQuoted(String value, int start, StringBuilder result) {
        char quote = value.charAt(start);
        result.append(quote);
        boolean escaped = false;
        for (int i = start + 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            result.append(ch);
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

    private static int appendLineComment(String value, int start, StringBuilder result) {
        for (int i = start; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n') {
                return i;
            }
            result.append(ch);
        }
        return value.length() - 1;
    }

    private static int appendBlockComment(String value, int start, StringBuilder result) {
        for (int i = start; i < value.length(); i++) {
            result.append(value.charAt(i));
            if (i > start && value.charAt(i - 1) == '*' && value.charAt(i) == '/') {
                return i;
            }
        }
        throw new IllegalArgumentException("Unclosed block comment in MQL expression");
    }

    private static int findRegexEnd(String value, int start) {
        boolean escaped = false;
        boolean characterClass = false;
        for (int i = start + 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '[') {
                characterClass = true;
            } else if (ch == ']') {
                characterClass = false;
            } else if (ch == '/' && !characterClass) {
                return i;
            } else if (ch == '\r' || ch == '\n') {
                return -1;
            }
        }
        return -1;
    }

    private static void appendPendingSpace(StringBuilder result, boolean pendingSpace, char current) {
        if (!pendingSpace || result.isEmpty()) {
            return;
        }
        char previous = result.charAt(result.length() - 1);
        if (!Character.isWhitespace(previous) && "([{.:,".indexOf(previous) < 0
                && ")]},;.".indexOf(current) < 0) {
            result.append(' ');
        }
    }

    private static void appendNewline(StringBuilder result, int indent) {
        trimWhitespace(result);
        if (!result.isEmpty()) {
            result.append('\n');
        }
        result.append(INDENT.repeat(Math.max(0, indent)));
    }

    private static void trimSpaces(StringBuilder result) {
        while (!result.isEmpty() && result.charAt(result.length() - 1) == ' ') {
            result.setLength(result.length() - 1);
        }
    }

    private static void trimWhitespace(StringBuilder result) {
        while (!result.isEmpty() && Character.isWhitespace(result.charAt(result.length() - 1))) {
            result.setLength(result.length() - 1);
        }
    }

    private static char nextNonWhitespace(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return value.charAt(i);
            }
        }
        return 0;
    }

    private static Container popContainer(Deque<Container> containers, char expectedOpen) {
        if (containers.isEmpty() || containers.peek().open() != expectedOpen) {
            throw new IllegalArgumentException("Unbalanced MQL expression");
        }
        return containers.pop();
    }

    private static char matchingOpen(char close) {
        return close == '}' ? '{' : '[';
    }

    private static char matchingClose(char open) {
        return open == '{' ? '}' : ']';
    }

    private static boolean isQuote(char value) {
        return value == '\'' || value == '"' || value == '`';
    }

    private record Container(char open, boolean multiline) {
    }
}
