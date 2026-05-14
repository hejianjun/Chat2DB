package ai.chat2db.server.domain.api.param;

import lombok.Data;

import java.io.Serializable;

@Data
public class DataGenerationRuleQueryParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private String columnName;

    private String generationType;

    private String subType;

    private String customParams;

    private String comment;

    private Long userId;
}
