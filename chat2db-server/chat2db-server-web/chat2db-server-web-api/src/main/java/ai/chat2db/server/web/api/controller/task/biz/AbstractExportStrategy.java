package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.spi.jdbc.DefaultValueHandler;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractExportStrategy implements ExportStrategy {

    @Override
    public void exportData(ExportContext exportContext) {
        if (exportContext.getCommandResults() != null) {
            doExportResults(exportContext.getCommandResults(), exportContext);
            return;
        }
        if (exportContext.getCachedResult() != null) {
            doExportResults(Collections.singletonList(exportContext.getCachedResult()), exportContext);
            return;
        }
        DefaultValueHandler valueHandler = new DefaultValueHandler();
        Connection connection = Chat2DBContext.getConnection();

        try (PreparedStatement ps = createStreamStatement(connection, exportContext.getSql());
             ResultSet rs = ps.executeQuery()) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<String> headerNames = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                headerNames.add(metaData.getColumnLabel(i));
            }

            doExport(rs, metaData, headerNames, columnCount, valueHandler, exportContext);

        } catch (SQLException e) {
            log.error("export data streaming error, strategy: {}", this.getClass().getSimpleName(), e);
            throw new BusinessException("dataSource.exportError", new Object[]{e.getMessage()}, e);
        }
    }

    protected PreparedStatement createStreamStatement(Connection connection, String sql) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        ps.setFetchSize(Integer.MIN_VALUE);
        return ps;
    }

    protected abstract void doExport(ResultSet rs, ResultSetMetaData metaData, List<String> headerNames,
                                     int columnCount, DefaultValueHandler valueHandler,
                                     ExportContext exportContext) throws SQLException;

    protected abstract void doExportResults(Iterable<ExecuteResult> results, ExportContext exportContext);

    protected List<List<String>> rowsForHeaders(ExecuteResult result, List<String> headerNames) {
        if (result.getDataList() == null || result.getDataList().isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> sourceIndexes = new HashMap<>();
        if (result.getHeaderList() != null) {
            for (int i = 0; i < result.getHeaderList().size(); i++) {
                sourceIndexes.put(result.getHeaderList().get(i).getName(), i);
            }
        }
        List<List<String>> rows = new ArrayList<>(result.getDataList().size());
        for (List<String> sourceRow : result.getDataList()) {
            List<String> row = new ArrayList<>(headerNames.size());
            for (String headerName : headerNames) {
                Integer sourceIndex = sourceIndexes.get(headerName);
                row.add(sourceIndex != null && sourceIndex < sourceRow.size() ? sourceRow.get(sourceIndex) : null);
            }
            rows.add(row);
        }
        return rows;
    }

    protected void updateProgress(int processedCount, ExportContext exportContext) {
        exportContext.getProgressUpdater().accept(processedCount);
    }
}
