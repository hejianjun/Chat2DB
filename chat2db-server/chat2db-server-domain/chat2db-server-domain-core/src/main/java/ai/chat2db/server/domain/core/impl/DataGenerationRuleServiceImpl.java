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

@Slf4j
@Service
public class DataGenerationRuleServiceImpl implements DataGenerationRuleService {

    private DataGenerationRuleMapper getMapper() {
        return Dbutils.getMapper(DataGenerationRuleMapper.class);
    }

    private DataGenerationRuleQueryParam toQueryParam(DataGenerationRuleDO rule) {
        DataGenerationRuleQueryParam param = new DataGenerationRuleQueryParam();
        param.setId(rule.getId());
        param.setDataSourceId(rule.getDataSourceId());
        param.setDatabaseName(rule.getDatabaseName());
        param.setSchemaName(rule.getSchemaName());
        param.setTableName(rule.getTableName());
        param.setColumnName(rule.getColumnName());
        param.setGenerationType(rule.getGenerationType());
        param.setSubType(rule.getSubType());
        param.setCustomParams(rule.getCustomParams());
        param.setComment(rule.getComment());
        param.setUserId(rule.getUserId());
        return param;
    }

    @Override
    public ActionResult saveRule(DataGenerationRuleSaveParam param) {
        try {
            DataGenerationRuleDO rule = new DataGenerationRuleDO();

            if (param.getId() != null) {
                rule.setId(param.getId());
                rule.setGenerationType(param.getGenerationType());
                rule.setSubType(param.getSubType());
                rule.setCustomParams(param.getCustomParams());
                rule.setComment(param.getComment());
                rule.setGmtModified(LocalDateTime.now());

                int result = getMapper().updateById(rule);
                if (result > 0) {
                    return ActionResult.isSuccess();
                }
                return ActionResult.fail("UPDATE_RULE_ERROR", "更新规则失败", null);
            } else {
                rule.setDataSourceId(param.getDataSourceId());
                rule.setDatabaseName(param.getDatabaseName());
                rule.setSchemaName(param.getSchemaName());
                rule.setTableName(param.getTableName());
                rule.setColumnName(param.getColumnName());
                rule.setGenerationType(param.getGenerationType());
                rule.setSubType(param.getSubType());
                rule.setCustomParams(param.getCustomParams());
                rule.setComment(param.getComment());
                rule.setUserId(param.getUserId());
                rule.setGmtCreate(LocalDateTime.now());
                rule.setGmtModified(LocalDateTime.now());

                int result = getMapper().insert(rule);
                if (result > 0) {
                    return ActionResult.isSuccess();
                }
                return ActionResult.fail("SAVE_RULE_ERROR", "保存规则失败", null);
            }
        } catch (Exception e) {
            log.error("Failed to save data generation rule", e);
            return ActionResult.fail("SAVE_RULE_ERROR", "保存规则失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ActionResult batchSaveRules(List<DataGenerationRuleSaveParam> params) {
        if (params == null || params.isEmpty()) {
            return ActionResult.isSuccess();
        }
        try {
            DataGenerationRuleSaveParam first = params.get(0);
            Long dataSourceId = first.getDataSourceId();
            String databaseName = first.getDatabaseName();
            String schemaName = first.getSchemaName();
            String tableName = first.getTableName();
            Long userId = first.getUserId();

            QueryWrapper<DataGenerationRuleDO> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("data_source_id", dataSourceId);
            deleteWrapper.eq("database_name", databaseName);
            if (schemaName != null) {
                deleteWrapper.eq("schema_name", schemaName);
            } else {
                deleteWrapper.isNull("schema_name");
            }
            deleteWrapper.eq("table_name", tableName);
            getMapper().delete(deleteWrapper);

            LocalDateTime now = LocalDateTime.now();
            List<DataGenerationRuleDO> rules = new ArrayList<>();
            for (DataGenerationRuleSaveParam p : params) {
                DataGenerationRuleDO rule = new DataGenerationRuleDO();
                rule.setDataSourceId(dataSourceId);
                rule.setDatabaseName(databaseName);
                rule.setSchemaName(schemaName);
                rule.setTableName(tableName);
                rule.setColumnName(p.getColumnName());
                rule.setGenerationType(p.getGenerationType());
                rule.setSubType(p.getSubType());
                rule.setCustomParams(p.getCustomParams());
                rule.setComment(p.getComment());
                rule.setUserId(userId);
                rule.setGmtCreate(now);
                rule.setGmtModified(now);
                rules.add(rule);
            }
            for (DataGenerationRuleDO rule : rules) {
                getMapper().insert(rule);
            }
            return ActionResult.isSuccess();
        } catch (Exception e) {
            log.error("Failed to batch save data generation rules", e);
            return ActionResult.fail("BATCH_SAVE_RULES_ERROR", "批量保存规则失败: " + e.getMessage(), null);
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
                result.add(toQueryParam(rule));
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
            }
            return ActionResult.fail("DELETE_RULE_ERROR", "删除规则失败", null);
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
                result.add(toQueryParam(rule));
            }
            return ListResult.of(result);
        } catch (Exception e) {
            log.error("Failed to get data generation rules by table", e);
            return ListResult.error("GET_RULES_BY_TABLE_ERROR", "获取表规则失败: " + e.getMessage());
        }
    }
}
