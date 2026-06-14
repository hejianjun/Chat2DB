package ai.chat2db.server.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "chat2db.mcp")
public class McpProperties {

    private boolean enabled = true;

    private String token;

    private Sql sql = new Sql();

    @Data
    public static class Sql {
        private boolean readOnly = true;
        private boolean allowWrite = false;
        private boolean allowDdl = false;
        private boolean requireConfirmParam = true;
        private int defaultPageSize = 100;
        private int maxRows = 1000;
    }
}
