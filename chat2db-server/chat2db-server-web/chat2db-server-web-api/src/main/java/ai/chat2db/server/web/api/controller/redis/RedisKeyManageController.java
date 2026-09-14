package ai.chat2db.server.web.api.controller.redis;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.redis.request.KeyCreateRequest;
import ai.chat2db.server.web.api.controller.redis.request.KeyDeleteRequest;
import ai.chat2db.server.web.api.controller.redis.request.KeyPartialUpdateRequest;
import ai.chat2db.server.web.api.controller.redis.request.KeyQueryRequest;
import ai.chat2db.server.web.api.controller.redis.request.KeyUpdateRequest;
import ai.chat2db.server.web.api.controller.redis.vo.KeyVO;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.redis.RedisKeyBrowser;
import ai.chat2db.spi.redis.RedisKeyInfo;
import ai.chat2db.spi.redis.RedisKeyMutation;
import ai.chat2db.spi.redis.RedisKeyScanResult;
import ai.chat2db.spi.redis.RedisKeyValuePage;
import ai.chat2db.spi.sql.Chat2DBContext;

import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * redis key运维类
 *
 * @author moji
 * @version MysqlTableManageController.java, v 0.1 2022年09月16日 17:41 moji Exp $
 * @date 2022/09/16
 */
@RequestMapping("/api/redis/key")
@RestController
@ConnectionInfoAspect
public class RedisKeyManageController {

    private static final int DEFAULT_KEY_COUNT = 1000;
    private static final int DEFAULT_VALUE_PAGE_SIZE = 50;
    private static final int MAX_VALUE_PAGE_SIZE = 200;
    private static final int MAX_STRING_PAGE_BYTES = 64 * 1024;
    private static final int MAX_FULL_WRITE_ELEMENTS = 10_000;
    private static final int MAX_WRITE_BYTES = 1024 * 1024;
    private static final long STREAM_TIMEOUT = 30 * 60 * 1000L;

    /**
     * 流式查询当前DB下的key列表
     *
     * @param request
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(KeyQueryRequest request) throws IOException {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<CompletableFuture<RedisKeyScanResult>> resultFutureRef = new AtomicReference<>();
        emitter.onCompletion(() -> {
            closed.set(true);
            cancelFuture(resultFutureRef.get());
        });
        emitter.onTimeout(() -> {
            closed.set(true);
            cancelFuture(resultFutureRef.get());
        });
        emitter.onError(throwable -> {
            closed.set(true);
            cancelFuture(resultFutureRef.get());
        });
        emitter.send(SseEmitter.event()
                .name("connect")
                .data(LocalDateTime.now().toString())
                .reconnectTime(3000));

        try {
            RedisKeyBrowser browser = getRedisKeyBrowser();
            int count = request.getCount() == null ? DEFAULT_KEY_COUNT : request.getCount();
            CompletableFuture<RedisKeyScanResult> resultFuture = browser.streamKeys(request.getDatabaseName(),
                    request.getSearchKey(), request.getCursor(), count, batch -> {
                        if (!closed.get()) {
                            sendEvent(emitter, "keys", batch.stream().map(this::toVO).toList());
                        }
                    });
            resultFutureRef.set(resultFuture);
            resultFuture.whenCompleteAsync((result, throwable) -> {
                if (closed.get()) {
                    return;
                }
                if (throwable != null) {
                    Throwable cause = unwrapCompletionException(throwable);
                    sendStreamError(emitter, cause);
                    return;
                }
                try {
                    if (sendEvent(emitter, "done", Map.of(
                            "total", result.getTotal(),
                            "cursor", result.getCursor(),
                            "hasMore", result.getHasMore()
                    ))) {
                        closed.set(true);
                        emitter.complete();
                    }
                } catch (Exception e) {
                    sendStreamError(emitter, e);
                }
            });
        } catch (Exception e) {
            sendStreamError(emitter, e);
        }
        return emitter;
    }

    /**
     * 获取缓存key详情
     *
     * @param request
     * @return
     */
    @GetMapping("/query")
    public DataResult<KeyVO> query(KeyQueryRequest request) {
        RedisKeyInfo keyInfo = getRedisKeyBrowser().queryKey(request.getDatabaseName(), request.getKeyName());
        return DataResult.of(toVO(keyInfo));
    }

    /** Exact hash field lookup, or a cursor batch for an explicit Redis glob pattern. */
    @GetMapping("/hash-field")
    public DataResult<RedisKeyValuePage> hashField(KeyQueryRequest request, @RequestParam String field) {
        return DataResult.of(getRedisKeyBrowser().queryHashFields(request.getDatabaseName(), request.getKeyName(),
                field, request.getCursor()));
    }

    /** Returns one bounded page without loading the complete Redis value. */
    @GetMapping("/value-page")
    public DataResult<RedisKeyValuePage> valuePage(KeyQueryRequest request) {
        int count = request.getCount() == null || request.getCount() <= 0
                ? DEFAULT_VALUE_PAGE_SIZE : Math.min(request.getCount(), MAX_VALUE_PAGE_SIZE);
        return DataResult.of(getRedisKeyBrowser().queryKeyPage(request.getDatabaseName(), request.getKeyName(),
                request.getCursor(), count, MAX_STRING_PAGE_BYTES));
    }

    /**
     * 新增Key
     *
     * @param request
     * @return
     */
    @PostMapping("/create")
    public ActionResult create(@RequestBody KeyCreateRequest request) {
        validateFullWrite(request.getKeyType(), request.getValue());
        getRedisKeyBrowser().createKey(request.getDatabaseName(), request.getName(), request.getKeyType(),
                request.getValue(), request.getTtl());
        return ActionResult.isSuccess();
    }

    /**
     * 修改key信息
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/update",method = {RequestMethod.POST, RequestMethod.PUT})
    public ActionResult update(@RequestBody KeyUpdateRequest request) {
        validateFullWrite(request.getKeyType(), request.getValue());
        if ("list".equalsIgnoreCase(request.getKeyType()) && request.getExpectedElementCount() != null
                && request.getValue() instanceof Collection<?> values
                && values.size() != request.getExpectedElementCount()) {
            throw new BusinessException("redis.listMustBeFullyLoaded");
        }
        Object updateTtl = request.getUpdateTtl();
        Long ttl = updateTtl == null ? null : Long.valueOf(String.valueOf(updateTtl));
        getRedisKeyBrowser().updateKey(request.getDatabaseName(), request.getOriginalKey(), request.getUpdateKey(),
                request.getKeyType(), request.getValue(), ttl);
        return ActionResult.isSuccess();
    }

    /**
     * 部分更新 hash、set 或 zset，并可同时更新 key 名称和 TTL。
     *
     * @param request
     * @return
     */
    @PostMapping("/partial-update")
    public ActionResult partialUpdate(@RequestBody KeyPartialUpdateRequest request) {
        Object updateTtl = request.getUpdateTtl();
        Long ttl = updateTtl == null ? null : Long.valueOf(String.valueOf(updateTtl));
        Map<String, String> hashUpserts = request.getHashUpserts() == null
                ? request.getAddedFields() : request.getHashUpserts();
        List<String> hashDeletes = request.getHashDeletes() == null
                ? request.getRemovedFields() : request.getHashDeletes();
        getRedisKeyBrowser().partialUpdateKey(request.getDatabaseName(), RedisKeyMutation.builder()
                .keyName(request.getKeyName())
                .updateKey(request.getUpdateKey())
                .keyType(request.getKeyType())
                .hashUpserts(hashUpserts)
                .hashDeletes(hashDeletes)
                .listUpdates(request.getListUpdates())
                .listAppends(request.getListAppends())
                .setAdds(request.getSetAdds())
                .setRemoves(request.getSetRemoves())
                .zsetUpserts(request.getZsetUpserts())
                .zsetRemoves(request.getZsetRemoves())
                .ttl(ttl)
                .build());
        return ActionResult.isSuccess();
    }


    /**
     * 删除key
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/delete", method = {RequestMethod.POST, RequestMethod.DELETE})
    public ActionResult delete(@RequestBody KeyDeleteRequest request) {
        getRedisKeyBrowser().deleteKey(request.getDatabaseName(), request.getKeyName());
        return ActionResult.isSuccess();
    }

    private RedisKeyBrowser getRedisKeyBrowser() {
        MetaData metaData = Chat2DBContext.getMetaData();
        if (metaData instanceof RedisKeyBrowser browser) {
            return browser;
        }
        throw new BusinessException("当前数据源不是 Redis");
    }

    private void validateFullWrite(String keyType, Object value) {
        if ("list".equalsIgnoreCase(keyType) && value instanceof Collection<?> values
                && values.size() > MAX_FULL_WRITE_ELEMENTS) {
            throw new BusinessException("redis.fullWriteTooManyElements");
        }
        if (String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_WRITE_BYTES) {
            throw new BusinessException("redis.fullWriteTooLarge");
        }
    }

    private KeyVO toVO(RedisKeyInfo keyInfo) {
        KeyVO vo = new KeyVO();
        BeanUtils.copyProperties(keyInfo, vo);
        return vo;
    }

    private boolean sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException e) {
            if (isClientAbort(e)) {
                return false;
            }
            throw new BusinessException("Redis key stream send failed", null, e);
        }
    }

    private void sendStreamError(SseEmitter emitter, Throwable throwable) {
        try {
            sendEvent(emitter, "redis_error", Map.of("message", throwable.getMessage()));
        } catch (Exception ignored) {
            // ignore send failure and complete the emitter with the original error
        }
        emitter.completeWithError(throwable);
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void cancelFuture(CompletableFuture<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            String className = cause.getClass().getName();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
