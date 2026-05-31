package ai.chat2db.server.domain.api.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 外键关系导出信息
 */
@Data
@Accessors(chain = true)
public class ForeignKeyInfo {
    /**
     * 外键名称
     */
    private String name;
    /**
     * 子表（拥有外键的表）
     */
    private String tableName;
    /**
     * 子表列
     */
    private String columnName;
    /**
     * 主表（被引用的表）
     */
    private String referencedTable;
    /**
     * 主表列
     */
    private String referencedColumnName;
    /**
     * 删除规则
     */
    private String deleteRule;
    /**
     * 更新规则
     */
    private String updateRule;
    /**
     * 来源类型（REAL/VIRTUAL）
     */
    private String sourceType;
    /**
     * 注释
     */
    private String comment;
}
