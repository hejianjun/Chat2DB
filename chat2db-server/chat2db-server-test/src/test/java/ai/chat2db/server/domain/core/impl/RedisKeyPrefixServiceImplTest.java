package ai.chat2db.server.domain.core.impl;

import java.util.List;
import java.util.Map;

import ai.chat2db.server.domain.api.model.RedisKeyPrefix;
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

    @Test
    void prefixSearchShouldNormalizeWildcardAndMatchOnlyLeadingPrefix() {
        String searchKey = RedisKeyPrefixServiceImpl.normalizePrefixSearchKey("vehicle:basicinfo:*");

        Assertions.assertEquals("vehicle:basicinfo:", searchKey);
        Assertions.assertTrue(RedisKeyPrefixServiceImpl.isPrefixSearchMatch(
                RedisKeyPrefix.builder()
                        .name("vehicle:basicinfo:")
                        .build(),
                searchKey,
                true));
        Assertions.assertFalse(RedisKeyPrefixServiceImpl.isPrefixSearchMatch(
                RedisKeyPrefix.builder()
                        .name("test:vehicle:basicinfo:")
                        .comment("vehicle:basicinfo:")
                        .build(),
                searchKey,
                true));
        Assertions.assertFalse(RedisKeyPrefixServiceImpl.isPrefixSearchMatch(
                RedisKeyPrefix.builder()
                        .name("basicinfo:item:")
                        .build(),
                searchKey,
                true));
    }

    @Test
    void normalPrefixSearchShouldStillMatchComments() {
        String searchKey = RedisKeyPrefixServiceImpl.normalizePrefixSearchKey("车辆基础");

        Assertions.assertTrue(RedisKeyPrefixServiceImpl.isPrefixSearchMatch(
                RedisKeyPrefix.builder()
                        .name("vehicle:basicinfo:")
                        .comment("车辆基础信息缓存")
                        .build(),
                searchKey,
                false));
        Assertions.assertFalse(RedisKeyPrefixServiceImpl.isPrefixSearchMatch(
                RedisKeyPrefix.builder()
                        .name("vehicle:basicinfo:")
                        .comment("车辆基础信息缓存")
                        .build(),
                searchKey,
                true));
        Assertions.assertFalse(RedisKeyPrefixServiceImpl.isPrefixSearchMatch(
                RedisKeyPrefix.builder()
                        .name("vehicle:basicinfo:")
                        .comment("车型状态缓存")
                        .build(),
                searchKey,
                false));
    }

    @Test
    void normalPrefixSearchShouldPrioritizeLeadingPrefixBeforeCommentMatches() {
        String searchKey = RedisKeyPrefixServiceImpl.normalizePrefixSearchKey("vehicle:basicinfo");

        Assertions.assertTrue(RedisKeyPrefixServiceImpl.isPrefixSearchMatch(
                RedisKeyPrefix.builder()
                        .name("vehicle:basicinfo:")
                        .build(),
                searchKey,
                false));
        Assertions.assertFalse(RedisKeyPrefixServiceImpl.isPrefixSearchMatch(
                RedisKeyPrefix.builder()
                        .name("basicinfo:item:")
                        .build(),
                searchKey,
                false));
    }
}
