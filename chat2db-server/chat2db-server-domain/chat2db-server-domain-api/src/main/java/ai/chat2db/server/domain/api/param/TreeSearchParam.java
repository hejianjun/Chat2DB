package ai.chat2db.server.domain.api.param;

import ai.chat2db.server.tools.base.wrapper.param.PageQueryParam;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TreeSearchParam extends PageQueryParam {
    private static final long serialVersionUID = 1L;

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String searchKey;

    private String treeNodeType;

    private boolean refresh;
}