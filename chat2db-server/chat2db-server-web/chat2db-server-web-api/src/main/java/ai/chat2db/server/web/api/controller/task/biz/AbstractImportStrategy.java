package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.listener.ReadListener;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractImportStrategy implements ImportStrategy {

    @Override
    public void importData(File file, ImportContext importContext) {
        final AtomicInteger processedCount = new AtomicInteger(0);
        final List<String> fileHeaders = new ArrayList<>();
        try (PreparedStatement ps = prepareInsertStatement(importContext)) {

            EasyExcel.read(file, new ReadListener<Map<Integer, String>>() {

                @Override
                public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
                    for (Map.Entry<Integer, ReadCellData<?>> entry : headMap.entrySet()) {
                        fileHeaders.add(entry.getValue().getStringValue().trim());
                    }

                    Map<String, Integer> columnOrderMap = importContext.getColumnOrderMap();
                    List<String> missingColumns = new ArrayList<>();

                    for (String fileHeader : fileHeaders) {
                        if (!columnOrderMap.containsKey(fileHeader)) {
                            missingColumns.add(fileHeader);
                        }
                    }

                    if (!missingColumns.isEmpty()) {
                        String missingColsStr = missingColumns.stream().collect(Collectors.joining(", "));
                        throw new BusinessException("dataSource.importColumnNotFound",
                                new Object[]{missingColsStr});
                    }

                    log.info("import file headers: {}, matched table columns: {}", fileHeaders, importContext.getHeaderList());
                }

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    try {
                        Map<String, String> rowData = new LinkedHashMap<>();
                        for (Map.Entry<Integer, String> entry : data.entrySet()) {
                            if (entry.getKey() < fileHeaders.size()) {
                                rowData.put(fileHeaders.get(entry.getKey()), entry.getValue());
                            }
                        }

                        int paramIndex = 1;
                        for (String columnName : importContext.getHeaderList()) {
                            ps.setObject(paramIndex++, rowData.getOrDefault(columnName, null));
                        }
                        ps.addBatch();

                        int count = processedCount.incrementAndGet();
                        if (count % 200 == 0) {
                            ps.executeBatch();
                            ps.clearBatch();
                            importContext.getProgressUpdater().accept(count);
                        }
                    } catch (SQLException e) {
                        log.error("import data batch error at row {}, data: {}", processedCount.get() + 1, data, e);
                        throw new BusinessException("dataSource.importRowError",
                                new Object[]{processedCount.get() + 1, e.getMessage()}, e);
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    try {
                        ps.executeBatch();
                        ps.clearBatch();
                        importContext.getProgressUpdater().accept(processedCount.get());
                    } catch (SQLException e) {
                        log.error("import data final batch error", e);
                        throw new BusinessException("dataSource.importError", new Object[]{e.getMessage()}, e);
                    }
                }
            }).sheet().doRead();
        } catch (SQLException e) {
            log.error("import data error, strategy: {}", this.getClass().getSimpleName(), e);
            throw new BusinessException("dataSource.importError", new Object[]{e.getMessage()}, e);
        }
    }

    protected PreparedStatement prepareInsertStatement(ImportContext importContext) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(importContext.getTableName()).append(" VALUES (");
        for (int i = 0; i < importContext.getColumnCount(); i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append("?");
        }
        sql.append(")");
        return importContext.getConnection().prepareStatement(sql.toString());
    }
}
