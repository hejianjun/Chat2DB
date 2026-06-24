package ai.chat2db.server.domain.api.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

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
