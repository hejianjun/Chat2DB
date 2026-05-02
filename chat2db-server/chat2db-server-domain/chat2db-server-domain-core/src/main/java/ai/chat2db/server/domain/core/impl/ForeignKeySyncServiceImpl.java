package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.CreateVirtualFKParam;
import ai.chat2db.server.domain.api.param.UpdateVirtualFKParam;
import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.ForeignKeyDO;
import ai.chat2db.server.domain.repository.entity.VirtualForeignKeyDO;
import ai.chat2db.server.domain.repository.mapper.ForeignKeyMapper;
import ai.chat2db.server.domain.repository.mapper.VirtualForeignKeyMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.VirtualForeignKey;
import ai.chat2db.spi.sql.Chat2DBContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ForeignKeySyncServiceImpl implements ForeignKeySyncService {

    private static final String SOURCE_TYPE_REAL = "REAL";
    private static final String SOURCE_TYPE_VIRTUAL_MANUAL = "MANUAL";
    private static final String SOURCE_TYPE_VIRTUAL_INFERRED = "INFERRED";

    private ForeignKeyMapper getFKMapper() {
        return Dbutils.getMapper(ForeignKeyMapper.class);
    }

    private VirtualForeignKeyMapper getVFKMapper() {
        return Dbutils.getMapper(VirtualForeignKeyMapper.class);
    }

    @Override
    public SyncResult syncForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        try {
            Connection connection = Chat2DBContext.getConnection();
            MetaData metaData = Chat2DBContext.getMetaData();
            List<ForeignKey> dbForeignKeys = metaData.foreignKeys(connection, databaseName, schemaName, tableName);

            List<ForeignKeyDO> existingFKs = queryRealFKsFromH2(dataSourceId, databaseName, schemaName, tableName);

            Set<String> dbFKKeys = dbForeignKeys.stream()
                    .map(this::buildUniqueKey)
                    .collect(Collectors.toSet());
            Set<String> existingFKKeys = existingFKs.stream()
                    .map(this::buildUniqueKeyFromDO)
                    .collect(Collectors.toSet());

            int added = 0, deleted = 0;

            for (ForeignKey fk : dbForeignKeys) {
                if (!existingFKKeys.contains(buildUniqueKey(fk))) {
                    insertForeignKey(fk, dataSourceId);
                    added++;
                }
            }

            for (ForeignKeyDO existing : existingFKs) {
                if (!dbFKKeys.contains(buildUniqueKeyFromDO(existing))) {
                    LambdaUpdateWrapper<ForeignKeyDO> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(ForeignKeyDO::getId, existing.getId());
                    getFKMapper().delete(wrapper);
                    deleted++;
                }
            }

            String syncVersion = UUID.randomUUID().toString().substring(0, 8);
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<ForeignKeyDO> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ForeignKeyDO::getDataSourceId, dataSourceId)
                    .eq(StringUtils.isNotBlank(databaseName), ForeignKeyDO::getDatabaseName, databaseName)
                    .eq(StringUtils.isNotBlank(schemaName), ForeignKeyDO::getSchemaName, schemaName)
                    .eq(StringUtils.isNotBlank(tableName), ForeignKeyDO::getTableName, tableName);
            ForeignKeyDO updateDO = new ForeignKeyDO();
            updateDO.setSyncTime(now);
            updateDO.setSyncVersion(syncVersion);
            getFKMapper().update(updateDO, updateWrapper);

            return new SyncResult(added, deleted, dbForeignKeys.size() - added);
        } catch (Exception e) {
            log.error("syncForeignKeys error", e);
            return new SyncResult(0, 0, 0);
        }
    }

    @Override
    public List<ForeignKey> listAllForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        List<ForeignKey> result = new ArrayList<>();

        List<ForeignKeyDO> realFKs = queryRealFKsFromH2(dataSourceId, databaseName, schemaName, tableName);
        for (ForeignKeyDO fk : realFKs) {
            result.add(convertDOToModel(fk));
        }

        List<VirtualForeignKeyDO> virtualFKs = queryVirtualFKsFromH2(dataSourceId, databaseName, schemaName, tableName);
        for (VirtualForeignKeyDO vk : virtualFKs) {
            result.add(convertVirtualDOToModel(vk));
        }

        return result;
    }

    @Override
    public DataResult<VirtualForeignKey> createVirtualFK(CreateVirtualFKParam param) {
        LambdaQueryWrapper<VirtualForeignKeyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VirtualForeignKeyDO::getDataSourceId, param.getDataSourceId())
                .eq(VirtualForeignKeyDO::getTableName, param.getTableName())
                .eq(VirtualForeignKeyDO::getColumnName, param.getColumnName())
                .eq(VirtualForeignKeyDO::getReferencedTable, param.getReferencedTable());

        if (getVFKMapper().selectCount(wrapper) > 0) {
            return DataResult.error("VIRTUAL_FK_EXISTS", "虚拟外键已存在");
        }

        VirtualForeignKeyDO entity = new VirtualForeignKeyDO();
        entity.setDataSourceId(param.getDataSourceId());
        entity.setDatabaseName(param.getDatabaseName());
        entity.setSchemaName(param.getSchemaName());
        entity.setTableName(param.getTableName());
        entity.setColumnName(param.getColumnName());
        entity.setVkName("VFK_" + param.getTableName() + "_" + param.getColumnName());
        entity.setReferencedTable(param.getReferencedTable());
        entity.setReferencedColumnName(param.getReferencedColumnName());
        entity.setComment(param.getComment());
        entity.setSourceType(SOURCE_TYPE_VIRTUAL_MANUAL);
        entity.setUserId(ContextUtils.getUserId());

        getVFKMapper().insert(entity);

        VirtualForeignKey vk = VirtualForeignKey.builder()
                .name(entity.getVkName())
                .tableName(entity.getTableName())
                .column(entity.getColumnName())
                .referencedTable(entity.getReferencedTable())
                .referencedColumn(entity.getReferencedColumnName())
                .comment(entity.getComment())
                .virtualProperty("User-defined virtual foreign key")
                .build();

        return DataResult.of(vk);
    }

    @Override
    public DataResult<VirtualForeignKey> updateVirtualFK(UpdateVirtualFKParam param) {
        VirtualForeignKeyDO existing = getVFKMapper().selectById(param.getId());
        if (existing == null) {
            return DataResult.error("VIRTUAL_FK_NOT_FOUND", "虚拟外键不存在");
        }

        VirtualForeignKeyDO updateDO = new VirtualForeignKeyDO();
        updateDO.setId(param.getId());
        if (StringUtils.isNotBlank(param.getComment())) {
            updateDO.setComment(param.getComment());
        }
        if (StringUtils.isNotBlank(param.getReferencedTable())) {
            updateDO.setReferencedTable(param.getReferencedTable());
        }
        if (StringUtils.isNotBlank(param.getReferencedColumnName())) {
            updateDO.setReferencedColumnName(param.getReferencedColumnName());
        }
        if (StringUtils.isNotBlank(param.getVkName())) {
            updateDO.setVkName(param.getVkName());
        }

        getVFKMapper().updateById(updateDO);

        VirtualForeignKeyDO updated = getVFKMapper().selectById(param.getId());
        VirtualForeignKey vk = VirtualForeignKey.builder()
                .name(updated.getVkName())
                .tableName(updated.getTableName())
                .column(updated.getColumnName())
                .referencedTable(updated.getReferencedTable())
                .referencedColumn(updated.getReferencedColumnName())
                .comment(updated.getComment())
                .virtualProperty("User-defined virtual foreign key")
                .build();

        return DataResult.of(vk);
    }

    @Override
    public ActionResult deleteVirtualFK(Long id) {
        VirtualForeignKeyDO existing = getVFKMapper().selectById(id);
        if (existing == null) {
            return ActionResult.fail("VIRTUAL_FK_NOT_FOUND", "虚拟外键不存在", "Virtual foreign key not found");
        }
        getVFKMapper().deleteById(id);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<String> deleteRealFK(Long id) {
        ForeignKeyDO existing = getFKMapper().selectById(id);
        if (existing == null) {
            return DataResult.error("FK_NOT_FOUND", "外键不存在");
        }

        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        Table table = Table.builder()
                .databaseName(existing.getDatabaseName())
                .schemaName(existing.getSchemaName())
                .name(existing.getTableName())
                .build();
        ForeignKey fk = ForeignKey.builder()
                .name(existing.getFkName())
                .tableName(existing.getTableName())
                .column(existing.getColumnName())
                .referencedTable(existing.getReferencedTable())
                .referencedColumn(existing.getReferencedColumnName())
                .updateRule(existing.getUpdateRule() != null ? existing.getUpdateRule() : 0)
                .deleteRule(existing.getDeleteRule() != null ? existing.getDeleteRule() : 0)
                .build();

        String dropFKSql = sqlBuilder.buildDropForeignKeySql(table, fk);

        getFKMapper().deleteById(id);

        return DataResult.of(dropFKSql);
    }

    @Override
    public List<String> generateForeignKeyDDL(Table oldTable, Table newTable) {
        List<String> ddlList = new ArrayList<>();

        List<ForeignKey> oldFKs = oldTable != null && oldTable.getForeignKeyList() != null
                ? oldTable.getForeignKeyList() : Collections.emptyList();
        List<ForeignKey> newFKs = newTable != null && newTable.getForeignKeyList() != null
                ? newTable.getForeignKeyList() : Collections.emptyList();

        Map<String, ForeignKey> oldFKMap = oldFKs.stream()
                .collect(Collectors.toMap(this::buildUniqueKey, f -> f, (o1, o2) -> o1));
        Map<String, ForeignKey> newFKMap = newFKs.stream()
                .collect(Collectors.toMap(this::buildUniqueKey, f -> f, (o1, o2) -> o1));

        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();

        for (ForeignKey newFK : newFKs) {
            if (!oldFKMap.containsKey(buildUniqueKey(newFK))) {
                String ddl = sqlBuilder.buildAddForeignKeySql(newTable, newFK);
                if (StringUtils.isNotBlank(ddl)) {
                    ddlList.add(ddl);
                }
            }
        }

        for (ForeignKey oldFK : oldFKs) {
            if (!newFKMap.containsKey(buildUniqueKey(oldFK))) {
                String ddl = sqlBuilder.buildDropForeignKeySql(oldTable, oldFK);
                if (StringUtils.isNotBlank(ddl)) {
                    ddlList.add(ddl);
                }
            }
        }

        return ddlList;
    }

    @Override
    public List<ForeignKey> queryRealForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        List<ForeignKeyDO> doList = queryRealFKsFromH2(dataSourceId, databaseName, schemaName, tableName);
        return doList.stream()
                .map(this::convertDOToModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<VirtualForeignKey> queryVirtualForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        List<VirtualForeignKeyDO> doList = queryVirtualFKsFromH2(dataSourceId, databaseName, schemaName, tableName);
        return doList.stream()
                .map(this::convertVirtualDOToModel)
                .collect(Collectors.toList());
    }

    private List<ForeignKeyDO> queryRealFKsFromH2(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        LambdaQueryWrapper<ForeignKeyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForeignKeyDO::getDataSourceId, dataSourceId)
                .eq(StringUtils.isNotBlank(tableName), ForeignKeyDO::getTableName, tableName)
                .eq(StringUtils.isNotBlank(databaseName), ForeignKeyDO::getDatabaseName, databaseName)
                .eq(StringUtils.isNotBlank(schemaName), ForeignKeyDO::getSchemaName, schemaName);
        return getFKMapper().selectList(wrapper);
    }

    private List<VirtualForeignKeyDO> queryVirtualFKsFromH2(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        LambdaQueryWrapper<VirtualForeignKeyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VirtualForeignKeyDO::getDataSourceId, dataSourceId)
                .eq(StringUtils.isNotBlank(tableName), VirtualForeignKeyDO::getTableName, tableName)
                .eq(StringUtils.isNotBlank(databaseName), VirtualForeignKeyDO::getDatabaseName, databaseName)
                .eq(StringUtils.isNotBlank(schemaName), VirtualForeignKeyDO::getSchemaName, schemaName);
        return getVFKMapper().selectList(wrapper);
    }

    private void insertForeignKey(ForeignKey fk, Long dataSourceId) {
        ForeignKeyDO entity = new ForeignKeyDO();
        entity.setDataSourceId(dataSourceId);
        entity.setDatabaseName(fk.getDatabaseName());
        entity.setSchemaName(fk.getSchemaName());
        entity.setTableName(fk.getTableName());
        entity.setColumnName(fk.getColumn());
        entity.setFkName(fk.getName());
        entity.setReferencedTable(fk.getReferencedTable());
        entity.setReferencedColumnName(fk.getReferencedColumn());
        entity.setReferencedSchema(fk.getSchemaName());
        entity.setReferencedDatabase(fk.getDatabaseName());
        entity.setUpdateRule(fk.getUpdateRule());
        entity.setDeleteRule(fk.getDeleteRule());
        entity.setComment(fk.getComment());
        entity.setSyncTime(LocalDateTime.now());
        getFKMapper().insert(entity);
    }

    private ForeignKey convertDOToModel(ForeignKeyDO fk) {
        return ForeignKey.builder()
                .name(fk.getFkName())
                .tableName(fk.getTableName())
                .schemaName(fk.getSchemaName())
                .databaseName(fk.getDatabaseName())
                .column(fk.getColumnName())
                .referencedTable(fk.getReferencedTable())
                .referencedColumn(fk.getReferencedColumnName())
                .updateRule(fk.getUpdateRule() != null ? fk.getUpdateRule() : 0)
                .deleteRule(fk.getDeleteRule() != null ? fk.getDeleteRule() : 0)
                .comment(fk.getComment())
                .build();
    }

    private VirtualForeignKey convertVirtualDOToModel(VirtualForeignKeyDO vk) {
        return VirtualForeignKey.builder()
                .name(vk.getVkName())
                .tableName(vk.getTableName())
                .schemaName(vk.getSchemaName())
                .databaseName(vk.getDatabaseName())
                .column(vk.getColumnName())
                .referencedTable(vk.getReferencedTable())
                .referencedColumn(vk.getReferencedColumnName())
                .comment(vk.getComment())
                .virtualProperty("User-defined virtual foreign key")
                .build();
    }

    private String buildUniqueKey(ForeignKey fk) {
        return String.join(":",
                StringUtils.defaultString(fk.getTableName()),
                StringUtils.defaultString(fk.getColumn()),
                StringUtils.defaultString(fk.getReferencedTable()),
                StringUtils.defaultString(fk.getReferencedColumn())
        );
    }

    private String buildUniqueKeyFromDO(ForeignKeyDO fk) {
        return String.join(":",
                StringUtils.defaultString(fk.getTableName()),
                StringUtils.defaultString(fk.getColumnName()),
                StringUtils.defaultString(fk.getReferencedTable()),
                StringUtils.defaultString(fk.getReferencedColumnName())
        );
    }
}
