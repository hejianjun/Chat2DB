package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.DataGenerationRuleQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationRuleSaveParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

public interface DataGenerationRuleService {

    ActionResult saveRule(DataGenerationRuleSaveParam param);

    ActionResult batchSaveRules(java.util.List<DataGenerationRuleSaveParam> params);

    ListResult<DataGenerationRuleQueryParam> queryRules(DataGenerationRuleQueryParam param);

    ActionResult deleteRule(Long id);

    ListResult<DataGenerationRuleQueryParam> getRulesByTable(Long dataSourceId, String databaseName, String schemaName, String tableName);
}
