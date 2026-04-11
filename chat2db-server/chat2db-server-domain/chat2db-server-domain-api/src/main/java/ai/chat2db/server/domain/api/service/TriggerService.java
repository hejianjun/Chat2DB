package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.TreeNode;
import ai.chat2db.server.domain.api.param.TreeSearchParam;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.model.Trigger;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public interface TriggerService {

    /**
     * Querying all triggers under a schema.
     *
     * @param databaseName
     * @return
     */
    ListResult<Trigger> triggers(@NotEmpty String databaseName, String schemaName);

    /**
     * Querying trigger information.
     * @param databaseName
     * @param schemaName
     * @param triggerName
     * @return
     */
    DataResult<Trigger> detail(String databaseName, String schemaName, String triggerName);

    /**
     * Search tree nodes for triggers.
     *
     * @param param
     * @return
     */
    List<TreeNode> searchTreeNodes(TreeSearchParam param);
}
