package ai.chat2db.server.web.api.controller.rdb.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TableTagUpdateRequest {

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    @NotBlank
    private String tableName;

    /** An empty value intentionally clears an existing tag. */
    private String tagName;
}
