package ai.chat2db.server.domain.api.param;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * Parameters for executing MCP batch update SQL in one transaction.
 */
@Data
public class DlBatchUpdateParam {

    @NotNull
    private List<String> sqlList;

    @NotNull
    private List<Integer> expectedUpdateCounts;

    @NotNull
    private Long consoleId;

    @NotNull
    private Long dataSourceId;

    @NotNull
    private String databaseName;

    private String schemaName;
}
