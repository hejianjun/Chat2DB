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
@TableName("data_generation_rule")
public class DataGenerationRuleDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    @TableField("data_source_id")
    private Long dataSourceId;

    @TableField("database_name")
    private String databaseName;

    @TableField("schema_name")
    private String schemaName;

    @TableField("table_name")
    private String tableName;

    @TableField("column_name")
    private String columnName;

    @TableField("generation_type")
    private String generationType;

    @TableField("row_count")
    private Integer rowCount;

    @TableField("batch_size")
    private Integer batchSize;

    @TableField("custom_params")
    private String customParams;

    private String comment;

    @TableField("user_id")
    private Long userId;
}
