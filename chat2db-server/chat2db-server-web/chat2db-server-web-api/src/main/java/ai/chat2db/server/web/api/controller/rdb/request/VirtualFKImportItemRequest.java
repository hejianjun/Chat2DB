package ai.chat2db.server.web.api.controller.rdb.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VirtualFKImportItemRequest {

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
