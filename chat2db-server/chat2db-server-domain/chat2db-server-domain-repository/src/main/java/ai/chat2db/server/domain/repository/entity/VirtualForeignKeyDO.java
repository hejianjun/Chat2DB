package ai.chat2db.server.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("virtual_foreign_key")
public class VirtualForeignKeyDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String tableName;

    @TableField("column_name")
    private String columnName;

    @TableField("vk_name")
    private String vkName;

    @TableField("referenced_table")
    private String referencedTable;

    @TableField("referenced_column")
    private String referencedColumnName;

    private String comment;

    @TableField("source_type")
    private String sourceType;

    @TableField("user_id")
    private Long userId;
}
