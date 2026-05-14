package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.DataGenerationRuleQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationRuleSaveParam;
import ai.chat2db.server.domain.api.service.DataGenerationRuleService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DataGenerationRuleDO;
import ai.chat2db.server.domain.repository.mapper.DataGenerationRuleMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据生成规则服务实现
 */
@Slf4j
@Service
public class DataGenerationRuleServiceImpl implements DataGenerationRuleService {

    private DataGenerationRuleMapper getMapper() {
        return Dbutils.getMapper(DataGenerationRuleMapper.class);
    }
    @Override
    public ActionResult saveRule(DataGenerationRuleSaveParam param) {
        try {
            DataGenerationRuleDO rule = new DataGenerationRuleDO();

            if (param.getId() != null) {
                // 更新现有规则
                rule.setId(param.getId());
                rule.setGenerationType(param.getGenerationType());
                rule.setRowCount(param.getRowCount());
                rule.setBatchSize(param.getBatchSize());
                rule.setCustomParams(param.getCustomParams());
                rule.setComment(param.getComment());
                rule.setGmtModified(LocalDateTime.now());

                int result = getMapper().updateById(rule);
                if (result > 0) {
                    return ActionResult.isSuccess();
                } else {
                    return ActionResult.fail("UPDATE_RULE_ERROR", "更新规则失败", null);
                }
            } else {
                // 创建新规则
                rule.setDataSourceId(param.getDataSourceId());
                rule.setDatabaseName(param.getDatabaseName());
                rule.setSchemaName(param.getSchemaName());
                rule.setTableName(param.getTableName());
                rule.setColumnName(param.getColumnName());
                rule.setGenerationType(param.getGenerationType());
                rule.setRowCount(param.getRowCount());
                rule.setBatchSize(param.getBatchSize());
                rule.setCustomParams(param.getCustomParams());
                rule.setComment(param.getComment());
                rule.setUserId(param.getUserId());
                rule.setGmtCreate(LocalDateTime.now());
                rule.setGmtModified(LocalDateTime.now());

                int result = getMapper().insert(rule);
                if (result > 0) {
                    return ActionResult.isSuccess();
                } else {
                    return ActionResult.fail("SAVE_RULE_ERROR", "保存规则失败", null);
                }
            }
        } catch (Exception e) {
            log.error("Failed to save data generation rule", e);
            return ActionResult.fail("SAVE_RULE_ERROR", "保存规则失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ListResult<DataGenerationRuleQueryParam> queryRules(DataGenerationRuleQueryParam param) {
        try {
            QueryWrapper<DataGenerationRuleDO> queryWrapper = new QueryWrapper<>();

            if (param.getId() != null) {
                queryWrapper.eq("id", param.getId());
            }
            if (param.getDataSourceId() != null) {
                queryWrapper.eq("data_source_id", param.getDataSourceId());
            }
            if (param.getDatabaseName() != null) {
                queryWrapper.eq("database_name", param.getDatabaseName());
            }
            if (param.getSchemaName() != null) {
                queryWrapper.eq("schema_name", param.getSchemaName());
            }
            if (param.getTableName() != null) {
                queryWrapper.eq("table_name", param.getTableName());
            }
            if (param.getColumnName() != null) {
                queryWrapper.eq("column_name", param.getColumnName());
            }
            if (param.getUserId() != null) {
                queryWrapper.eq("user_id", param.getUserId());
            }

            queryWrapper.orderByDesc("gmt_modified");

            List<DataGenerationRuleDO> rules = getMapper().selectList(queryWrapper);
            List<DataGenerationRuleQueryParam> result = new ArrayList<>();

            for (DataGenerationRuleDO rule : rules) {
                DataGenerationRuleQueryParam queryParam = new DataGenerationRuleQueryParam();
                queryParam.setId(rule.getId());
                queryParam.setDataSourceId(rule.getDataSourceId());
                queryParam.setDatabaseName(rule.getDatabaseName());
                queryParam.setSchemaName(rule.getSchemaName());
                queryParam.setTableName(rule.getTableName());
                queryParam.setColumnName(rule.getColumnName());
                queryParam.setGenerationType(rule.getGenerationType());
                queryParam.setRowCount(rule.getRowCount());
                queryParam.setBatchSize(rule.getBatchSize());
                queryParam.setCustomParams(rule.getCustomParams());
                queryParam.setComment(rule.getComment());
                queryParam.setUserId(rule.getUserId());
                result.add(queryParam);
            }

            return ListResult.of(result);
        } catch (Exception e) {
            log.error("Failed to query data generation rules", e);
            return ListResult.error("QUERY_RULES_ERROR", "查询规则失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult deleteRule(Long id) {
        try {
            int result = getMapper().deleteById(id);
            if (result > 0) {
                return ActionResult.isSuccess();
            } else {
                return ActionResult.fail("DELETE_RULE_ERROR", "删除规则失败", null);
            }
        } catch (Exception e) {
            log.error("Failed to delete data generation rule", e);
            return ActionResult.fail("DELETE_RULE_ERROR", "删除规则失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ListResult<DataGenerationRuleQueryParam> getRulesByTable(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        try {
            QueryWrapper<DataGenerationRuleDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("data_source_id", dataSourceId);
            queryWrapper.eq("database_name", databaseName);
            queryWrapper.eq("table_name", tableName);

            if (schemaName != null) {
                queryWrapper.eq("schema_name", schemaName);
            }

            List<DataGenerationRuleDO> rules = getMapper().selectList(queryWrapper);
            List<DataGenerationRuleQueryParam> result = new ArrayList<>();
            
            for (DataGenerationRuleDO rule : rules) {
                DataGenerationRuleQueryParam queryParam = new DataGenerationRuleQueryParam();
                queryParam.setId(rule.getId());
                queryParam.setDataSourceId(rule.getDataSourceId());
                queryParam.setDatabaseName(rule.getDatabaseName());
                queryParam.setSchemaName(rule.getSchemaName());
                queryParam.setTableName(rule.getTableName());
                queryParam.setColumnName(rule.getColumnName());
                queryParam.setGenerationType(rule.getGenerationType());
                queryParam.setRowCount(rule.getRowCount());
                queryParam.setBatchSize(rule.getBatchSize());
                queryParam.setCustomParams(rule.getCustomParams());
                queryParam.setComment(rule.getComment());
                queryParam.setUserId(rule.getUserId());
                result.add(queryParam);
            }
            
            return ListResult.of(result);
        } catch (Exception e) {
            log.error("Failed to get data generation rules by table", e);
            return ListResult.error("GET_RULES_BY_TABLE_ERROR", "获取表规则失败: " + e.getMessage());
        }
    }
}
