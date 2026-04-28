package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.DeprecatedTableParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

import java.util.List;

public interface DeprecatedTableService {

    /**
     * User deprecated table
     * @param param
     * @return
     */
    ActionResult deprecatedTable(DeprecatedTableParam param);


    /**
     * Delete deprecated table
     * @param param
     * @return
     */
    ActionResult deleteDeprecatedTable(DeprecatedTableParam param);


    /**
     * Query user deprecated tables
     * @param param
     * @return
     */
    ListResult<String> queryDeprecatedTables(DeprecatedTableParam param);
}
