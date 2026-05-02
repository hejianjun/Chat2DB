package ai.chat2db.server.web.api.controller.rdb.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ForeignKeySyncRequest {

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String tableName;
}
