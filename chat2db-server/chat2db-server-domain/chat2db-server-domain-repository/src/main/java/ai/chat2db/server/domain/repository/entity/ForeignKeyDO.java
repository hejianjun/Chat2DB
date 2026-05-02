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
@TableName("foreign_key")
public class ForeignKeyDO implements Serializable {

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

    @TableField("fk_name")
    private String fkName;

    @TableField("referenced_table")
    private String referencedTable;

    @TableField("referenced_column")
    private String referencedColumnName;

    @TableField("referenced_schema")
    private String referencedSchema;

    @TableField("referenced_database")
    private String referencedDatabase;

    private Integer updateRule;

    private Integer deleteRule;

    private String comment;

    private LocalDateTime syncTime;

    private String syncVersion;
}
