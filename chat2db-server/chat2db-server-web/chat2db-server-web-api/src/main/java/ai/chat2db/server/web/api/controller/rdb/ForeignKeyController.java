package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.domain.api.param.CreateVirtualFKParam;
import ai.chat2db.server.domain.api.param.UpdateVirtualFKParam;
import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.server.web.api.controller.rdb.request.CreateVirtualFKRequest;
import ai.chat2db.server.web.api.controller.rdb.request.DeleteVirtualFKBatchRequest;
import ai.chat2db.server.web.api.controller.rdb.request.DeleteFKByNameRequest;
import ai.chat2db.server.web.api.controller.rdb.request.DeleteFKRequest;
import ai.chat2db.server.web.api.controller.rdb.request.ForeignKeyListRequest;
import ai.chat2db.server.web.api.controller.rdb.request.ForeignKeySyncRequest;
import ai.chat2db.server.web.api.controller.rdb.request.ImportVirtualFKRequest;
import ai.chat2db.server.web.api.controller.rdb.request.UpdateVirtualFKRequest;
import ai.chat2db.server.web.api.controller.rdb.request.VirtualFKValidateRequest;
import ai.chat2db.server.web.api.controller.rdb.vo.DeleteFKResult;
import ai.chat2db.server.web.api.controller.rdb.vo.ForeignKeyVO;
import ai.chat2db.server.web.api.controller.rdb.vo.SyncResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.VirtualForeignKey;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@ConnectionInfoAspect
@RequestMapping("/api/rdb/fk")
@RestController
public class ForeignKeyController {

    private static final long VALIDATE_STREAM_TIMEOUT = 30 * 60 * 1000L;

    @Autowired
    private ForeignKeySyncService foreignKeySyncService;


    @PostMapping("/sync")
    public DataResult<SyncResult> sync(@Valid @RequestBody ForeignKeySyncRequest request) {
        ForeignKeySyncService.SyncResult result = foreignKeySyncService.syncForeignKeys(
                request.getDataSourceId(),
                request.getDatabaseName(),
                request.getSchemaName(),
                request.getTableName()
        );
        return DataResult.of(SyncResult.builder()
                .added(result.getAdded())
                .deleted(result.getDeleted())
                .unchanged(result.getUnchanged())
                .build());
    }

    @GetMapping("/list")
    public ListResult<ForeignKeyVO> list(@Valid ForeignKeyListRequest request) {
        List<ForeignKey> fks = foreignKeySyncService.listForeignKeys(
                request.getDataSourceId(),
                request.getDatabaseName(),
                request.getSchemaName(),
                request.getTableName(),
                request.getRelation()
        );
        List<ForeignKeyVO> voList = fks.stream()
                .map(fk -> ForeignKeyVO.builder()
                        .id(fk.getId())
                        .name(fk.getName())
                        .tableName(fk.getTableName())
                        .columnName(fk.getColumn())
                        .referencedTable(fk.getReferencedTable())
                        .referencedColumnName(fk.getReferencedColumn())
                        .comment(fk.getComment())
                        .updateRule(fk.getUpdateRule())
                        .deleteRule(fk.getDeleteRule())
                        .sourceType(fk instanceof VirtualForeignKey ? "VIRTUAL" : "REAL")
                        .editable(fk instanceof VirtualForeignKey)
                        .build())
                .collect(Collectors.toList());
        return ListResult.of(voList);
    }

    @PostMapping("/virtual/create")
    public DataResult<VirtualForeignKey> createVirtual(@Valid @RequestBody CreateVirtualFKRequest request) {
        CreateVirtualFKParam param = CreateVirtualFKParam.builder()
                .dataSourceId(request.getDataSourceId())
                .databaseName(request.getDatabaseName())
                .schemaName(request.getSchemaName())
                .tableName(request.getTableName())
                .columnName(request.getColumnName())
                .referencedTable(request.getReferencedTable())
                .referencedColumnName(request.getReferencedColumnName())
                .comment(request.getComment())
                .build();
        return DataResult.of(foreignKeySyncService.createVirtualFK(param));
    }

    @PostMapping("/virtual/import")
    public DataResult<ForeignKeySyncService.ImportVirtualFKResult> importVirtual(
            @Valid @RequestBody ImportVirtualFKRequest request) {
        List<CreateVirtualFKParam> params = request.getVirtualForeignKeys().stream()
                .map(item -> CreateVirtualFKParam.builder()
                        .dataSourceId(request.getDataSourceId())
                        .databaseName(request.getDatabaseName())
                        .schemaName(request.getSchemaName())
                        .tableName(item.getTableName())
                        .columnName(item.getColumnName())
                        .referencedTable(item.getReferencedTable())
                        .referencedColumnName(item.getReferencedColumnName())
                        .comment(item.getComment())
                        .build())
                .collect(Collectors.toList());
        return DataResult.of(foreignKeySyncService.importVirtualFKs(params, request.isSkipInvalid()));
    }

    @PostMapping("/virtual/update")
    public DataResult<VirtualForeignKey> updateVirtual(@Valid @RequestBody UpdateVirtualFKRequest request) {
        UpdateVirtualFKParam param = UpdateVirtualFKParam.builder()
                .id(request.getId())
                .dataSourceId(request.getDataSourceId())
                .databaseName(request.getDatabaseName())
                .schemaName(request.getSchemaName())
                .comment(request.getComment())
                .tableName(request.getTableName())
                .columnName(request.getColumnName())
                .referencedTable(request.getReferencedTable())
                .referencedColumnName(request.getReferencedColumnName())
                .vkName(request.getVkName())
                .build();
        return DataResult.of(foreignKeySyncService.updateVirtualFK(param));
    }

    @PostMapping("/delete")
    public DataResult<DeleteFKResult> delete(@Valid @RequestBody DeleteFKRequest request) {
        if ("VIRTUAL".equals(request.getSourceType())) {
            foreignKeySyncService.deleteVirtualFK(request.getId());
            return DataResult.of(DeleteFKResult.builder().executedDDL(null).build());
        } else {
            String ddl = foreignKeySyncService.deleteRealFK(request.getId());
            return DataResult.of(DeleteFKResult.builder().executedDDL(ddl).build());
        }
    }

    @PostMapping("/virtual/delete_batch")
    public DataResult<Integer> deleteVirtualBatch(@Valid @RequestBody DeleteVirtualFKBatchRequest request) {
        return DataResult.of(foreignKeySyncService.deleteVirtualFKs(request.getIds()));
    }

    @PostMapping("/virtual/validate")
    public DataResult<ForeignKeySyncService.VirtualForeignKeyConsistencyResult> validateVirtual(
            @Valid @RequestBody VirtualFKValidateRequest request) {
        return DataResult.of(foreignKeySyncService.validateVirtualForeignKeys(
                request.getDataSourceId(),
                request.getDatabaseName(),
                request.getSchemaName(),
                request.getTableName(),
                request.getRelation(),
                request.getIds()
        ));
    }

    @GetMapping(value = "/virtual/validate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter validateVirtualStream(@Valid VirtualFKValidateRequest request) throws IOException {
        String validationId = UUID.randomUUID().toString().substring(0, 8);
        long streamStartedAt = System.nanoTime();
        log.info("[VirtualFKValidation][{}] stream request received, dataSourceId={}, databaseName={}, schemaName={}, tableName={}, relation={}, requestedIdCount={}",
                validationId, request.getDataSourceId(), request.getDatabaseName(), request.getSchemaName(),
                request.getTableName(), request.getRelation(), request.getIds() == null ? 0 : request.getIds().size());
        SseEmitter emitter = new SseEmitter(VALIDATE_STREAM_TIMEOUT);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger totalCount = new AtomicInteger();
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();

        emitter.onCompletion(() -> {
            running.set(false);
            log.info("[VirtualFKValidation][{}] emitter completed, elapsedMs={}",
                    validationId, elapsedMillis(streamStartedAt));
        });
        emitter.onTimeout(() -> {
            running.set(false);
            log.warn("[VirtualFKValidation][{}] emitter timed out, elapsedMs={}",
                    validationId, elapsedMillis(streamStartedAt));
        });
        emitter.onError(error -> {
            running.set(false);
            log.warn("[VirtualFKValidation][{}] emitter error, elapsedMs={}",
                    validationId, elapsedMillis(streamStartedAt), error);
        });
        long connectEventStartedAt = System.nanoTime();
        log.info("[VirtualFKValidation][{}] SSE connect event send begin", validationId);
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data(LocalDateTime.now().toString())
                    .reconnectTime(3000));
            log.info("[VirtualFKValidation][{}] SSE connect event send end, elapsedMs={}",
                    validationId, elapsedMillis(connectEventStartedAt));
        } catch (IOException e) {
            log.error("[VirtualFKValidation][{}] SSE connect event send failed, elapsedMs={}",
                    validationId, elapsedMillis(connectEventStartedAt), e);
            throw e;
        }

        CompletableFuture.runAsync(() -> {
            try {
                log.info("[VirtualFKValidation][{}] worker started, elapsedMs={}",
                        validationId, elapsedMillis(streamStartedAt));
                Chat2DBContext.putContext(connectInfo);
                log.info("[VirtualFKValidation][{}] connection context restored", validationId);
                Dbutils.runWithSession(() -> {
                    log.info("[VirtualFKValidation][{}] database session callback started", validationId);
                    ForeignKeySyncService.VirtualForeignKeyConsistencyResult result =
                            foreignKeySyncService.validateVirtualForeignKeys(
                                    request.getDataSourceId(),
                                    request.getDatabaseName(),
                                    request.getSchemaName(),
                                    request.getTableName(),
                                    request.getRelation(),
                                    request.getIds(),
                                    new ForeignKeySyncService.VirtualForeignKeyConsistencyListener() {
                                        @Override
                                        public void onStart(int total) {
                                            requireRunning(running);
                                            totalCount.set(total);
                                            log.info("[VirtualFKValidation][{}] progress start prepared, total={}",
                                                    validationId, total);
                                            if (!sendValidationEvent(validationId, emitter, running, "start", Map.of("total", total))) {
                                                throw new ValidationStreamClosedException();
                                            }
                                        }

                                        @Override
                                        public void onItem(int processed, int validCount,
                                                           ForeignKeySyncService.VirtualForeignKeyConsistencyItem item) {
                                            requireRunning(running);
                                            log.info("[VirtualFKValidation][{}] progress item prepared, item={}/{}, id={}, itemValid={}, validCount={}",
                                                    validationId, processed, totalCount.get(), item.getId(), item.isValid(), validCount);
                                            if (!sendValidationEvent(validationId, emitter, running, "item", Map.of(
                                                    "processed", processed,
                                                    "validCount", validCount,
                                                    "total", totalCount.get(),
                                                     "item", item))) {
                                                throw new ValidationStreamClosedException();
                                            }
                                        }
                                    });
                    log.info("[VirtualFKValidation][{}] service validation returned, total={}, validCount={}, elapsedMs={}",
                            validationId, result.getTotal(), result.getValidCount(), elapsedMillis(streamStartedAt));
                    if (running.get() && sendValidationEvent(validationId, emitter, running, "done", result)) {
                        running.set(false);
                        emitter.complete();
                        log.info("[VirtualFKValidation][{}] done event sent and emitter completed, elapsedMs={}",
                                validationId, elapsedMillis(streamStartedAt));
                    } else {
                        log.warn("[VirtualFKValidation][{}] done event skipped because stream is closed", validationId);
                    }
                    log.info("[VirtualFKValidation][{}] database session callback finished, elapsedMs={}",
                            validationId, elapsedMillis(streamStartedAt));
                });
            } catch (Exception e) {
                if (running.get()) {
                    log.error("[VirtualFKValidation][{}] stream worker failed, elapsedMs={}",
                            validationId, elapsedMillis(streamStartedAt), e);
                    sendValidationEvent(validationId, emitter, running, "validation_error",
                            Map.of("message", errorMessage(e)));
                    running.set(false);
                    emitter.completeWithError(e);
                }
            } finally {
                running.set(false);
                Chat2DBContext.removeContext();
                log.info("[VirtualFKValidation][{}] worker finished, elapsedMs={}",
                        validationId, elapsedMillis(streamStartedAt));
            }
        });
        log.info("[VirtualFKValidation][{}] stream request returned emitter, elapsedMs={}",
                validationId, elapsedMillis(streamStartedAt));
        return emitter;
    }

    private boolean sendValidationEvent(String validationId, SseEmitter emitter, AtomicBoolean running,
                                        String eventName, Object data) {
        if (!running.get()) {
            log.warn("[VirtualFKValidation][{}] SSE event send skipped because stream is closed, event={}",
                    validationId, eventName);
            return false;
        }
        long sendStartedAt = System.nanoTime();
        log.info("[VirtualFKValidation][{}] SSE event send begin, event={}", validationId, eventName);
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            log.info("[VirtualFKValidation][{}] SSE event send end, event={}, elapsedMs={}",
                    validationId, eventName, elapsedMillis(sendStartedAt));
            return true;
        } catch (IOException | IllegalStateException e) {
            running.set(false);
            log.warn("[VirtualFKValidation][{}] SSE event send failed, event={}, elapsedMs={}",
                    validationId, eventName, elapsedMillis(sendStartedAt), e);
            return false;
        }
    }

    private void requireRunning(AtomicBoolean running) {
        if (!running.get()) {
            throw new ValidationStreamClosedException();
        }
    }

    private String errorMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static class ValidationStreamClosedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @PostMapping("/delete_by_name")
    public ActionResult deleteByName(@Valid @RequestBody DeleteFKByNameRequest request) {
        List<VirtualForeignKey> virtualFKs = foreignKeySyncService.queryVirtualForeignKeys(
                request.getDataSourceId(),
                request.getDatabaseName(),
                request.getSchemaName(),
                request.getTableName()
        );
        for (VirtualForeignKey vk : virtualFKs) {
            if (vk.getName() != null && vk.getName().equals(request.getKeyName())) {
                foreignKeySyncService.deleteVirtualFK(vk.getId());
                return ActionResult.isSuccess();
            }
        }
        return ActionResult.fail("foreignKey.virtualForeignKeyNotFound",
                I18nUtils.getMessage("foreignKey.virtualForeignKeyNotFound"), "");
    }
}
