package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.DataGenerationConfigQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationConfigSaveParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;

public interface DataGenerationConfigService {

    ActionResult saveConfig(DataGenerationConfigSaveParam param);

    DataResult<DataGenerationConfigQueryParam> getConfigByTable(Long dataSourceId, String databaseName, String schemaName, String tableName);

    ActionResult deleteConfig(Long id);
}
