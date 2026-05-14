package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.ColumnGenerationRuleParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

import java.util.List;

public interface DataGenerationRuleService {

    ListResult<ColumnGenerationRuleParam> getRulesByTable(Long dataSourceId, String databaseName, String schemaName, String tableName);

    ActionResult saveRulesByTable(Long dataSourceId, String databaseName, String schemaName, String tableName, Long userId, List<ColumnGenerationRuleParam> rules);

    ActionResult deleteRule(Long id);
}
