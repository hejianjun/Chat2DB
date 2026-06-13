package ai.chat2db.server.domain.core.converter;

import ai.chat2db.server.domain.api.param.DeprecatedTableParam;
import ai.chat2db.server.domain.repository.entity.DeprecatedTableDO;
import org.springframework.stereotype.Component;

@Component
public class DeprecatedTableConverter {

    public DeprecatedTableDO param2do(DeprecatedTableParam param) {
        DeprecatedTableDO deprecatedTableDO = new DeprecatedTableDO();
        deprecatedTableDO.setDataSourceId(param.getDataSourceId());
        deprecatedTableDO.setDatabaseName(param.getDatabaseName());
        deprecatedTableDO.setSchemaName(param.getSchemaName());
        deprecatedTableDO.setTableName(param.getTableName());
        deprecatedTableDO.setUserId(param.getUserId());
        return deprecatedTableDO;
    }
}
