package ai.chat2db.server.mcp.support;

import java.util.List;
import java.util.stream.Collectors;

import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ResultFormatter {

    private static final int MAX_CELL_LENGTH = 200;

    public String format(List<ExecuteResult> results) {
        if (results == null || results.isEmpty()) {
            return "No result.";
        }
        return results.stream().map(this::format).collect(Collectors.joining("\n\n"));
    }

    public String format(ExecuteResult result) {
        if (result == null) {
            return "No result.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("success: ").append(result.getSuccess()).append('\n');
        if (StringUtils.isNotBlank(result.getMessage())) {
            builder.append("message: ").append(result.getMessage()).append('\n');
        }
        if (result.getUpdateCount() != null) {
            builder.append("updateCount: ").append(result.getUpdateCount()).append('\n');
        }
        if (result.getHeaderList() == null || result.getHeaderList().isEmpty()) {
            return builder.toString();
        }
        builder.append('\n');
        List<String> headers = result.getHeaderList().stream().map(Header::getName).collect(Collectors.toList());
        builder.append('|').append(String.join("|", headers)).append('|').append('\n');
        builder.append('|').append(headers.stream().map(header -> "---").collect(Collectors.joining("|"))).append('|').append('\n');
        if (result.getDataList() != null) {
            for (List<String> row : result.getDataList()) {
                builder.append('|').append(row.stream().map(this::cell).collect(Collectors.joining("|"))).append('|').append('\n');
            }
        }
        if (Boolean.TRUE.equals(result.getHasNextPage())) {
            builder.append("\nResult truncated. Request the next page to continue.\n");
        }
        return builder.toString();
    }

    private String cell(String value) {
        String normalized = StringUtils.defaultString(value).replace("|", "\\|").replace("\n", " ");
        return StringUtils.abbreviate(normalized, MAX_CELL_LENGTH);
    }
}
