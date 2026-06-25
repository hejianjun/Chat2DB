package ai.chat2db.server.web.api.controller.redis;

import java.util.List;

import ai.chat2db.server.domain.api.model.RedisKeyPrefix;
import ai.chat2db.server.domain.api.model.RedisKeyPrefixBuildStatus;
import ai.chat2db.server.domain.api.param.RedisKeyPrefixQueryParam;
import ai.chat2db.server.domain.api.service.RedisKeyPrefixService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import ai.chat2db.server.web.api.controller.redis.request.KeyPrefixCommentRequest;
import ai.chat2db.server.web.api.controller.redis.request.KeyPrefixDeleteRequest;
import ai.chat2db.server.web.api.controller.redis.request.KeyPrefixQueryRequest;
import ai.chat2db.spi.sql.Chat2DBContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/redis/key-prefix")
@RestController
@ConnectionInfoAspect
public class RedisKeyPrefixController {

    @Autowired
    private RedisKeyPrefixService redisKeyPrefixService;

    @GetMapping("/search")
    public ListResult<RedisKeyPrefix> search(@Valid KeyPrefixQueryRequest request) {
        if (request.isRefresh()) {
            redisKeyPrefixService.refresh(request.getDataSourceId(), request.getDatabaseName(),
                    Chat2DBContext.getConnectInfo());
        }
        RedisKeyPrefixQueryParam param = RedisKeyPrefixQueryParam.builder()
                .dataSourceId(request.getDataSourceId())
                .databaseName(request.getDatabaseName())
                .schemaName(request.getSchemaName())
                .searchKey(request.getSearchKey())
                .refresh(request.isRefresh())
                .sortField(request.getSortField())
                .sortOrder(request.getSortOrder())
                .build();
        List<RedisKeyPrefix> list = redisKeyPrefixService.search(param);
        return ListResult.of(list);
    }

    @PostMapping("/refresh")
    public DataResult<String> refresh(@Valid @RequestBody DataSourceBaseRequest request) {
        String statusKey = redisKeyPrefixService.refresh(request.getDataSourceId(), request.getDatabaseName(),
                Chat2DBContext.getConnectInfo());
        return DataResult.of(statusKey);
    }

    @PostMapping("/comment")
    public DataResult<Long> comment(@Valid @RequestBody KeyPrefixCommentRequest request) {
        long seqNo = redisKeyPrefixService.updateComment(request.getDataSourceId(), request.getDatabaseName(),
                request.getPrefix(), request.getComment());
        return DataResult.of(seqNo);
    }

    @PostMapping("/delete")
    public DataResult<Boolean> delete(@Valid @RequestBody KeyPrefixDeleteRequest request) {
        redisKeyPrefixService.delete(request.getDataSourceId(), request.getDatabaseName(), request.getPrefix());
        return DataResult.of(true);
    }

    @GetMapping("/status")
    public DataResult<RedisKeyPrefixBuildStatus> status(@Valid DataSourceBaseRequest request) {
        return DataResult.of(redisKeyPrefixService.getStatus(request.getDataSourceId(), request.getDatabaseName()));
    }
}
