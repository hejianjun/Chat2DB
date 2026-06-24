package ai.chat2db.server.domain.api.param;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataCommentParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    private Long dataSourceId;

    private Long userId;

    @NotBlank
    private String objectType;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private String columnName;

    @NotBlank
    private String objectName;

    private String comment;

    private String aiComment;
}
