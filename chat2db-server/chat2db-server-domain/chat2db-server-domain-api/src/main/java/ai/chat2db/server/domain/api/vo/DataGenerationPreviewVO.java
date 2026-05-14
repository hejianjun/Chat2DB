package ai.chat2db.server.domain.api.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据生成预览响应
 */
@Data
public class DataGenerationPreviewVO {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 预览数据列表
     */
    private List<Map<String, Object>> previewData;

    /**
     * 列信息
     */
    private List<ColumnInfo> columns;

    @Data
    public static class ColumnInfo {
        /**
         * 列名
         */
        private String columnName;

        /**
         * 数据类型
         */
        private String dataType;

        /**
         * 生成类型
         */
        private String generationType;

        /**
         * 列注释
         */
        private String comment;
    }
}
