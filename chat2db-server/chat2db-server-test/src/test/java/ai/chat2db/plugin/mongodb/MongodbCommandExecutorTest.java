package ai.chat2db.plugin.mongodb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

class MongodbCommandExecutorTest {

    @Test
    void formatDateAsUtcIsoInstant() {
        Date date = Date.from(Instant.parse("2026-04-21T16:00:00.123Z"));

        Assertions.assertEquals("2026-04-21T16:00:00.123Z", MongodbCommandExecutor.formatValue(date));
    }
}
