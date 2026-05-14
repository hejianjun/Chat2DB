package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.DataGenerationRuleQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationRuleSaveParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

/**
 * 数据生成规则服务接口
 */
public interface DataGenerationRuleService {

    /**
     * 保存数据生成规则
     *
     * @param param 保存参数
     * @return 保存结果
     */
    ActionResult saveRule(DataGenerationRuleSaveParam param);

    /**
     * 查询数据生成规则
     *
     * @param param 查询参数
     * @return 规则列表
     */
    ListResult<DataGenerationRuleQueryParam> queryRules(DataGenerationRuleQueryParam param);

    /**
     * 删除数据生成规则
     *
     * @param id 规则ID
     * @return 删除结果
     */
    ActionResult deleteRule(Long id);

    /**
     * 根据表信息获取数据生成规则
     *
     * @param dataSourceId 数据源ID
     * @param databaseName 数据库名
     * @param schemaName   模式名
     * @param tableName    表名
     * @return 规则列表
     */
    ListResult<DataGenerationRuleQueryParam> getRulesByTable(Long dataSourceId, String databaseName, String schemaName, String tableName);
}
