package ai.chat2db.server.tools.common.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.ContentType;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.QueryFilter;
import org.zalando.logbook.common.MediaTypeQuery;
import org.zalando.logbook.core.HeaderFilters;
import org.zalando.logbook.core.QueryFilters;

/** Filters sensitive values before Logbook passes HTTP traffic to a log sink. */
public final class HttpLogMasking {

    private static final String MASK = "XXX";
    private static final String OMITTED = "[body omitted]";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Predicate<String> FORM_URL_ENCODED =
        MediaTypeQuery.compile("application/x-www-form-urlencoded");
    private static final Set<String> SENSITIVE_NAMES = Set.of(
        "password", "passwd", "pwd", "secret", "clientsecret", "secretkey", "privatekey",
        "token", "accesstoken", "refreshtoken", "idtoken", "apikey", "xapikey", "authorization",
        "credential", "cookie", "setcookie"
    );

    private HttpLogMasking() {
    }

    public static BodyFilter bodyFilter() {
        QueryFilter formFilter = queryFilter();
        return (contentType, body) -> {
            if (body == null || body.isEmpty()) {
                return body;
            }
            if (ContentType.isJsonMediaType(contentType)) {
                return maskJson(body);
            }
            if (contentType != null && FORM_URL_ENCODED.test(contentType)) {
                return formFilter.filter(body);
            }
            return OMITTED;
        };
    }

    public static HeaderFilter headerFilter() {
        return HeaderFilters.replaceHeaders(HttpLogMasking::isSensitiveName, MASK);
    }

    public static QueryFilter queryFilter() {
        return QueryFilters.replaceQuery(HttpLogMasking::isSensitiveQueryName, MASK);
    }

    private static String maskJson(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            maskNode(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return OMITTED;
        }
    }

    private static void maskNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fieldNames().forEachRemaining(name -> {
                if (isSensitiveName(name)) {
                    object.put(name, MASK);
                } else {
                    maskNode(object.get(name));
                }
            });
        } else if (node instanceof ArrayNode array) {
            array.forEach(HttpLogMasking::maskNode);
        }
    }

    private static boolean isSensitiveQueryName(String name) {
        try {
            return isSensitiveName(URLDecoder.decode(name, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isSensitiveName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE_NAMES.contains(normalized);
    }
}
