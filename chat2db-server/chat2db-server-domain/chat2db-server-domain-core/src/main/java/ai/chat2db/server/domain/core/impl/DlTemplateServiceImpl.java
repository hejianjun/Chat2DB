package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.*;
import ai.chat2db.server.domain.api.param.operation.OperationLogCreateParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.domain.api.service.OperationLogService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.core.cache.QueryResultCache;
import ai.chat2db.server.domain.core.converter.CommandConverter;
import ai.chat2db.server.domain.core.util.MetaNameUtils;
import ai.chat2db.server.tools.base.enums.DataSourceTypeEnum;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueHandler;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.enums.SqlTypeEnum;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.mongodb.MqlSyntaxParser;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.SqlUtils;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.PagerUtils;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Slf4j
@Service
public class DlTemplateServiceImpl implements DlTemplateService {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private TableService tableService;

    @Autowired
    private CommandConverter commandConverter;

    @Autowired
    private QueryResultCache queryResultCache;

    @Override
    public List<ExecuteResult> execute(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        Command command = commandConverter.param2model(param);
        List<ExecuteResult> results = executor.execute(command);
        for (ExecuteResult executeResult : results) {
            ensureRowNumberColumn(executeResult, I18nUtils.getMessage("sqlResult.rowNumber"));
            List<Header> headers = executeResult.getHeaderList();
            if (executeResult.getSuccess() && executeResult.isCanEdit() && CollectionUtils.isNotEmpty(headers)) {
                headers = setColumnInfo(headers, executeResult.getTableName(), param.getSchemaName(),
                        param.getDatabaseName());
                executeResult.setHeaderList(headers);
            }
            Long operationLogId = addOperationLog(executeResult);
            executeResult.setOperationLogId(operationLogId);
            cacheQueryResult(executeResult, operationLogId);
        }
        return results;
    }

    @Override
    public ExecuteResult executeUpdate(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        ExecuteResult result = new ExecuteResult();
        result.setSuccess(true);
        DbType dbType =
                JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        List<String> sqlList = SqlUtils.parse(param.getSql(), dbType);
        Connection connection = Chat2DBContext.getConnection();
        try {
            connection.setAutoCommit(false);
            for (String originalSql : sqlList) {
                ExecuteResult executeResult = executor.executeUpdate(originalSql, connection, 1);
                result = executeResult;
                addOperationLog(executeResult);
            }
            connection.commit();
        } catch (Exception e) {
            log.error("executeUpdate error", e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    @Override
    public List<ExecuteResult> executeBatchUpdate(DlBatchUpdateParam param) {
        validateBatchUpdateParam(param);
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        Connection connection = Chat2DBContext.getConnection();
        List<ExecuteResult> results = new ArrayList<>();
        boolean originalAutoCommit = true;
        boolean rollback = false;
        String rollbackMessage = null;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            for (int i = 0; i < param.getSqlList().size(); i++) {
                String sql = param.getSqlList().get(i);
                Integer expectedUpdateCount = param.getExpectedUpdateCounts().get(i);
                ExecuteResult executeResult = executor.executeUpdate(sql, connection, expectedUpdateCount);
                executeResult.setStatementIndex(i + 1);
                executeResult.setOriginalSql(sql);
                results.add(executeResult);
                if (!Boolean.TRUE.equals(executeResult.getSuccess())) {
                    rollback = true;
                    rollbackMessage = buildRollbackMessage(sql, expectedUpdateCount, executeResult);
                    executeResult.setMessage(rollbackMessage);
                    break;
                }
            }
            if (rollback) {
                connection.rollback();
            } else {
                connection.commit();
            }
        } catch (Exception e) {
            log.error("executeBatchUpdate error", e);
            rollback = true;
            rollbackMessage = "Batch update failed and rolled back: " + e.getMessage();
            rollbackQuietly(connection);
            if (results.isEmpty() || Boolean.TRUE.equals(results.get(results.size() - 1).getSuccess())) {
                ExecuteResult executeResult = ExecuteResult.builder()
                        .success(Boolean.FALSE)
                        .message(rollbackMessage)
                        .sql(results.size() < param.getSqlList().size() ? param.getSqlList().get(results.size()) : null)
                        .statementIndex(results.size() + 1)
                        .build();
                results.add(executeResult);
            } else {
                results.get(results.size() - 1).setMessage(rollbackMessage);
            }
        } finally {
            restoreAutoCommit(connection, originalAutoCommit);
        }
        if (rollback) {
            markRolledBackAndLog(results, rollbackMessage);
        } else {
            results.forEach(this::addOperationLog);
        }
        return results;
    }

    static void ensureRowNumberColumn(ExecuteResult executeResult, String rowNumberName) {
        if (executeResult == null || !Boolean.TRUE.equals(executeResult.getSuccess())
                || !SqlTypeEnum.SELECT.getCode().equals(executeResult.getSqlType())
                || CollectionUtils.isEmpty(executeResult.getHeaderList())) {
            return;
        }
        boolean hasRowNumberColumn = executeResult.getHeaderList().stream()
                .anyMatch(header -> DataTypeEnum.CHAT2DB_ROW_NUMBER.getCode().equals(header.getDataType()));
        if (hasRowNumberColumn) {
            return;
        }

        List<Header> headers = new ArrayList<>(executeResult.getHeaderList().size() + 1);
        headers.add(Header.builder()
                .name(rowNumberName)
                .dataType(DataTypeEnum.CHAT2DB_ROW_NUMBER.getCode())
                .build());
        headers.addAll(executeResult.getHeaderList());
        executeResult.setHeaderList(headers);

        if (CollectionUtils.isEmpty(executeResult.getDataList())) {
            return;
        }
        int pageNo = executeResult.getPageNo() == null ? 1 : Math.max(executeResult.getPageNo(), 1);
        int pageSize = executeResult.getPageSize() == null
                ? executeResult.getDataList().size()
                : Math.max(executeResult.getPageSize(), 0);
        int firstRowNumber = 1 + (pageNo - 1) * pageSize;
        List<List<String>> rows = new ArrayList<>(executeResult.getDataList().size());
        for (int i = 0; i < executeResult.getDataList().size(); i++) {
            List<String> row = executeResult.getDataList().get(i);
            List<String> rowWithNumber = new ArrayList<>(row.size() + 1);
            rowWithNumber.add(Integer.toString(firstRowNumber + i));
            rowWithNumber.addAll(row);
            rows.add(rowWithNumber);
        }
        executeResult.setDataList(rows);
    }




    @Override
    public Long count(DlCountParam param) {
        if (StringUtils.isBlank(param.getSql())) {
            return 0L;
        }
        if (DataSourceTypeEnum.MONGODB.getCode().equalsIgnoreCase(Chat2DBContext.getConnectInfo().getDbType())) {
            return countMongoDocuments(param.getSql());
        }
        DbType dbType =
                JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        String sql = param.getSql();
        // 解析sql分页
        SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
        if (!(sqlStatement instanceof SQLSelectStatement)) {
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        sql = PagerUtils.count(sql, dbType);
        ExecuteResult executeResult;
        try {
            ValueHandler valueHandler = Chat2DBContext.getMetaData().getValueHandler();
            executeResult = Chat2DBContext.getMetaData().getCommandExecutor().execute(sql, Chat2DBContext.getConnection(), true, null, null,
                    valueHandler);
        } catch (SQLException e) {
            log.warn("执行sql:{}异常", sql, e);
            executeResult = ExecuteResult.builder()
                    .sql(sql)
                    .success(Boolean.FALSE)
                    .message(e.getMessage())
                    .build();
        }

        return extractCount(executeResult);
    }

    private Long countMongoDocuments(String mql) {
        String countMql;
        try {
            countMql = buildMongoCountMql(mql);
        } catch (IllegalArgumentException e) {
            log.warn("Unable to build MongoDB count query from MQL: {}", mql, e);
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        log.info("Generated MongoDB count MQL: sourceMql={}, countMql={}", mql, countMql);

        ExecuteResult executeResult;
        try {
            executeResult = Chat2DBContext.getMetaData().getCommandExecutor().execute(countMql, null, true,
                    null, null, Chat2DBContext.getMetaData().getValueHandler());
        } catch (SQLException e) {
            log.warn("Failed to execute MongoDB count query: {}", countMql, e);
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        if (!Boolean.TRUE.equals(executeResult.getSuccess())) {
            log.warn("MongoDB count query failed: {}, message: {}", countMql, executeResult.getMessage());
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        return extractCount(executeResult);
    }

    String buildMongoCountMql(String mql) {
        List<MqlSyntaxParser.StatementPosition> statements = MqlSyntaxParser.splitStatements(mql);
        if (statements.size() != 1) {
            throw new IllegalArgumentException("MongoDB count requires exactly one statement");
        }

        String statement = statements.get(0).statement().trim();
        if (!statement.startsWith("db.")) {
            throw new IllegalArgumentException("MQL must start with db.");
        }

        String expression = statement.substring(3).trim();
        String collectionReference;
        List<MqlSyntaxParser.MethodCall> calls;
        if (expression.startsWith("getCollection(")) {
            calls = new ArrayList<>(MqlSyntaxParser.parseMethodCalls(expression));
            if (calls.size() < 2 || !"getCollection".equals(calls.get(0).name())
                    || calls.get(0).arguments().size() != 1) {
                throw new IllegalArgumentException("Invalid db.getCollection() query");
            }
            collectionReference = "db.getCollection(" + calls.remove(0).arguments().get(0) + ")";
        } else {
            int dot = expression.indexOf('.');
            if (dot <= 0) {
                throw new IllegalArgumentException("MQL must include a collection and method");
            }
            collectionReference = "db." + expression.substring(0, dot).trim();
            calls = MqlSyntaxParser.parseMethodCalls(expression.substring(dot + 1));
        }

        if (calls.isEmpty() || !("find".equals(calls.get(0).name())
                || "countDocuments".equals(calls.get(0).name()))) {
            throw new IllegalArgumentException("MongoDB total count supports find() queries only");
        }
        List<String> arguments = calls.get(0).arguments();
        String filter = arguments.isEmpty() || StringUtils.isBlank(arguments.get(0)) ? "{}" : arguments.get(0);
        return collectionReference + ".countDocuments(" + filter + ")";
    }

    private Long extractCount(ExecuteResult executeResult) {
        List<List<String>> dataList = executeResult.getDataList();
        if (CollectionUtils.isEmpty(dataList)) {
            return 0L;
        }
        String count = EasyCollectionUtils.stream(executeResult.getDataList())
                .findFirst()
                .orElse(Collections.emptyList())
                .stream()
                .findFirst()
                .orElse("0");
        return Long.valueOf(count);
    }

    @Override
    public String updateSelectResult(UpdateSelectResultParam param) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        String sql = sqlBuilder.generateSqlBasedOnResults(param.getTableName(), param.getHeaderList(),
                param.getOperations());
        return sql;
    }

    @Override
    public String getOrderBySql(OrderByParam param) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        String orderSql = sqlBuilder.buildOrderBySql(param.getOriginSql(), param.getOrderByList());
        return orderSql;
    }


    private List<Header> setColumnInfo(List<Header> headers, String tableName, String schemaName, String databaseName) {
        try {
            TableQueryParam tableQueryParam = new TableQueryParam();
            tableQueryParam.setTableName(MetaNameUtils.getMetaName(tableName));
            tableQueryParam.setSchemaName(schemaName);
            tableQueryParam.setDatabaseName(databaseName);
            tableQueryParam.setRefresh(true);
            tableQueryParam.setDataSourceId(Chat2DBContext.getConnectInfo().getDataSourceId());
            List<TableColumn> columns = tableService.queryColumns(tableQueryParam);
            if (CollectionUtils.isEmpty(columns)) {
                return headers;
            }
            Map<String, TableColumn> columnMap = columns.stream().collect(
                    Collectors.toMap(TableColumn::getName, tableColumn -> tableColumn));
            List<TableIndex> tableIndices = tableService.queryIndexes(tableQueryParam);
            if (!CollectionUtils.isEmpty(tableIndices)) {
                for (TableIndex tableIndex : tableIndices) {
                    if ("PRIMARY".equalsIgnoreCase(tableIndex.getType())) {
                        List<TableIndexColumn> columnList = tableIndex.getColumnList();
                        if (!CollectionUtils.isEmpty(columnList)) {
                            for (TableIndexColumn tableIndexColumn : columnList) {
                                TableColumn tableColumn = columnMap.get(tableIndexColumn.getColumnName());
                                if (tableColumn != null) {
                                    tableColumn.setPrimaryKey(true);
                                }
                            }
                        }
                    }
                }
            }
            for (Header header : headers) {
                TableColumn tableColumn = columnMap.get(header.getName());
                if (tableColumn != null) {
                    header.setPrimaryKey(tableColumn.getPrimaryKey());
                    header.setComment(tableColumn.getComment());
                    header.setDefaultValue(tableColumn.getDefaultValue());
                    header.setNullable(tableColumn.getNullable());
                    header.setColumnSize(tableColumn.getColumnSize());
                    header.setDecimalDigits(tableColumn.getDecimalDigits());
                }
            }

        } catch (Exception e) {
            log.error("setColumnInfo error:", e);
        }
        return headers;
    }


    private void validateBatchUpdateParam(DlBatchUpdateParam param) {
        if (param == null || CollectionUtils.isEmpty(param.getSqlList())
                || CollectionUtils.isEmpty(param.getExpectedUpdateCounts())) {
            throw new IllegalArgumentException("sqlList and expectedUpdateCounts must not be empty.");
        }
        if (param.getSqlList().size() != param.getExpectedUpdateCounts().size()) {
            throw new IllegalArgumentException("sqlList and expectedUpdateCounts must have the same size.");
        }
        for (int i = 0; i < param.getSqlList().size(); i++) {
            if (StringUtils.isBlank(param.getSqlList().get(i))) {
                throw new IllegalArgumentException("sqlList[" + i + "] must not be blank.");
            }
            if (param.getExpectedUpdateCounts().get(i) == null) {
                throw new IllegalArgumentException("expectedUpdateCounts[" + i + "] must not be null.");
            }
        }
    }

    private String buildRollbackMessage(String sql, Integer expectedUpdateCount, ExecuteResult executeResult) {
        return "Batch update rolled back. SQL: " + sql + ", expectedUpdateCount: " + expectedUpdateCount
                + ", actualUpdateCount: " + executeResult.getUpdateCount() + ". " + executeResult.getMessage();
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (Exception rollbackException) {
            log.error("executeBatchUpdate rollback error", rollbackException);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (Exception e) {
            log.error("executeBatchUpdate restore autoCommit error", e);
        }
    }

    private void markRolledBackAndLog(List<ExecuteResult> results, String rollbackMessage) {
        for (ExecuteResult executeResult : results) {
            executeResult.setSuccess(Boolean.FALSE);
            if (StringUtils.isBlank(executeResult.getMessage())) {
                executeResult.setMessage("Rolled back by MCP batch update: " + rollbackMessage);
            }
            addOperationLog(executeResult, "fail", "rolled back by MCP batch update");
        }
    }

    private Long addOperationLog(ExecuteResult executeResult) {
        return addOperationLog(executeResult, null, null);
    }

    private Long addOperationLog(ExecuteResult executeResult, String status, String extendInfo) {
        if (executeResult == null) {
            return null;
        }
        try {
            ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
            if (DataSourceTypeEnum.REDIS.getCode().equals(connectInfo.getDbType())) {
                return null;
            }
            OperationLogCreateParam createParam = new OperationLogCreateParam();
            createParam.setDdl(executeResult.getSql());
            createParam.setStatus(StringUtils.defaultIfBlank(status, executeResult.getSuccess() ? "success" : "fail"));
            createParam.setDatabaseName(connectInfo.getDatabaseName());
            createParam.setDataSourceId(connectInfo.getDataSourceId());
            createParam.setSchemaName(connectInfo.getSchemaName());
            createParam.setUseTime(executeResult.getDuration());
            createParam.setType(connectInfo.getDbType());
            createParam.setOperationRows(
                    executeResult.getUpdateCount() != null ? Long.valueOf(executeResult.getUpdateCount()) : null);
            createParam.setExtendInfo(extendInfo);
            return operationLogService.create(createParam);
        } catch (Exception e) {
            log.error("addOperationLog error:", e);
            return null;
        }
    }

    private void cacheQueryResult(ExecuteResult executeResult, Long operationLogId) {
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
        String dbType = connectInfo.getDbType();
        boolean supported = "HIVE".equalsIgnoreCase(dbType) || "DLC".equalsIgnoreCase(dbType);
        if (!supported || operationLogId == null || !Boolean.TRUE.equals(executeResult.getSuccess())
                || !SqlTypeEnum.SELECT.getCode().equalsIgnoreCase(executeResult.getSqlType())) {
            return;
        }
        queryResultCache.put(operationLogId, ContextUtils.getUserId(), connectInfo.getDataSourceId(),
                executeResult.getSql(), executeResult);
    }
}
