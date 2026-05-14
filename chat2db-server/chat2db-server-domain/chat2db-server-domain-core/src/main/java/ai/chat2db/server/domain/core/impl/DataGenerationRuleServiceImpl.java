package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.ColumnGenerationRuleParam;
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

    private ColumnGenerationRuleParam toParam(DataGenerationRuleDO rule) {
        ColumnGenerationRuleParam param = new ColumnGenerationRuleParam();
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
    public ListResult<ColumnGenerationRuleParam> getRulesByTable(Long dataSourceId, String databaseName, String schemaName, String tableName) {
        try {
            QueryWrapper<DataGenerationRuleDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("data_source_id", dataSourceId);
            queryWrapper.eq("database_name", databaseName);
            queryWrapper.eq("table_name", tableName);
            if (schemaName != null) {
                queryWrapper.eq("schema_name", schemaName);
            } else {
                queryWrapper.isNull("schema_name");
            }

            List<DataGenerationRuleDO> rules = getMapper().selectList(queryWrapper);
            List<ColumnGenerationRuleParam> result = new ArrayList<>();
            for (DataGenerationRuleDO rule : rules) {
                result.add(toParam(rule));
            }
            return ListResult.of(result);
        } catch (Exception e) {
            log.error("Failed to get data generation rules by table", e);
            return ListResult.error("GET_RULES_BY_TABLE_ERROR", "获取规则失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult saveRulesByTable(Long dataSourceId, String databaseName, String schemaName, String tableName, Long userId, List<ColumnGenerationRuleParam> rules) {
        if (rules == null || rules.isEmpty()) {
            return ActionResult.isSuccess();
        }
        try {
            QueryWrapper<DataGenerationRuleDO> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("data_source_id", dataSourceId);
            deleteWrapper.eq("database_name", databaseName);
            deleteWrapper.eq("table_name", tableName);
            if (schemaName != null) {
                deleteWrapper.eq("schema_name", schemaName);
            } else {
                deleteWrapper.isNull("schema_name");
            }
            getMapper().delete(deleteWrapper);

            LocalDateTime now = LocalDateTime.now();
            for (ColumnGenerationRuleParam r : rules) {
                DataGenerationRuleDO rule = new DataGenerationRuleDO();
                rule.setDataSourceId(dataSourceId);
                rule.setDatabaseName(databaseName);
                rule.setSchemaName(schemaName);
                rule.setTableName(tableName);
                rule.setColumnName(r.getColumnName());
                rule.setGenerationType(r.getGenerationType());
                rule.setSubType(r.getSubType());
                rule.setCustomParams(r.getCustomParams());
                rule.setComment(r.getComment());
                rule.setUserId(userId);
                rule.setGmtCreate(now);
                rule.setGmtModified(now);
                getMapper().insert(rule);
            }
            return ActionResult.isSuccess();
        } catch (Exception e) {
            log.error("Failed to save data generation rules", e);
            return ActionResult.fail("SAVE_RULES_ERROR", "保存规则失败: " + e.getMessage(), null);
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
}
