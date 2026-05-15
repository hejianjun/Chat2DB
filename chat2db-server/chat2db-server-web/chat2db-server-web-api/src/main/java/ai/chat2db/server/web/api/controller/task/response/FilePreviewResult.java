package ai.chat2db.server.web.api.controller.task.response;

import lombok.Data;

import java.util.List;

/**
 * 文件预览结果
 */
@Data
public class FilePreviewResult {
    /**
     * 文件列头列表
     */
    private List<String> fileHeaders;

    /**
     * 目标表字段列表
     */
    private List<TableColumnInfo> tableColumns;

    /**
     * 自动匹配结果
     */
    private List<AutoMapping> autoMappings;

    @Data
    public static class AutoMapping {
        /**
         * 源字段名
         */
        private String sourceField;

        /**
         * 目标字段名
         */
        private String targetField;

        /**
         * 是否自动匹配成功（同名字段）
         */
        private boolean matched;
    }
}
