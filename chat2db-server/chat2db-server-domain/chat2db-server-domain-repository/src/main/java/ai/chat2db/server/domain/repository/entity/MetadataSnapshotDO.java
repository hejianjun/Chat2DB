package ai.chat2db.server.domain.repository.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("metadata_snapshot")
public class MetadataSnapshotDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private Long dataSourceId;

    private String objectType;

    private String objectKey;

    private String databaseName;

    private String schemaName;

    private String objectName;

    private Long objectCount;

    private Long version;

    private String status;

    private LocalDateTime refreshedAt;

    private String errorMessage;
}
