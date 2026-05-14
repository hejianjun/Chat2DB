package ai.chat2db.server.domain.api.param;

import lombok.Data;

import java.util.List;

@Data
public class DataGenerationRequest {

    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private Integer rowCount;

    private List<ColumnConfigParam> columnConfigs;

    private Integer batchSize = 1000;

    private Boolean previewMode = false;
}
