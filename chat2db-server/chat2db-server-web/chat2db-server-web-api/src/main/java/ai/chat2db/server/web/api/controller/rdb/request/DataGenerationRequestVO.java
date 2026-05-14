package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequestInfo;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DataGenerationRequestVO implements DataSourceBaseRequestInfo {

    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private Integer rowCount;

    private List<ColumnConfigVO> columnConfigs;

    private Integer batchSize = 1000;

    private Boolean previewMode = false;

    @Data
    public static class ColumnConfigVO {
        private String columnName;
        private String dataType;
        private String generationType;
        private String subType;
        private String comment;
        private Boolean nullable;
        private Integer maxLength;
        private Integer scale;
        private Map<String, Object> customParams;
    }
}
