package ai.chat2db.server.web.api.controller.task.biz;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

final class ExportFileNameUtils {

    private static final int MAX_BASE_NAME_CODE_POINTS = 120;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern INVALID_FILE_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern TRAILING_DOTS_OR_SPACES = Pattern.compile("[. ]+$");
    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$");

    private ExportFileNameUtils() {
    }

    static String resolveBaseName(String preferredName, String fallbackName) {
        String baseName = StringUtils.defaultIfBlank(preferredName, fallbackName);
        baseName = StringUtils.defaultIfBlank(baseName, "export").trim();
        baseName = INVALID_FILE_NAME_CHARS.matcher(baseName).replaceAll("_");
        baseName = TRAILING_DOTS_OR_SPACES.matcher(baseName).replaceAll("");
        if (StringUtils.isBlank(baseName)) {
            baseName = "export";
        }
        if (WINDOWS_RESERVED_NAME.matcher(baseName).matches()) {
            baseName = "_" + baseName;
        }
        return truncateByCodePoints(baseName, MAX_BASE_NAME_CODE_POINTS);
    }

    static String buildTempFilePrefix(String baseName) {
        return buildTempFilePrefix(baseName, LocalDateTime.now());
    }

    static String buildTempFilePrefix(String baseName, LocalDateTime now) {
        return resolveBaseName(baseName, "export") + "_" + now.format(TIMESTAMP_FORMATTER) + "_chat2db_tmp_";
    }

    private static String truncateByCodePoints(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }
}
