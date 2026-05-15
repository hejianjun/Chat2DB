package ai.chat2db.server.web.api.controller.task.response;

import lombok.Data;

/**
 * 表字段信息
 */
@Data
public class TableColumnInfo {
    /**
     * 字段名
     */
    private String name;

    /**
     * 字段类型
     */
    private String type;

    /**
     * 是否主键
     */
    private boolean primaryKey;
}
