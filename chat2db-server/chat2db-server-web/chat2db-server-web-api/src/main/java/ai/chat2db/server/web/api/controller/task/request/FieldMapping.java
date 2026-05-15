package ai.chat2db.server.web.api.controller.task.request;

import lombok.Data;

/**
 * 字段映射配置
 */
@Data
public class FieldMapping {
    /**
     * 源字段名（文件列名）
     */
    private String sourceField;

    /**
     * 目标字段名（表字段）
     */
    private String targetField;

    /**
     * 是否主键
     */
    private Boolean primaryKey;
}
