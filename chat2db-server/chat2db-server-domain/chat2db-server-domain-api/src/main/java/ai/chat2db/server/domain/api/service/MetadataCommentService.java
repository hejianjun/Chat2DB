package ai.chat2db.server.domain.api.service;

import java.util.Collection;
import java.util.Map;

import ai.chat2db.server.domain.api.model.MetadataComment;
import ai.chat2db.server.domain.api.param.MetadataCommentParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;

public interface MetadataCommentService {

    String TYPE_DATABASE = "DATABASE";
    String TYPE_SCHEMA = "SCHEMA";
    String TYPE_REDIS_PREFIX = "REDIS_PREFIX";
    String TYPE_REDIS_KEY = "REDIS_KEY";

    ActionResult save(MetadataCommentParam param);

    Map<String, MetadataComment> queryByKeys(Long dataSourceId, Long userId, String objectType,
                                             Collection<String> objectKeys);

    String buildObjectKey(String objectType, String databaseName, String schemaName, String tableName,
                          String columnName, String objectName);
}
