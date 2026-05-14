package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.domain.api.param.DataGenerationRuleQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationRuleSaveParam;
import ai.chat2db.server.domain.api.service.DataGenerationRuleService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.rdb.converter.DataGenerationRuleConverter;
import ai.chat2db.server.web.api.controller.rdb.vo.DataGenerationRuleVO;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 数据生成规则控制器
 */
@Slf4j
@ConnectionInfoAspect
@RequestMapping("/api/rdb/table/generate-data/rules")
@RestController
public class DataGenerationRuleController {

    @Autowired
    private DataGenerationRuleService dataGenerationRuleService;

    /**
     * 保存数据生成规则
     */
    @PostMapping("/save")
    public ActionResult saveRule(@RequestBody DataGenerationRuleVO ruleVO) {
        try {
            DataGenerationRuleSaveParam param = DataGenerationRuleConverter.voToSaveParam(ruleVO);
            return dataGenerationRuleService.saveRule(param);
        } catch (Exception e) {
            log.error("Failed to save data generation rule", e);
            return ActionResult.fail("SAVE_RULE_ERROR", "保存规则失败: " + e.getMessage(), null);
        }
    }

    /**
     * 查询数据生成规则
     */
    @GetMapping("/list")
    public ListResult<DataGenerationRuleVO> queryRules(
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String databaseName,
            @RequestParam(required = false) String schemaName,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String columnName) {
        try {
            DataGenerationRuleQueryParam param = new DataGenerationRuleQueryParam();
            param.setDataSourceId(dataSourceId);
            param.setDatabaseName(databaseName);
            param.setSchemaName(schemaName);
            param.setTableName(tableName);
            param.setColumnName(columnName);
            
            ListResult<DataGenerationRuleQueryParam> result = dataGenerationRuleService.queryRules(param);
            
            // 转换为VO
            List<DataGenerationRuleVO> voList = new ArrayList<>();
            for (DataGenerationRuleQueryParam queryParam : result.getData()) {
                DataGenerationRuleVO vo = DataGenerationRuleConverter.paramToVO(queryParam);
                voList.add(vo);
            }
            
            return ListResult.of(voList);
        } catch (Exception e) {
            log.error("Failed to query data generation rules", e);
            return ListResult.error("QUERY_RULES_ERROR", "查询规则失败: " + e.getMessage());
        }
    }

    /**
     * 根据表信息获取数据生成规则
     */
    @GetMapping("/table")
    public ListResult<DataGenerationRuleVO> getRulesByTable(
            @RequestParam Long dataSourceId,
            @RequestParam String databaseName,
            @RequestParam(required = false) String schemaName,
            @RequestParam String tableName) {
        try {
            ListResult<DataGenerationRuleQueryParam> result = dataGenerationRuleService.getRulesByTable(
                dataSourceId, databaseName, schemaName, tableName);
            
            // 转换为VO
            List<DataGenerationRuleVO> voList = new ArrayList<>();
            for (DataGenerationRuleQueryParam queryParam : result.getData()) {
                DataGenerationRuleVO vo = DataGenerationRuleConverter.paramToVO(queryParam);
                voList.add(vo);
            }
            
            return ListResult.of(voList);
        } catch (Exception e) {
            log.error("Failed to get data generation rules by table", e);
            return ListResult.error("GET_RULES_BY_TABLE_ERROR", "获取表规则失败: " + e.getMessage());
        }
    }

    /**
     * 删除数据生成规则
     */
    @DeleteMapping("/{id}")
    public ActionResult deleteRule(@PathVariable Long id) {
        try {
            return dataGenerationRuleService.deleteRule(id);
        } catch (Exception e) {
            log.error("Failed to delete data generation rule", e);
            return ActionResult.fail("DELETE_RULE_ERROR", "删除规则失败: " + e.getMessage(), null);
        }
    }
}
