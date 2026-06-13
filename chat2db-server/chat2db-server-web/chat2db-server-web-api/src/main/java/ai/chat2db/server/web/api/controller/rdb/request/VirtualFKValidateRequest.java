package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequestInfo;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class VirtualFKValidateRequest implements DataSourceBaseRequestInfo {

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private ForeignKeySyncService.ForeignKeyRelation relation = ForeignKeySyncService.ForeignKeyRelation.SOURCE;

    private List<Long> ids;
}
