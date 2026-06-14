package ai.chat2db.server.mcp.tool;

import com.alibaba.druid.sql.SQLUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class SqlUtilTools {

    @Tool(description = "Format SQL text. This is a local utility and does not access any datasource.")
    public String formatSql(@ToolParam(description = "SQL text") String sql,
        @ToolParam(description = "Database type such as mysql, postgresql, oracle. Optional", required = false) String dbType) {
        return SQLUtils.format(sql, dbType == null || dbType.isBlank() ? "mysql" : dbType);
    }

    @Tool(description = "Parse SQL roughly and return the first SQL keyword/type. This is a local utility and does not access any datasource.")
    public String parseSql(@ToolParam(description = "SQL text") String sql) {
        String trimmed = sql == null ? "" : sql.stripLeading();
        int index = trimmed.indexOf(' ');
        return (index < 0 ? trimmed : trimmed.substring(0, index)).toLowerCase();
    }
}
