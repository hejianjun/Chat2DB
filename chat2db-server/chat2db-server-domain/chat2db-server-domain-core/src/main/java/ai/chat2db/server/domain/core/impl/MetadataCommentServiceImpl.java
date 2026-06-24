package ai.chat2db.server.domain.core.impl;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import ai.chat2db.server.domain.api.model.MetadataComment;
import ai.chat2db.server.domain.api.param.MetadataCommentParam;
import ai.chat2db.server.domain.api.service.MetadataCommentService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.MetadataCommentDO;
import ai.chat2db.server.domain.repository.mapper.MetadataCommentMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.model.LoginUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class MetadataCommentServiceImpl implements MetadataCommentService {

    private MetadataCommentMapper getMapper() {
        return Dbutils.getMapper(MetadataCommentMapper.class);
    }

    @Override
    public ActionResult save(MetadataCommentParam param) {
        Long userId = Objects.requireNonNullElse(param.getUserId(), currentUserId());
        userId = Objects.requireNonNullElse(userId, 0L);
        String objectKey = buildObjectKey(param.getObjectType(), param.getDatabaseName(), param.getSchemaName(),
                param.getTableName(), param.getColumnName(), param.getObjectName());

        LambdaQueryWrapper<MetadataCommentDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MetadataCommentDO::getDataSourceId, param.getDataSourceId())
                .eq(MetadataCommentDO::getUserId, userId)
                .eq(MetadataCommentDO::getObjectType, normalizeType(param.getObjectType()))
                .eq(MetadataCommentDO::getObjectKey, objectKey);
        MetadataCommentDO old = getMapper().selectOne(queryWrapper);

        MetadataCommentDO entity = new MetadataCommentDO();
        entity.setDataSourceId(param.getDataSourceId());
        entity.setUserId(userId);
        entity.setObjectType(normalizeType(param.getObjectType()));
        entity.setObjectKey(objectKey);
        entity.setDatabaseName(param.getDatabaseName());
        entity.setSchemaName(param.getSchemaName());
        entity.setTableName(param.getTableName());
        entity.setColumnName(param.getColumnName());
        entity.setObjectName(param.getObjectName());
        entity.setComment(param.getComment());
        entity.setAiComment(param.getAiComment());

        if (old == null) {
            getMapper().insert(entity);
            return ActionResult.isSuccess();
        }

        entity.setGmtModified(LocalDateTime.now());
        LambdaUpdateWrapper<MetadataCommentDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MetadataCommentDO::getId, old.getId());
        getMapper().update(entity, updateWrapper);
        return ActionResult.isSuccess();
    }

    @Override
    public Map<String, MetadataComment> queryByKeys(Long dataSourceId, Long userId, String objectType,
                                                    Collection<String> objectKeys) {
        if (dataSourceId == null || CollectionUtils.isEmpty(objectKeys)) {
            return Collections.emptyMap();
        }
        Long safeUserId = Objects.requireNonNullElse(userId, currentUserId());
        safeUserId = Objects.requireNonNullElse(safeUserId, 0L);
        LambdaQueryWrapper<MetadataCommentDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MetadataCommentDO::getDataSourceId, dataSourceId)
                .eq(MetadataCommentDO::getUserId, safeUserId)
                .eq(MetadataCommentDO::getObjectType, normalizeType(objectType))
                .in(MetadataCommentDO::getObjectKey, objectKeys);
        return getMapper().selectList(queryWrapper).stream()
                .map(this::toModel)
                .collect(Collectors.toMap(MetadataComment::getObjectKey, item -> item, (oldVal, newVal) -> newVal));
    }

    @Override
    public String buildObjectKey(String objectType, String databaseName, String schemaName, String tableName,
                                 String columnName, String objectName) {
        String type = normalizeType(objectType);
        if (TYPE_DATABASE.equals(type)) {
            return "db=" + safeObjectName(databaseName, objectName);
        }
        if (TYPE_SCHEMA.equals(type)) {
            return "db=" + safe(databaseName) + "|schema=" + safeObjectName(schemaName, objectName);
        }
        if (TYPE_REDIS_PREFIX.equals(type)) {
            return "db=" + safe(databaseName) + "|redisPrefix=" + safeObjectName(objectName, objectName);
        }
        if (TYPE_REDIS_KEY.equals(type)) {
            return "db=" + safe(databaseName) + "|redisKey=" + safeObjectName(objectName, objectName);
        }
        return "db=" + safe(databaseName) + "|schema=" + safe(schemaName) + "|name=" + safeObjectName(objectName,
                objectName);
    }

    private MetadataComment toModel(MetadataCommentDO entity) {
        return MetadataComment.builder()
                .dataSourceId(entity.getDataSourceId())
                .userId(entity.getUserId())
                .objectType(entity.getObjectType())
                .objectKey(entity.getObjectKey())
                .databaseName(entity.getDatabaseName())
                .schemaName(entity.getSchemaName())
                .tableName(entity.getTableName())
                .columnName(entity.getColumnName())
                .objectName(entity.getObjectName())
                .comment(entity.getComment())
                .aiComment(entity.getAiComment())
                .build();
    }

    private String normalizeType(String objectType) {
        return StringUtils.upperCase(StringUtils.defaultString(objectType));
    }

    private String safeObjectName(String value, String fallback) {
        return safe(StringUtils.defaultIfBlank(value, fallback));
    }

    private String safe(String value) {
        return StringUtils.defaultString(value);
    }

    private Long currentUserId() {
        LoginUser loginUser = ContextUtils.queryLoginUser();
        return loginUser == null ? 0L : loginUser.getId();
    }
}
