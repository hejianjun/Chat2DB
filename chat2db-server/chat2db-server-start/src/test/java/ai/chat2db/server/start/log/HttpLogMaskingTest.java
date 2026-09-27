package ai.chat2db.server.start.log;

import ai.chat2db.server.tools.common.util.HttpLogMasking;
import org.junit.jupiter.api.Test;
import org.zalando.logbook.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HttpLogMaskingTest {

    @Test
    void masksSensitiveJsonAtEveryLevelWithoutHidingOrdinaryFields() {
        String input = "{\"sql\":\"select 1\",\"apiKey\":\"top-secret\","
            + "\"items\":[{\"PASSWORD\":\"hunter2\"},{\"token\":{\"value\":\"nested-secret\"}}]}";

        String result = HttpLogMasking.bodyFilter().filter("application/json", input);

        assertEquals("{\"sql\":\"select 1\",\"apiKey\":\"XXX\","
            + "\"items\":[{\"PASSWORD\":\"XXX\"},{\"token\":\"XXX\"}]}", result);
        assertFalse(result.contains("top-secret"));
        assertFalse(result.contains("hunter2"));
    }

    @Test
    void masksFormQueryAndHeaders() {
        assertEquals("user=alice&password=XXX&API_KEY=XXX",
            HttpLogMasking.bodyFilter().filter("application/x-www-form-urlencoded",
                "user=alice&password=hunter2&API_KEY=top-secret"));
        assertEquals("page=1&api%4Bey=XXX&TOKEN=XXX",
            HttpLogMasking.queryFilter().filter("page=1&api%4Bey=top-secret&TOKEN=hunter2"));

        HttpHeaders headers = HttpHeaders.empty()
            .update("Authorization", "Bearer top-secret")
            .update("X-Api-Key", "top-secret")
            .update("Accept", "application/json");
        HttpHeaders result = HttpLogMasking.headerFilter().filter(headers);
        assertEquals("XXX", result.getFirst("Authorization"));
        assertEquals("XXX", result.getFirst("X-Api-Key"));
        assertEquals("application/json", result.getFirst("Accept"));
    }

    @Test
    void omitsBodiesThatCannotBeSafelyFiltered() {
        assertEquals("[body omitted]", HttpLogMasking.bodyFilter().filter("application/json", "{broken"));
        assertEquals("[body omitted]", HttpLogMasking.bodyFilter().filter("text/plain", "password=hunter2"));
    }
}
