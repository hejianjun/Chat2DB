package ai.chat2db.server.domain.api.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataComment implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long dataSourceId;

    private Long userId;

    private String objectType;

    private String objectKey;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private String columnName;

    private String objectName;

    private String comment;

    private String aiComment;
}
