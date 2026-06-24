package ai.chat2db.server.domain.api.service;

import java.util.Collection;
import java.util.Map;

import ai.chat2db.server.domain.api.model.MetadataSnapshot;

public interface MetadataSnapshotService {

    String TYPE_TABLE = "TABLE";
    String TYPE_REDIS_KEY = "REDIS_KEY";
    String TYPE_REDIS_PREFIX = "REDIS_PREFIX";

    String STATUS_READY = "READY";
    String STATUS_EMPTY = "EMPTY";
    String STATUS_REFRESHING = "REFRESHING";
    String STATUS_FAILED = "FAILED";

    MetadataSnapshot get(Long dataSourceId, String objectType, String objectKey);

    Map<String, MetadataSnapshot> queryByKeys(Long dataSourceId, String objectType, Collection<String> objectKeys);

    void upsertCount(Long dataSourceId, String objectType, String objectKey, String databaseName, String schemaName,
                     String objectName, Long objectCount, Long version, String status, String errorMessage);

    String buildObjectKey(String objectType, String databaseName, String schemaName, String objectName);
}
