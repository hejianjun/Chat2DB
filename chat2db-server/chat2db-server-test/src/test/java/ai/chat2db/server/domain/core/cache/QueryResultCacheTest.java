package ai.chat2db.server.domain.core.cache;

import java.util.Collections;

import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

class QueryResultCacheTest {

    @Test
    void shouldCompressAndRestoreQueryResult() {
        QueryResultCache cache = new QueryResultCache(new StandardEnvironment());
        ExecuteResult result = ExecuteResult.builder()
                .success(true)
                .sql("select * from orders limit 200")
                .originalSql("select * from orders")
                .operationLogId(42L)
                .headerList(Collections.singletonList(Header.builder().name("id").build()))
                .dataList(Collections.singletonList(Collections.singletonList("1")))
                .build();

        cache.put(42L, 7L, 9L, result.getSql(), result);
        QueryResultCache.CachedQueryResult restored = cache.get(42L);

        Assertions.assertNotNull(restored);
        Assertions.assertEquals(7L, restored.getUserId());
        Assertions.assertEquals(9L, restored.getDataSourceId());
        Assertions.assertEquals("1", restored.getResult().getDataList().get(0).get(0));
        Assertions.assertEquals(42L, restored.getResult().getOperationLogId());
    }
}
