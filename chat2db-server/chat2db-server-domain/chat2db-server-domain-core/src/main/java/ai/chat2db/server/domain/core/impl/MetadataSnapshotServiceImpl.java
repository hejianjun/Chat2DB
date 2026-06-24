package ai.chat2db.server.domain.core.impl;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import ai.chat2db.server.domain.api.model.MetadataSnapshot;
import ai.chat2db.server.domain.api.service.MetadataSnapshotService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.MetadataSnapshotDO;
import ai.chat2db.server.domain.repository.mapper.MetadataSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class MetadataSnapshotServiceImpl implements MetadataSnapshotService {

    private MetadataSnapshotMapper getMapper() {
        return Dbutils.getMapper(MetadataSnapshotMapper.class);
    }

    @Override
    public MetadataSnapshot get(Long dataSourceId, String objectType, String objectKey) {
        if (dataSourceId == null || StringUtils.isBlank(objectKey)) {
            return null;
        }
        LambdaQueryWrapper<MetadataSnapshotDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MetadataSnapshotDO::getDataSourceId, dataSourceId)
                .eq(MetadataSnapshotDO::getObjectType, normalizeType(objectType))
                .eq(MetadataSnapshotDO::getObjectKey, objectKey);
        MetadataSnapshotDO entity = getMapper().selectOne(queryWrapper);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public Map<String, MetadataSnapshot> queryByKeys(Long dataSourceId, String objectType,
                                                     Collection<String> objectKeys) {
        if (dataSourceId == null || CollectionUtils.isEmpty(objectKeys)) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<MetadataSnapshotDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MetadataSnapshotDO::getDataSourceId, dataSourceId)
                .eq(MetadataSnapshotDO::getObjectType, normalizeType(objectType))
                .in(MetadataSnapshotDO::getObjectKey, objectKeys);
        return getMapper().selectList(queryWrapper).stream()
                .map(this::toModel)
                .collect(Collectors.toMap(MetadataSnapshot::getObjectKey, item -> item, (oldVal, newVal) -> newVal));
    }

    @Override
    public void upsertCount(Long dataSourceId, String objectType, String objectKey, String databaseName,
                            String schemaName, String objectName, Long objectCount, Long version, String status,
                            String errorMessage) {
        if (dataSourceId == null || StringUtils.isBlank(objectKey)) {
            return;
        }
        String normalizedType = normalizeType(objectType);
        LambdaQueryWrapper<MetadataSnapshotDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MetadataSnapshotDO::getDataSourceId, dataSourceId)
                .eq(MetadataSnapshotDO::getObjectType, normalizedType)
                .eq(MetadataSnapshotDO::getObjectKey, objectKey);
        MetadataSnapshotDO old = getMapper().selectOne(queryWrapper);

        MetadataSnapshotDO entity = new MetadataSnapshotDO();
        entity.setDataSourceId(dataSourceId);
        entity.setObjectType(normalizedType);
        entity.setObjectKey(objectKey);
        entity.setDatabaseName(databaseName);
        entity.setSchemaName(schemaName);
        entity.setObjectName(StringUtils.defaultIfBlank(objectName, databaseName));
        entity.setObjectCount(objectCount);
        entity.setVersion(version);
        entity.setStatus(status);
        entity.setRefreshedAt(LocalDateTime.now());
        entity.setErrorMessage(errorMessage);

        if (old == null) {
            getMapper().insert(entity);
            return;
        }
        entity.setGmtModified(LocalDateTime.now());
        LambdaUpdateWrapper<MetadataSnapshotDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MetadataSnapshotDO::getId, old.getId());
        getMapper().update(entity, updateWrapper);
    }

    @Override
    public String buildObjectKey(String objectType, String databaseName, String schemaName, String objectName) {
        String type = normalizeType(objectType);
        if (TYPE_TABLE.equals(type)) {
            return "db=" + safe(databaseName) + "|schema=" + safe(schemaName);
        }
        if (TYPE_REDIS_KEY.equals(type)) {
            return "db=" + safe(databaseName) + "|redisKeys";
        }
        if (TYPE_REDIS_PREFIX.equals(type)) {
            return "db=" + safe(databaseName) + "|redisPrefixes";
        }
        return "db=" + safe(databaseName) + "|schema=" + safe(schemaName) + "|name=" + safe(objectName);
    }

    private MetadataSnapshot toModel(MetadataSnapshotDO entity) {
        return MetadataSnapshot.builder()
                .dataSourceId(entity.getDataSourceId())
                .objectType(entity.getObjectType())
                .objectKey(entity.getObjectKey())
                .databaseName(entity.getDatabaseName())
                .schemaName(entity.getSchemaName())
                .objectName(entity.getObjectName())
                .objectCount(entity.getObjectCount())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .refreshedAt(entity.getRefreshedAt())
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    private String normalizeType(String objectType) {
        return StringUtils.upperCase(StringUtils.defaultString(objectType));
    }

    private String safe(String value) {
        return StringUtils.defaultString(value);
    }
}
