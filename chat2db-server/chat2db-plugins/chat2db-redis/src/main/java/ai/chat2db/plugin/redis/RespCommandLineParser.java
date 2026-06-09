package ai.chat2db.plugin.redis;

import java.util.ArrayList;
import java.util.List;

final class RespCommandLineParser {

    private RespCommandLineParser() {
    }

    static List<String> parse(String resp) {
        List<String> result = new ArrayList<>();
        if (resp == null || resp.isBlank() || resp.charAt(0) != '*') {
            return result;
        }
        int cursor = readLineEnd(resp, 0);
        if (cursor < 0) {
            return result;
        }
        int count = Integer.parseInt(resp.substring(1, cursor));
        cursor += 2;
        for (int i = 0; i < count; i++) {
            if (cursor >= resp.length() || resp.charAt(cursor) != '$') {
                return result;
            }
            int lengthEnd = readLineEnd(resp, cursor);
            if (lengthEnd < 0) {
                return result;
            }
            int length = Integer.parseInt(resp.substring(cursor + 1, lengthEnd));
            cursor = lengthEnd + 2;
            if (cursor + length > resp.length()) {
                return result;
            }
            result.add(resp.substring(cursor, cursor + length));
            cursor += length + 2;
        }
        return result;
    }

    private static int readLineEnd(String value, int start) {
        return value.indexOf("\r\n", start);
    }
}
