package ai.chat2db.plugin.dlc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import ai.chat2db.plugin.dlc.builder.DlcSqlBuilder;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class DlcMetaData extends DefaultMetaService implements MetaData {
    private static final String DATASOURCE_CONNECTION_NAME_KEY = "datasource_connection_name";
    private static final String DEFAULT_CATALOG_NAME = "DataLakeCatalog";

    @Override
    public List<Database> databases(Connection connection) {
        try {
            return SQLExecutor.getInstance().execute(connection, "SHOW DATABASES", resultSet -> {
                List<Database> databases = new ArrayList<>();
                while (resultSet.next()) {
                    Database database = new Database();
                    String name = resultSet.getString(1);
                    database.setName(name);
                    databases.add(database);
                }
                return databases;
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        // DLC does not support schemas; avoid calling JDBC metadata for schema discovery.
        return Collections.emptyList();
    }

    @Override
    public TableMeta getTableMeta(Connection connection, String databaseName, String schemaName) {
        // DLC JDBC does not support DatabaseMetaData#getTypeInfo, so provide the common type set directly.
        return TableMeta.builder()
                .columnTypes(Arrays.asList(
                        columnType("STRING", false, false),
                        columnType("VARCHAR", true, false),
                        columnType("CHAR", true, false),
                        columnType("BOOLEAN", false, false),
                        columnType("TINYINT", false, false),
                        columnType("SMALLINT", false, false),
                        columnType("INT", false, false),
                        columnType("INTEGER", false, false),
                        columnType("BIGINT", false, false),
                        columnType("FLOAT", false, false),
                        columnType("DOUBLE", false, false),
                        columnType("DECIMAL", true, true),
                        columnType("DATE", false, false),
                        columnType("TIMESTAMP", false, false),
                        columnType("BINARY", false, false),
                        columnType("ARRAY", false, false),
                        columnType("MAP", false, false),
                        columnType("STRUCT", false, false)
                ))
                .build();
    }

    private ColumnType columnType(String typeName, boolean supportLength, boolean supportScale) {
        return new ColumnType(
                typeName,
                supportLength,
                supportScale,
                true,
                false,
                false,
                false,
                true,
                true,
                false,
                true,
                false
        );
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        try {
            String sql = "SHOW TABLES";
            if (StringUtils.isNotBlank(tableName)) {
                sql += " LIKE '" + tableName + "'";
            }

            return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                List<Table> tables = new ArrayList<>();
                ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
                int tableNameIndex = findColumnIndex(resultSetMetaData,
                        "tableName", "tbl_name", "table_name", "tab_name");
                int namespaceIndex = findColumnIndex(resultSetMetaData,
                        "namespace", "database_name", "db_name");
                int commentIndex = findColumnIndex(resultSetMetaData, "comment", "table_comment");
                if (tableNameIndex < 1) {
                    tableNameIndex = 1;
                }
                while (resultSet.next()) {
                    String namespace = namespaceIndex > 0 ? resultSet.getString(namespaceIndex) : null;
                    String name = resultSet.getString(tableNameIndex);
                    if (StringUtils.isBlank(name)) {
                        continue;
                    }
                    Table table = Table.builder()
                            .name(name)
                            .databaseName(StringUtils.isNotBlank(namespace) ? namespace : databaseName)
                            .schemaName(schemaName)
                            .type("TABLE")
                            .build();
                    if (commentIndex > 0) {
                        String comment = resultSet.getString(commentIndex);
                        if (StringUtils.isNotBlank(comment) && !"NULL".equalsIgnoreCase(comment)) {
                            table.setComment(comment);
                        }
                    }
                    tables.add(table);
                }
                return tables;
            });
        } catch (Exception e) {
            log.warn("DLC query tables failed, databaseName:{}, schemaName:{}, tableName:{}",
                    databaseName, schemaName, tableName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        for (String sql : buildShowCreateSqls("TABLE", databaseName, tableName)) {
            log.info("DLC tableDDL execute, databaseName:{}, schemaName:{}, tableName:{}, sql:{}",
                    databaseName, schemaName, tableName, sql);
            try {
                String ddl = executeShowCreateSql(connection, sql, "createtab_stmt");
                if (StringUtils.isNotBlank(ddl)) {
                    log.info("DLC tableDDL success, databaseName:{}, schemaName:{}, tableName:{}, sql:{}, ddlLength:{}",
                            databaseName, schemaName, tableName, sql, ddl.length());
                    log.debug("DLC tableDDL content, databaseName:{}, schemaName:{}, tableName:{}, sql:{}, ddl:{}",
                            databaseName, schemaName, tableName, sql, ddl);
                    return ddl;
                }
            } catch (Exception e) {
                log.warn("DLC tableDDL sql failed, databaseName:{}, schemaName:{}, tableName:{}, sql:{}",
                        databaseName, schemaName, tableName, sql, e);
            }
        }
        log.error("DLC tableDDL failed, all candidate sql returned empty, databaseName:{}, schemaName:{}, tableName:{}",
                databaseName, schemaName, tableName);
        return null;
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return Collections.emptyList();
        }
        try {
            String sql = "DESCRIBE " + tableName;
            return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                List<TableColumn> columns = new ArrayList<>();
                int ordinalPosition = 1;
                for (DlcDescribeRow row : readDescribeRows(resultSet)) {
                    String colName = row.colName();
                    if (isDescribeMetadataSection(colName)) {
                        break;
                    }
                    if (StringUtils.isBlank(colName) || "# col_name".equals(colName)
                            || "---".equals(colName) || "data_type".equals(colName)) {
                        continue;
                    }
                    String dataType = row.dataType();
                    if (StringUtils.isBlank(dataType)) {
                        continue;
                    }
                    String comment = row.comment();

                    TableColumn column = new TableColumn();
                    column.setName(colName);
                    column.setOldName(colName);
                    column.setDataType(dataType);
                    column.setColumnType(dataType);
                    column.setDatabaseName(databaseName);
                    column.setSchemaName(schemaName);
                    column.setTableName(tableName);
                    column.setOrdinalPosition(ordinalPosition++);
                    if (StringUtils.isNotBlank(comment) && !"NULL".equalsIgnoreCase(comment)) {
                        column.setComment(comment);
                    }
                    columns.add(column);
                }
                return columns;
            });
        } catch (Exception e) {
            log.warn("DLC query columns failed, databaseName:{}, schemaName:{}, tableName:{}",
                    databaseName, schemaName, tableName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean supportBatchQueryColumns() {
        return false;
    }

    @Override
    public void enrichTable(Connection connection, String databaseName, String schemaName, Table table) {
        if (table == null || StringUtils.isBlank(table.getName()) || StringUtils.isNotBlank(table.getPartition())) {
            return;
        }
        String partition = queryPartition(connection, table.getName());
        if (StringUtils.isNotBlank(partition)) {
            table.setPartition(partition);
        }
    }

    private boolean isDescribeMetadataSection(String colName) {
        return StringUtils.startsWith(colName, "#")
                && !"# col_name".equals(colName);
    }

    private List<DlcDescribeRow> readDescribeRows(ResultSet resultSet) throws SQLException {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        int colNameIndex = findColumnIndex(resultSetMetaData, "col_name", "column_name");
        int dataTypeIndex = findColumnIndex(resultSetMetaData, "data_type", "column_type", "type");
        int commentIndex = findColumnIndex(resultSetMetaData, "comment", "column_comment");
        List<DlcDescribeRow> rows = new ArrayList<>();

        if (colNameIndex > 0 && dataTypeIndex > 0) {
            while (resultSet.next()) {
                rows.add(new DlcDescribeRow(
                        StringUtils.trimToNull(resultSet.getString(colNameIndex)),
                        StringUtils.trimToNull(resultSet.getString(dataTypeIndex)),
                        commentIndex > 0 ? StringUtils.trimToNull(resultSet.getString(commentIndex)) : null
                ));
            }
            return rows;
        }

        int resultIndex = findColumnIndex(resultSetMetaData, "result");
        if (resultIndex < 1) {
            throw new SQLException("Unsupported DLC DESCRIBE result columns: "
                    + getResultColumnNames(resultSetMetaData));
        }
        while (resultSet.next()) {
            addDescribeTextRows(rows, resultSet.getString(resultIndex));
        }
        return rows;
    }

    private void addDescribeTextRows(List<DlcDescribeRow> rows, String describeText) {
        if (StringUtils.isBlank(describeText)) {
            return;
        }
        String normalizedText = describeText.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalizedText.split("\n")) {
            String trimmedLine = StringUtils.trimToEmpty(line);
            if (StringUtils.isBlank(trimmedLine)) {
                continue;
            }

            String[] values = line.split("\\t", -1);
            if (values.length < 2) {
                values = trimmedLine.split("\\s{2,}", 3);
            }
            if (values.length < 2) {
                if (StringUtils.startsWith(trimmedLine, "#")) {
                    rows.add(new DlcDescribeRow(trimmedLine, null, null));
                }
                continue;
            }

            String colName = StringUtils.trimToNull(values[0]);
            if (StringUtils.isBlank(colName) || colName.matches("-+")) {
                continue;
            }
            String dataType = StringUtils.trimToNull(values[1]);
            String comment = values.length > 2 ? StringUtils.trimToNull(values[2]) : null;
            rows.add(new DlcDescribeRow(colName, dataType, comment));
        }
    }

    private int findColumnIndex(ResultSetMetaData resultSetMetaData, String... candidateNames) throws SQLException {
        if (resultSetMetaData == null) {
            return -1;
        }
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            String columnLabel = resultSetMetaData.getColumnLabel(i);
            String columnName = resultSetMetaData.getColumnName(i);
            for (String candidateName : candidateNames) {
                if (StringUtils.equalsIgnoreCase(candidateName, columnLabel)
                        || StringUtils.equalsIgnoreCase(candidateName, columnName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String getResultColumnNames(ResultSetMetaData resultSetMetaData) throws SQLException {
        if (resultSetMetaData == null) {
            return "[]";
        }
        List<String> columnNames = new ArrayList<>();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            columnNames.add(resultSetMetaData.getColumnLabel(i));
        }
        return columnNames.toString();
    }

    private String queryPartition(Connection connection, String tableName) {
        try {
            String sql = "DESCRIBE " + tableName;
            return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                List<String> partitions = new ArrayList<>();
                boolean partitionSection = false;
                for (DlcDescribeRow row : readDescribeRows(resultSet)) {
                    String colName = row.colName();
                    if (isDescribeMetadataSection(colName)) {
                        partitionSection = "# Partitioning".equalsIgnoreCase(colName);
                        continue;
                    }
                    if (!partitionSection) {
                        continue;
                    }
                    String dataType = row.dataType();
                    if (StringUtils.isNotBlank(dataType)) {
                        partitions.add(dataType);
                    }
                }
                return String.join(", ", partitions);
            });
        } catch (Exception e) {
            log.warn("DLC query partition failed, tableName:{}", tableName, e);
            return null;
        }
    }

    private record DlcDescribeRow(String colName, String dataType, String comment) {
    }

    @Override
    public List<Table> views(Connection connection, String databaseName, String schemaName) {
        try {
            return SQLExecutor.getInstance().execute(connection, "SHOW VIEWS", resultSet -> {
                List<Table> views = new ArrayList<>();
                while (resultSet.next()) {
                    Table view = Table.builder()
                            .name(resultSet.getString(1))
                            .databaseName(databaseName)
                            .schemaName(schemaName)
                            .type("VIEW")
                            .build();
                    views.add(view);
                }
                return views;
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        Table table = new Table();
        table.setName(viewName);
        table.setDatabaseName(databaseName);
        table.setSchemaName(schemaName);
        table.setType("VIEW");
        for (String sql : buildShowCreateSqls("VIEW", databaseName, viewName)) {
            log.info("DLC viewDDL execute, databaseName:{}, schemaName:{}, viewName:{}, sql:{}",
                    databaseName, schemaName, viewName, sql);
            try {
                String ddl = executeShowCreateSql(connection, sql, "createview_stmt");
                if (StringUtils.isNotBlank(ddl)) {
                    table.setDdl(ddl);
                    log.info("DLC viewDDL success, databaseName:{}, schemaName:{}, viewName:{}, sql:{}, ddlLength:{}",
                            databaseName, schemaName, viewName, sql, ddl.length());
                    return table;
                }
            } catch (Exception e) {
                log.warn("DLC viewDDL sql failed, databaseName:{}, schemaName:{}, viewName:{}, sql:{}",
                        databaseName, schemaName, viewName, sql, e);
            }
        }
        log.error("DLC viewDDL failed, all candidate sql returned empty, databaseName:{}, schemaName:{}, viewName:{}",
                databaseName, schemaName, viewName);
        return table;
    }

    private String executeShowCreateSql(Connection connection, String sql, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return getDlcCreateStatement(resultSet, columnName);
            }
            String taskId = getTaskId(statement);
            log.warn("DLC show create returned empty jdbc result, sql:{}, taskId:{}", sql, taskId);
            return queryCreateStatementFromTaskResult(statement, taskId);
        }
    }

    private String getTaskId(Statement statement) {
        try {
            Object taskId = statement.getClass().getMethod("getTaskId").invoke(statement);
            return taskId == null ? null : taskId.toString();
        } catch (Exception e) {
            log.warn("DLC get taskId from statement failed", e);
            return null;
        }
    }

    private String queryCreateStatementFromTaskResult(Statement statement, String taskId) {
        if (StringUtils.isBlank(taskId)) {
            return null;
        }
        try {
            Object client = getDlcClient(statement);
            if (client == null) {
                return null;
            }
            String resultSet = queryResult(client, taskId);
            String ddl = parseCreateStatement(resultSet);
            if (StringUtils.isNotBlank(ddl)) {
                log.info("DLC show create task result success, taskId:{}, ddlLength:{}", taskId, ddl.length());
                return ddl;
            }

            resultSet = describeTaskResult(client, taskId);
            ddl = parseCreateStatement(resultSet);
            if (StringUtils.isNotBlank(ddl)) {
                log.info("DLC show create describe task result success, taskId:{}, ddlLength:{}", taskId, ddl.length());
                return ddl;
            }
            log.warn("DLC show create task result is blank, taskId:{}", taskId);
        } catch (Exception e) {
            log.warn("DLC query show create task result failed, taskId:{}", taskId, e);
        }
        return null;
    }

    private Object getDlcClient(Statement statement) throws Exception {
        Object dlcConnection = statement.getConnection();
        Object dlc = dlcConnection.getClass().getMethod("getDlc").invoke(dlcConnection);
        return dlc == null ? null : dlc.getClass().getMethod("getClient").invoke(dlc);
    }

    private String queryResult(Object client, String taskId) throws Exception {
        ClassLoader classLoader = client.getClass().getClassLoader();
        Class<?> requestClass = classLoader.loadClass("com.tencentcloudapi.dlc.v20210125.models.QueryResultRequest");
        Object request = requestClass.getDeclaredConstructor().newInstance();
        requestClass.getMethod("setTaskId", String.class).invoke(request, taskId);
        Object response = client.getClass().getMethod("QueryResult", requestClass).invoke(client, request);
        Object resultSet = response.getClass().getMethod("getResultSet").invoke(response);
        log.debug("DLC QueryResult raw resultSet, taskId:{}, resultSet:{}", taskId, resultSet);
        return resultSet == null ? null : resultSet.toString();
    }

    private String describeTaskResult(Object client, String taskId) throws Exception {
        ClassLoader classLoader = client.getClass().getClassLoader();
        Class<?> requestClass = classLoader.loadClass("com.tencentcloudapi.dlc.v20210125.models.DescribeTaskResultRequest");
        Object request = requestClass.getDeclaredConstructor().newInstance();
        requestClass.getMethod("setTaskId", String.class).invoke(request, taskId);
        requestClass.getMethod("setMaxResults", Long.class).invoke(request, 10L);
        Object response = client.getClass().getMethod("DescribeTaskResult", requestClass).invoke(client, request);
        Object taskInfo = response.getClass().getMethod("getTaskInfo").invoke(response);
        if (taskInfo == null) {
            return null;
        }
        Object resultSet = taskInfo.getClass().getMethod("getResultSet").invoke(taskInfo);
        log.debug("DLC DescribeTaskResult raw resultSet, taskId:{}, resultSet:{}", taskId, resultSet);
        return resultSet == null ? null : resultSet.toString();
    }

    private String parseCreateStatement(String resultSet) {
        if (StringUtils.isBlank(resultSet)) {
            return null;
        }
        try {
            String ddl = findCreateStatement(JSON.parse(resultSet));
            if (StringUtils.isNotBlank(ddl)) {
                return ddl;
            }
        } catch (Exception e) {
            log.debug("DLC parse task result as json failed, fallback to raw text", e);
        }
        return extractCreateStatement(resultSet);
    }

    private String findCreateStatement(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return extractCreateStatement(stringValue);
        }
        if (value instanceof JSONArray array) {
            for (Object item : array) {
                String ddl = findCreateStatement(item);
                if (StringUtils.isNotBlank(ddl)) {
                    return ddl;
                }
            }
            return null;
        }
        if (value instanceof JSONObject object) {
            String ddl = findCreateStatement(object.get("createtab_stmt"));
            if (StringUtils.isNotBlank(ddl)) {
                return ddl;
            }
            ddl = findCreateStatement(object.get("createview_stmt"));
            if (StringUtils.isNotBlank(ddl)) {
                return ddl;
            }
            return findCreateStatement(object.values());
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                String ddl = findCreateStatement(item);
                if (StringUtils.isNotBlank(ddl)) {
                    return ddl;
                }
            }
        }
        return null;
    }

    private String extractCreateStatement(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        int tableIndex = StringUtils.indexOfIgnoreCase(value, "CREATE TABLE ");
        int viewIndex = StringUtils.indexOfIgnoreCase(value, "CREATE VIEW ");
        int index;
        if (tableIndex < 0) {
            index = viewIndex;
        } else if (viewIndex < 0) {
            index = tableIndex;
        } else {
            index = Math.min(tableIndex, viewIndex);
        }
        if (index < 0) {
            return null;
        }
        return value.substring(index).trim();
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        try {
            return SQLExecutor.getInstance().execute(connection, "SHOW FUNCTIONS", resultSet -> {
                List<Function> functions = new ArrayList<>();
                while (resultSet.next()) {
                    String name = resultSet.getString(1);
                    if (StringUtils.isNotBlank(name)) {
                        Function function = new Function();
                        function.setName(name);
                        function.setDatabaseName(databaseName);
                        function.setSchemaName(schemaName);
                        functions.add(function);
                    }
                }
                return functions;
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Function function(Connection connection, String databaseName, String schemaName, String functionName) {
        return null;
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public List<ForeignKey> foreignKeys(Connection connection, String databaseName, String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new DlcSqlBuilder();
    }

    private String getDlcCreateStatement(ResultSet resultSet, String columnName) throws SQLException {
        try {
            return resultSet.getString(columnName);
        } catch (SQLException e) {
            log.warn("DLC create statement column:{} not found, fallback to first column", columnName, e);
            return resultSet.getString(1);
        }
    }

    private List<String> buildShowCreateSqls(String objectType, String databaseName, String objectName) {
        List<String> sqlList = new ArrayList<>();
        addShowCreateSql(sqlList, objectType, objectName);
        if (StringUtils.isNotBlank(databaseName) && !StringUtils.contains(objectName, ".")) {
            addShowCreateSql(sqlList, objectType, databaseName + "." + objectName);
            addShowCreateSql(sqlList, objectType, getMetaDataName(databaseName, objectName));

            String catalogName = getCatalogName();
            if (StringUtils.isNotBlank(catalogName)) {
                addShowCreateSql(sqlList, objectType, catalogName + "." + databaseName + "." + objectName);
                addShowCreateSql(sqlList, objectType, getMetaDataName(catalogName, databaseName, objectName));
            }
        }
        return sqlList;
    }

    private void addShowCreateSql(List<String> sqlList, String objectType, String objectName) {
        if (StringUtils.isBlank(objectName)) {
            return;
        }
        String sql = "SHOW CREATE " + objectType + " " + objectName;
        if (!sqlList.contains(sql)) {
            sqlList.add(sql);
        }
    }

    private String getCatalogName() {
        try {
            Map<String, Object> extendMap = Chat2DBContext.getConnectInfo().getExtendMap();
            Object catalogName = extendMap.get(DATASOURCE_CONNECTION_NAME_KEY);
            if (catalogName != null && StringUtils.isNotBlank(catalogName.toString())) {
                return catalogName.toString();
            }
            String url = Chat2DBContext.getConnectInfo().getUrl();
            String catalogFromUrl = getQueryParam(url, DATASOURCE_CONNECTION_NAME_KEY);
            if (StringUtils.isNotBlank(catalogFromUrl)) {
                return catalogFromUrl;
            }
        } catch (Exception e) {
            log.warn("DLC get catalog name failed, fallback to default catalog", e);
        }
        return DEFAULT_CATALOG_NAME;
    }

    private String getQueryParam(String url, String key) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String param = key + "=";
        int paramIndex = url.indexOf(param);
        if (paramIndex < 0) {
            return null;
        }
        int valueStart = paramIndex + param.length();
        int valueEnd = url.indexOf("&", valueStart);
        if (valueEnd < 0) {
            return url.substring(valueStart);
        }
        return url.substring(valueStart, valueEnd);
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names)
                .filter(StringUtils::isNotBlank)
                .map(name -> "`" + name + "`")
                .collect(Collectors.joining("."));
    }
}
