package ai.chat2db.server.domain.core.impl;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RedisKeyPrefixServiceImplTest {

    @Test
    void buildPrefixCountMapShouldUseLongestDelimiterPrefixes() {
        Map<String, Long> prefixes = RedisKeyPrefixServiceImpl.buildPrefixCountMap(List.of(
                "user:profile:1",
                "user:profile:2",
                "user:session:1",
                "user:session:2",
                "use_case",
                "use_more",
                "us-west-cache",
                "us-west-db",
                "order.created.1",
                "order.created.2",
                "single:key",
                "a:b:1",
                "a:b:2",
                "a:b:c:1",
                "a:b:c:2"
        ), ":_.-");

        Assertions.assertEquals(2L, prefixes.get("user:profile:"));
        Assertions.assertEquals(2L, prefixes.get("user:session:"));
        Assertions.assertEquals(2L, prefixes.get("use_"));
        Assertions.assertEquals(2L, prefixes.get("us-west-"));
        Assertions.assertEquals(2L, prefixes.get("order.created."));
        Assertions.assertEquals(2L, prefixes.get("a:b:c:"));
        Assertions.assertFalse(prefixes.containsKey("us"));
        Assertions.assertFalse(prefixes.containsKey("use"));
        Assertions.assertFalse(prefixes.containsKey("single:"));
        Assertions.assertFalse(prefixes.containsKey("a:b:"));
    }
}
