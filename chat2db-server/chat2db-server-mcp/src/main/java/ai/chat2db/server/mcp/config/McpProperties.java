package ai.chat2db.server.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Chat2DB MCP server.
 */
@Data
@ConfigurationProperties(prefix = "chat2db.mcp")
public class McpProperties {

    /** Enables or disables the MCP server and its related beans. */
    private boolean enabled = true;

    /** Optional bearer token required by MCP HTTP endpoints. */
    private String token;

    /** SQL execution policy exposed to MCP tools. */
    private Sql sql = new Sql();

    /**
     * Safety limits for SQL tools. Write and DDL execution stay disabled unless explicitly enabled.
     */
    @Data
    public static class Sql {
        /** Restricts the normal query tool to read-only statements. */
        private boolean readOnly = true;

        /** Allows DML statements through the dangerous write tool. */
        private boolean allowWrite = false;

        /** Allows DDL statements through the dangerous DDL tool. */
        private boolean allowDdl = false;

        /** Page size used when the caller does not provide one. */
        private int defaultPageSize = 100;

        /** Hard cap for rows returned by MCP query tools. */
        private int maxRows = 1000;
    }
}
