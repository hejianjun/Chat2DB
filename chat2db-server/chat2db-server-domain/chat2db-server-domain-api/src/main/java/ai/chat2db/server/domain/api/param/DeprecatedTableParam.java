package ai.chat2db.server.domain.api.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeprecatedTableParam {

    @NotNull
    private Long dataSourceId;

    /**
     * DB名称
     */
    private String databaseName;

    /**
     * 表所在空间
     */
    private String schemaName;

    /**
     * tableName
     */
    private String tableName;

    /**
     * deprecated userId
     */
    private Long userId;
}
