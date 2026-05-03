package ai.chat2db.server.web.api.controller.rdb.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeleteFKByNameRequest {

    @NotNull
    private Long dataSourceId;

    @NotBlank
    private String databaseName;

    private String schemaName;

    @NotBlank
    private String tableName;

    @NotBlank
    private String keyName;
}
