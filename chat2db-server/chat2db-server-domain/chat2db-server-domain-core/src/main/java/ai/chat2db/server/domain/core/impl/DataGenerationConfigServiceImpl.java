package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.DataGenerationConfigQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationConfigSaveParam;
import ai.chat2db.server.domain.api.service.DataGenerationConfigService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DataGenerationConfigDO;
import ai.chat2db.server.domain.repository.mapper.DataGenerationConfigMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DataGenerationConfigServiceImpl implements DataGenerationConfigService {

    private DataGenerationConfigMapper getMapper() {
        return Dbutils.getMapper(DataGenerationConfigMapper.class);
    }

    private DataGenerationConfigQueryParam toQueryParam(DataGenerationConfigDO config) {
        DataGenerationConfigQueryParam param = new DataGenerationConfigQueryParam();
        param.setId(config.getId());
        param.setDataSourceId(config.getDataSourceId());
        param.setDatabaseName(config.getDatabaseName());
        param.setSchemaName(config.getSchemaName());
        param.setTableName(config.getTableName());
        param.setRowCount(config.getRowCount());
        param.setBatchSize(config.getBatchSize());
        param.setUserId(config.getUserId());
        return param;
    }

    @Override
    public ActionResult saveConfig(DataGenerationConfigSaveParam param) {
        try {
            DataGenerationConfigDO config = new DataGenerationConfigDO();

            if (param.getId() != null) {
                config.setId(param.getId());
                config.setRowCount(param.getRowCount());
                config.setBatchSize(param.getBatchSize());
                config.setGmtModified(LocalDateTime.now());

                int result = getMapper().updateById(config);
                if (result > 0) {
                    return ActionResult.isSuccess();
                }
                return ActionResult.fail("UPDATE_CONFIG_ERROR", "更新配置失败", null);
            }

            QueryWrapper<DataGenerationConfigDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("data_source_id", param.getDataSourceId());
            queryWrapper.eq("database_name", param.getDatabaseName());
            if (param.getSchemaName() != null) {
                queryWrapper.eq("schema_name", param.getSchemaName());
            } else {
                queryWrapper.isNull("schema_name");
            }
            queryWrapper.eq("table_name", param.getTableName());

            DataGenerationConfigDO existing = getMapper().selectOne(queryWrapper);
            if (existing != null) {
                config.setId(existing.getId());
                config.setRowCount(param.getRowCount());
                config.setBatchSize(param.getBatchSize());
                config.setGmtModified(LocalDateTime.now());
                int result = getMapper().updateById(config);
                if (result > 0) {
                    return ActionResult.isSuccess();
                }
                return ActionResult.fail("UPDATE_CONFIG_ERROR", "更新配置失败", null);
            }

            config.setDataSourceId(param.getDataSourceId());
            config.setDatabaseName(param.getDatabaseName());
            config.setSchemaName(param.getSchemaName());
            config.setTableName(param.getTableName());
            config.setRowCount(param.getRowCount());
            config.setBatchSize(param.getBatchSize());
            config.setUserId(param.getUserId());
            config.setGmtCreate(LocalDateTime.now());
            config.setGmtModified(LocalDateTime.now());

            int result = getMapper().insert(config);
            if (result > 0) {
                return ActionResult.isSuccess();
            }
            return ActionResult.fail("SAVE_CONFIG_ERROR", "保存配置失败", null);
        } catch (Exception e) {
            log.error("Failed to save data generation config", e);
            return ActionResult.fail("SAVE_CONFIG_ERROR", "保存配置失败: " + e.getMessage(), null);
        }
    }

    @Override
    public DataResult<DataGenerationConfigQueryParam> getConfigByTable(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        try {
            QueryWrapper<DataGenerationConfigDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("data_source_id", dataSourceId);
            queryWrapper.eq("database_name", databaseName);
            queryWrapper.eq("table_name", tableName);
            if (schemaName != null) {
                queryWrapper.eq("schema_name", schemaName);
            } else {
                queryWrapper.isNull("schema_name");
            }

            DataGenerationConfigDO config = getMapper().selectOne(queryWrapper);
            if (config == null) {
                DataGenerationConfigQueryParam defaultParam = new DataGenerationConfigQueryParam();
                defaultParam.setDataSourceId(dataSourceId);
                defaultParam.setDatabaseName(databaseName);
                defaultParam.setSchemaName(schemaName);
                defaultParam.setTableName(tableName);
                defaultParam.setRowCount(100);
                defaultParam.setBatchSize(1000);
                return DataResult.of(defaultParam);
            }
            return DataResult.of(toQueryParam(config));
        } catch (Exception e) {
            log.error("Failed to get data generation config by table", e);
            return DataResult.error("GET_CONFIG_ERROR", "获取配置失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult deleteConfig(Long id) {
        try {
            int result = getMapper().deleteById(id);
            if (result > 0) {
                return ActionResult.isSuccess();
            }
            return ActionResult.fail("DELETE_CONFIG_ERROR", "删除配置失败", null);
        } catch (Exception e) {
            log.error("Failed to delete data generation config", e);
            return ActionResult.fail("DELETE_CONFIG_ERROR", "删除配置失败: " + e.getMessage(), null);
        }
    }
}
