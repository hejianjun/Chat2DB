package ai.chat2db.server.web.api.controller.sql.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SqlFileExecuteRequest extends DataSourceBaseRequest {

    /**
     * Continue executing statements after an error.
     */
    private boolean skipErrors;

    /**
     * Commit after this many successful statements. Zero commits once after all statements.
     */
    @Min(0)
    @Max(100000)
    private Integer commitBatchSize = 0;
}
