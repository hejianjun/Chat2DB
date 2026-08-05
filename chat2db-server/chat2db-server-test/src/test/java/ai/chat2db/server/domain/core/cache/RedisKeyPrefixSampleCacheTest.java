package ai.chat2db.server.domain.core.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import ai.chat2db.server.domain.api.model.RedisKeyPrefixSample;
import ai.chat2db.spi.redis.RedisKeyInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RedisKeyPrefixSampleCacheTest {

    @Test
    void shouldReusePersistedSamplesAndSupportForcedRefresh() {
        TestCache cache = new TestCache();
        AtomicInteger loads = new AtomicInteger();

        List<RedisKeyInfo> first = cache.get(1L, "0", "user:", false, () -> samples(loads.incrementAndGet()));
        List<RedisKeyInfo> cached = cache.get(1L, "0", "user:", false, () -> samples(loads.incrementAndGet()));
        List<RedisKeyInfo> refreshed = cache.get(1L, "0", "user:", true, () -> samples(loads.incrementAndGet()));

        Assertions.assertSame(first, cached);
        Assertions.assertNotSame(first, refreshed);
        Assertions.assertEquals(2, loads.get());
        Assertions.assertEquals("key-2", refreshed.get(0).getName());
    }

    @Test
    void shouldReuseSamplesAfterCacheObjectIsRecreated() {
        Map<String, List<RedisKeyInfo>> persisted = new ConcurrentHashMap<>();
        TestCache firstInstance = new TestCache(persisted);
        firstInstance.get(1L, "0", "user:", false, () -> samples(1));

        TestCache restartedInstance = new TestCache(persisted);
        AtomicInteger loads = new AtomicInteger();
        List<RedisKeyInfo> cached = restartedInstance.get(1L, "0", "user:", false,
                () -> samples(loads.incrementAndGet()));

        Assertions.assertEquals(0, loads.get());
        Assertions.assertEquals("key-1", cached.get(0).getName());
    }

    @Test
    void shouldReadTypesOnlyFromPersistedSamples() {
        RedisKeyPrefixSampleCache cache = new RedisKeyPrefixSampleCache() {
            @Override
            protected List<RedisKeyPrefixSample> findAll(Long dataSourceId, String databaseName) {
                return List.of(
                        RedisKeyPrefixSample.builder().prefix("user:").samples(List.of(
                                RedisKeyInfo.builder().type("hash").build(),
                                RedisKeyInfo.builder().type("string").build(),
                                RedisKeyInfo.builder().type("hash").build())).build(),
                        RedisKeyPrefixSample.builder().prefix("ignored:")
                                .samples(List.of(RedisKeyInfo.builder().type("list").build())).build());
            }
        };

        Map<String, String> types = cache.findSampleTypes(1L, "0", java.util.Set.of("user:"));

        Assertions.assertEquals(Map.of("user:", "hash,string"), types);
    }

    private static List<RedisKeyInfo> samples(int sequence) {
        return List.of(RedisKeyInfo.builder().name("key-" + sequence).build());
    }

    private static class TestCache extends RedisKeyPrefixSampleCache {

        private final Map<String, List<RedisKeyInfo>> persisted;

        private TestCache() {
            this(new ConcurrentHashMap<>());
        }

        private TestCache(Map<String, List<RedisKeyInfo>> persisted) {
            this.persisted = persisted;
        }

        @Override
        protected List<RedisKeyInfo> find(SampleCacheKey key) {
            return persisted.get(storageKey(key));
        }

        @Override
        protected void save(SampleCacheKey key, List<RedisKeyInfo> samples) {
            persisted.put(storageKey(key), samples);
        }

        private String storageKey(SampleCacheKey key) {
            return key.dataSourceId() + ":" + key.databaseName() + ":" + key.prefixHash();
        }
    }
}
