package ai.chat2db.server.domain.core.cache;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import ai.chat2db.spi.model.IndexModel;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LuceneIndexManagerFactory implements DisposableBean {

    private final ConcurrentHashMap<Long, LuceneIndexManager<?>> instances = new ConcurrentHashMap<>();

    public <T extends IndexModel> LuceneIndexManager<T> getManager(Long id) {
        if (id == null) {
            log.error("dataSourceId is null");
            if (Chat2DBContext.getConnectInfo() != null) {
                log.error("dataSourceId is null,use connectInfo:{}", Chat2DBContext.getConnectInfo());
                id = Chat2DBContext.getConnectInfo().getDataSourceId();
            }
        }
        return (LuceneIndexManager<T>) instances.computeIfAbsent(id, k -> new LuceneIndexManager<>(k));
    }

    @Override
    public void destroy() throws Exception {
        instances.values().forEach(manager -> {
            try {
                manager.close();
            } catch (IOException e) {
                // 处理关闭异常
                log.error("Failed to close LuceneIndexManager", e);
            }
        });
        instances.clear();
    }

}
