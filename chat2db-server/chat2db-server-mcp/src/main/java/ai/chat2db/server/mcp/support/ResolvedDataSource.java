package ai.chat2db.server.mcp.support;

import ai.chat2db.server.domain.api.model.DataSource;
import lombok.Builder;
import lombok.Data;

/**
 * Datasource identity resolved from a signed MCP datasource reference.
 */
@Data
@Builder
public class ResolvedDataSource {

    private Long dataSourceId;

    private DataSource dataSource;

    private String summary;
}
