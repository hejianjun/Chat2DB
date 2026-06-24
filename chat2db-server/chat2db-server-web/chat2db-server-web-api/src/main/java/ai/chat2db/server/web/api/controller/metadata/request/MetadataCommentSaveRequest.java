package ai.chat2db.server.web.api.controller.metadata.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MetadataCommentSaveRequest {

    @NotNull
    private Long dataSourceId;

    @NotBlank
    private String objectType;

    private String databaseName;

    private String schemaName;

    private String objectName;

    private String comment;

    private String aiComment;
}
