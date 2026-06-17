package ai.chat2db.server.mcp.support;

import java.util.List;
import java.util.stream.Collectors;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.SimpleTable;
import ai.chat2db.spi.model.Table;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Formats SPI execution results into compact Markdown text for MCP clients and LLM consumption.
 */
@Component
public class ResultFormatter {

    /** Keeps individual cells short enough for chat-oriented transports. */
    private static final int MAX_CELL_LENGTH = 200;

    public String format(List<ExecuteResult> results) {
        if (results == null || results.isEmpty()) {
            return "No result.";
        }
        return results.stream().map(this::format).collect(Collectors.joining("\n\n"));
    }

    public String formatCsv(List<ExecuteResult> results) {
        if (results == null || results.isEmpty()) {
            return "No result.";
        }
        if (results.size() > 1) {
            return results.stream().map(this::formatCsv).collect(Collectors.joining("\n\n"));
        }
        return formatCsv(results.get(0));
    }

    public String formatCsv(ExecuteResult result) {
        if (result == null || !Boolean.TRUE.equals(result.getSuccess())
            || result.getHeaderList() == null || result.getHeaderList().isEmpty()) {
            return format(result);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(result.getHeaderList().stream().map(Header::getName).map(this::csvCell)
            .collect(Collectors.joining(","))).append('\n');
        if (result.getDataList() != null) {
            for (List<String> row : result.getDataList()) {
                builder.append(row.stream().map(this::csvCell).collect(Collectors.joining(","))).append('\n');
            }
        }
        return builder.toString();
    }

    public String formatDatasourcesCsv(List<DataSource> dataSources) {
        StringBuilder builder = new StringBuilder("id,alias,type,host,port,url,environmentShortName\n");
        if (dataSources == null || dataSources.isEmpty()) {
            return builder.toString();
        }
        for (DataSource dataSource : dataSources) {
            builder.append(csvCell(dataSource.getId() == null ? null : dataSource.getId().toString())).append(',')
                .append(csvCell(dataSource.getAlias())).append(',')
                .append(csvCell(dataSource.getType())).append(',')
                .append(csvCell(dataSource.getHost())).append(',')
                .append(csvCell(dataSource.getPort())).append(',')
                .append(csvCell(dataSource.getUrl())).append(',')
                .append(csvCell(dataSource.getEnvironment() == null ? null : dataSource.getEnvironment().getShortName()))
                .append('\n');
        }
        return builder.toString();
    }

    public String formatDatabasesCsv(List<Database> databases) {
        StringBuilder builder = new StringBuilder("name,comment,charset,collation,owner,count\n");
        if (databases == null || databases.isEmpty()) {
            return builder.toString();
        }
        for (Database database : databases) {
            builder.append(csvCell(database.getName())).append(',')
                .append(csvCell(database.getComment())).append(',')
                .append(csvCell(database.getCharset())).append(',')
                .append(csvCell(database.getCollation())).append(',')
                .append(csvCell(database.getOwner())).append(',')
                .append(csvCell(database.getCount() == null ? null : database.getCount().toString()))
                .append('\n');
        }
        return builder.toString();
    }

    public String formatSchemasCsv(List<Schema> schemas) {
        StringBuilder builder = new StringBuilder("databaseName,name,comment,owner,treeNodeType,keyType\n");
        if (schemas == null || schemas.isEmpty()) {
            return builder.toString();
        }
        for (Schema schema : schemas) {
            builder.append(csvCell(schema.getDatabaseName())).append(',')
                .append(csvCell(schema.getName())).append(',')
                .append(csvCell(schema.getComment())).append(',')
                .append(csvCell(schema.getOwner())).append(',')
                .append(csvCell(schema.getTreeNodeType())).append(',')
                .append(csvCell(schema.getKeyType()))
                .append('\n');
        }
        return builder.toString();
    }

    public String formatSimpleTablesCsv(List<SimpleTable> tables) {
        StringBuilder builder = new StringBuilder("name,comment\n");
        if (tables == null || tables.isEmpty()) {
            return builder.toString();
        }
        for (SimpleTable table : tables) {
            builder.append(csvCell(table.getName())).append(',')
                .append(csvCell(table.getComment()))
                .append('\n');
        }
        return builder.toString();
    }

    public String formatTablesCsv(List<Table> tables) {
        StringBuilder builder = new StringBuilder(
            "name,comment,databaseName,schemaName,dbType,type,pinned,deprecated,engine,charset,collate,rowCount,aiComment\n");
        if (tables == null || tables.isEmpty()) {
            return builder.toString();
        }
        for (Table table : tables) {
            builder.append(csvCell(table.getName())).append(',')
                .append(csvCell(table.getComment())).append(',')
                .append(csvCell(table.getDatabaseName())).append(',')
                .append(csvCell(table.getSchemaName())).append(',')
                .append(csvCell(table.getDbType())).append(',')
                .append(csvCell(table.getType())).append(',')
                .append(csvCell(Boolean.toString(table.isPinned()))).append(',')
                .append(csvCell(Boolean.toString(table.isDeprecated()))).append(',')
                .append(csvCell(table.getEngine())).append(',')
                .append(csvCell(table.getCharset())).append(',')
                .append(csvCell(table.getCollate())).append(',')
                .append(csvCell(table.getRowCount() == null ? null : table.getRowCount().toString())).append(',')
                .append(csvCell(table.getAiComment()))
                .append('\n');
        }
        return builder.toString();
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
        // Markdown tables are easy for MCP clients and LLMs to render or summarize.
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
        // Escape table delimiters and flatten line breaks before applying the cell-length cap.
        String normalized = StringUtils.defaultString(value).replace("|", "\\|").replace("\n", " ");
        return StringUtils.abbreviate(normalized, MAX_CELL_LENGTH);
    }

    private String csvCell(String value) {
        String normalized = StringUtils.defaultString(value);
        if (!StringUtils.containsAny(normalized, ',', '"', '\n', '\r')) {
            return normalized;
        }
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }
}
