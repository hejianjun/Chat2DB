package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequestInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ImportVirtualFKRequest implements DataSourceBaseRequestInfo {

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    @Valid
    @NotEmpty
    private List<VirtualFKImportItemRequest> virtualForeignKeys;
}
