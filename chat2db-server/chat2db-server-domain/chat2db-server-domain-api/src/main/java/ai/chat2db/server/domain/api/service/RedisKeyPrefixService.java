package ai.chat2db.server.domain.api.service;

import java.util.List;

import ai.chat2db.server.domain.api.model.RedisKeyPrefix;
import ai.chat2db.server.domain.api.model.RedisKeyPrefixBuildStatus;
import ai.chat2db.server.domain.api.param.RedisKeyPrefixQueryParam;
import ai.chat2db.spi.sql.ConnectInfo;

public interface RedisKeyPrefixService {

    List<RedisKeyPrefix> search(RedisKeyPrefixQueryParam param);

    long refresh(Long dataSourceId, String databaseName, ConnectInfo connectInfo);

    void updateComment(Long dataSourceId, String databaseName, String prefix, String comment);

    void delete(Long dataSourceId, String databaseName, String prefix);

    RedisKeyPrefixBuildStatus getStatus(Long dataSourceId, String databaseName);
}
