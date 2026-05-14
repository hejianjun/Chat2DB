package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.domain.api.param.DataGenerationRuleQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationRuleSaveParam;
import ai.chat2db.server.domain.api.service.DataGenerationRuleService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/rdb/table/generation-rule")
public class DataGenerationRuleController {

    @Autowired
    private DataGenerationRuleService ruleService;

    @PostMapping("/save")
    public ActionResult saveRule(@RequestBody DataGenerationRuleSaveParam param) {
        try {
            return ruleService.saveRule(param);
        } catch (Exception e) {
            log.error("Failed to save data generation rule", e);
            return ActionResult.fail("SAVE_RULE_ERROR", "保存规则失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/batch-save")
    public ActionResult batchSaveRules(@RequestBody List<DataGenerationRuleSaveParam> params) {
        try {
            return ruleService.batchSaveRules(params);
        } catch (Exception e) {
            log.error("Failed to batch save data generation rules", e);
            return ActionResult.fail("BATCH_SAVE_RULES_ERROR", "批量保存规则失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/query")
    public ListResult<DataGenerationRuleQueryParam> queryRules(@RequestBody DataGenerationRuleQueryParam param) {
        try {
            return ruleService.queryRules(param);
        } catch (Exception e) {
            log.error("Failed to query data generation rules", e);
            return ListResult.error("QUERY_RULES_ERROR", "查询规则失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ActionResult deleteRule(@PathVariable Long id) {
        try {
            return ruleService.deleteRule(id);
        } catch (Exception e) {
            log.error("Failed to delete data generation rule", e);
            return ActionResult.fail("DELETE_RULE_ERROR", "删除规则失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/list")
    public ListResult<DataGenerationRuleQueryParam> getRulesByTable(
            @RequestParam Long dataSourceId,
            @RequestParam String databaseName,
            @RequestParam(required = false) String schemaName,
            @RequestParam String tableName) {
        try {
            return ruleService.getRulesByTable(dataSourceId, databaseName, schemaName, tableName);
        } catch (Exception e) {
            log.error("Failed to get data generation rules by table", e);
            return ListResult.error("GET_RULES_BY_TABLE_ERROR", "获取表规则失败: " + e.getMessage());
        }
    }
}
