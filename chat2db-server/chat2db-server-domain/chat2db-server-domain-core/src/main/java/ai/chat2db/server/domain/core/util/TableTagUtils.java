package ai.chat2db.server.domain.core.util;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class TableTagUtils {

    private TableTagUtils() {
    }

    public static Map<String, String> calculateTags(List<String> tableNames) {
        Map<String, String> result = new LinkedHashMap<>();
        List<NameParts> names = new ArrayList<>(tableNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(NameParts::new)
                .collect(Collectors.toMap(name -> name.tokens, name -> name, (first, ignored) -> first,
                        LinkedHashMap::new))
                .values());
        Map<NameParts, Integer> candidateSizes = new LinkedHashMap<>();
        for (NameParts name : names) {
            for (int size = name.tokens.size(); size >= 1; size--) {
                List<String> prefix = name.tokens.subList(0, size);
                if (names.stream().filter(other -> other.startsWith(prefix)).count() >= 2) {
                    candidateSizes.put(name, size);
                    break;
                }
            }
        }

        Set<List<String>> candidateTags = candidateSizes.entrySet().stream()
                .map(entry -> entry.getKey().tokens.subList(0, entry.getValue()))
                .collect(Collectors.toSet());
        Set<List<String>> longestTags = candidateTags.stream()
                .filter(tag -> candidateTags.stream().noneMatch(other -> other.size() > tag.size() && startsWith(other, tag)))
                .collect(Collectors.toSet());
        Map<List<String>, String> tagLabels = new LinkedHashMap<>();
        for (Map.Entry<NameParts, Integer> entry : candidateSizes.entrySet()) {
            NameParts name = entry.getKey();
            int tagSize = entry.getValue();
            List<String> tag = name.tokens.subList(0, tagSize);
            if (!longestTags.contains(tag)) {
                continue;
            }
            tagLabels.putIfAbsent(tag, name.prefix(tagSize));
            result.put(name.original, tagLabels.get(tag));
        }
        names.stream().filter(name -> !result.containsKey(name.original)).forEach(name -> result.put(name.original, null));
        return result;
    }

    private static boolean startsWith(List<String> tokens, List<String> prefix) {
        return tokens.size() >= prefix.size() && tokens.subList(0, prefix.size()).equals(prefix);
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
            return tokens.size() >= prefix.size() && tokens.subList(0, prefix.size()).equals(prefix);
        }

        private String prefix(int count) {
            return original.substring(0, ends.get(count - 1));
        }
    }
}
