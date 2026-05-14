package ai.chat2db.server.domain.api.param;

import lombok.Data;

import java.io.Serializable;

@Data
public class DataGenerationConfigQueryParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private Integer rowCount;

    private Integer batchSize;

    private Long userId;
}
