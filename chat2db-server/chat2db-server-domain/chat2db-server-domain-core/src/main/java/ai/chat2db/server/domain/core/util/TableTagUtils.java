package ai.chat2db.server.domain.core.util;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TableTagUtils {

    private TableTagUtils() {
    }

    public static Map<String, String> calculateTags(List<String> tableNames) {
        Map<String, String> result = new LinkedHashMap<>();
        List<NameParts> names = tableNames.stream().filter(StringUtils::isNotBlank).map(NameParts::new).toList();
        for (NameParts name : names) {
            String tag = null;
            for (int size = name.tokens.size() - 1; size >= 1; size--) {
                final int prefixSize = size;
                List<String> prefix = name.tokens.subList(0, size);
                if (names.stream().filter(other -> other.tokens.size() > prefixSize && other.startsWith(prefix)).count() >= 2) {
                    tag = name.prefix(size);
                    break;
                }
            }
            result.put(name.original, tag);
        }
        return result;
    }

    private static final class NameParts {
        private final String original;
        private final List<String> tokens = new ArrayList<>();
        private final List<Integer> ends = new ArrayList<>();

        private NameParts(String original) {
            this.original = original;
            int start = 0;
            for (int index = 0; index < original.length(); index++) {
                if ("_.-".indexOf(original.charAt(index)) >= 0) {
                    add(start, index);
                    start = index + 1;
                }
            }
            add(start, original.length());
        }

        private void add(int start, int end) {
            if (start < end) {
                tokens.add(original.substring(start, end).toLowerCase(Locale.ROOT));
                ends.add(end);
            }
        }

        private boolean startsWith(List<String> prefix) {
            return tokens.subList(0, prefix.size()).equals(prefix);
        }

        private String prefix(int count) {
            return original.substring(0, ends.get(count - 1));
        }
    }
}
