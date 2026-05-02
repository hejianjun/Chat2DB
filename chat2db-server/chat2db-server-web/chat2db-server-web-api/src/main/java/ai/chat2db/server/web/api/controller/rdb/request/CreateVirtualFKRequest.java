package ai.chat2db.server.web.api.controller.rdb.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateVirtualFKRequest {

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    @NotBlank
    private String tableName;

    @NotBlank
    private String columnName;

    @NotBlank
    private String referencedTable;

    @NotBlank
    private String referencedColumnName;

    private String comment;
}
