package ai.chat2db.server.domain.api.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DataGenerationPreviewVO {

    private String tableName;

    private List<Map<String, Object>> previewData;

    private List<ColumnInfo> columns;

    @Data
    public static class ColumnInfo {
        private String columnName;

        private String dataType;

        private String comment;
    }
}
