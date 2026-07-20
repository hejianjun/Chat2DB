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

    /** Shared relational and MongoDB execution policy exposed to MCP tools. */
    private Sql sql = new Sql();

    /** MongoDB execution policy exposed to MCP tools. */
    private Mongo mongodb = new Mongo();

    /** Redis read and write payload limits. */
    private Redis redis = new Redis();

    /**
     * Shared MCP safety policy. Write covers SQL DML and MongoDB document writes; DDL covers SQL DDL and
     * MongoDB collection/index administration.
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

    /** MongoDB-specific execution limits. Permissions are inherited from the shared SQL policy above. */
    @Data
    public static class Mongo {
        /** Maximum number of documents accepted by one INSERT_MANY prepare request. */
        private int maxBatchDocuments = 100;
    }

    @Data
    public static class Redis {
        private int defaultKeyLimit = 100;
        private int maxKeyLimit = 1000;
        private int defaultValuePageSize = 50;
        private int maxValuePageSize = 200;
        private int maxStringPageBytes = 65536;
        private int maxFullWriteElements = 10000;
        private int maxWriteBytes = 1048576;
    }
}
