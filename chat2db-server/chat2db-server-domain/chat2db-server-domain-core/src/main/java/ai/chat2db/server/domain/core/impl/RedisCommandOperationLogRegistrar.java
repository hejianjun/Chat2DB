package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.operation.OperationLogCreateParam;
import ai.chat2db.server.domain.api.service.OperationLogService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.spi.redis.RedisCommandAuditEvent;
import ai.chat2db.spi.redis.RedisCommandAuditRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Lazy(false)
@Component
public class RedisCommandOperationLogRegistrar implements InitializingBean {

    @Autowired
    @Lazy
    private OperationLogService operationLogService;

    @Override
    public void afterPropertiesSet() {
        log.info("Register redis command audit operation log sink");
        RedisCommandAuditRegistry.register(this::createOperationLog);
    }

    private void createOperationLog(RedisCommandAuditEvent event) {
        if (event == null) {
            return;
        }
        // CommandListener callbacks run outside Chat2DB request threads, so bind a repository session explicitly.
        CompletableFuture.runAsync(() -> Dbutils.runWithSession(() -> doCreateOperationLog(event)));
    }

    private void doCreateOperationLog(RedisCommandAuditEvent event) {
        try {
            OperationLogCreateParam param = new OperationLogCreateParam();
            param.setDataSourceId(event.getDataSourceId());
            param.setUserId(event.getUserId());
            param.setDatabaseName(event.getDatabaseName());
            param.setType(event.getType());
            param.setDdl(event.getCommand());
            param.setStatus(event.getStatus());
            param.setUseTime(event.getUseTime());
            param.setOperationRows(event.getOperationRows());
            param.setExtendInfo(event.getExtendInfo());
            log.info("Create redis command operation log, command={}", event.getCommand());
            operationLogService.create(param);
        } catch (Exception e) {
            log.error("create redis command operation log error:", e);
        }
    }
}
