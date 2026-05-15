package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.ColumnConfigParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

import java.util.List;

public interface DataGenerationRuleService {

    ListResult<ColumnConfigParam> getColumnConfigs(Long dataSourceId, String databaseName, String schemaName, String tableName);

    ActionResult saveColumnConfigs(Long dataSourceId, String databaseName, String schemaName, String tableName, Long userId, List<ColumnConfigParam> configs, Integer rowCount);
}
