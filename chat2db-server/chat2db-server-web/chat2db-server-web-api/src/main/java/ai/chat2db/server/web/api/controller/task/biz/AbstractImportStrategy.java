package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.web.api.controller.task.request.FieldMapping;
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

                    // 验证映射配置
                    validateMappings(fileHeaders, importContext);

                    log.info("import file headers: {}, target columns: {}", fileHeaders, importContext.getHeaderList());
                }

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    try {
                        // 构建源数据：源字段名 → 值
                        Map<String, String> sourceData = new LinkedHashMap<>();
                        for (Map.Entry<Integer, String> entry : data.entrySet()) {
                            if (entry.getKey() < fileHeaders.size()) {
                                sourceData.put(fileHeaders.get(entry.getKey()), entry.getValue());
                            }
                        }

                        // 根据映射转换为目标字段数据
                        Map<String, String> rowData = convertToTargetData(sourceData, importContext);

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

    /**
     * 验证映射配置
     */
    private void validateMappings(List<String> fileHeaders, ImportContext importContext) {
        List<FieldMapping> mappings = importContext.getFieldMappings();
        if (mappings == null || mappings.isEmpty()) {
            // 没有映射配置，使用原有逻辑：验证文件列是否都在目标表中
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
            return;
        }

        // 有映射配置时，验证所有映射的目标字段是否都存在于目标表中
        Map<String, Integer> columnOrderMap = importContext.getColumnOrderMap();
        List<String> invalidTargets = new ArrayList<>();
        for (FieldMapping mapping : mappings) {
            if (!columnOrderMap.containsKey(mapping.getTargetField())) {
                invalidTargets.add(mapping.getTargetField());
            }
        }
        if (!invalidTargets.isEmpty()) {
            String invalidStr = invalidTargets.stream().collect(Collectors.joining(", "));
            throw new BusinessException("dataSource.importInvalidTargetField",
                    new Object[]{invalidStr});
        }
    }

    /**
     * 将源数据转换为目标字段数据
     */
    private Map<String, String> convertToTargetData(Map<String, String> sourceData, ImportContext importContext) {
        Map<String, String> rowData = new LinkedHashMap<>();
        List<FieldMapping> mappings = importContext.getFieldMappings();

        if (mappings == null || mappings.isEmpty()) {
            // 没有映射配置，使用同名字段匹配
            for (String columnName : importContext.getHeaderList()) {
                rowData.put(columnName, sourceData.getOrDefault(columnName, null));
            }
            return rowData;
        }

        // 构建反向映射：目标字段 → 源字段
        Map<String, String> targetToSourceMap = new LinkedHashMap<>();
        for (FieldMapping mapping : mappings) {
            targetToSourceMap.put(mapping.getTargetField(), mapping.getSourceField());
        }

        // 根据映射获取数据
        for (String targetColumn : importContext.getHeaderList()) {
            String sourceField = targetToSourceMap.get(targetColumn);
            if (sourceField != null) {
                rowData.put(targetColumn, sourceData.getOrDefault(sourceField, null));
            } else {
                rowData.put(targetColumn, null);
            }
        }

        return rowData;
    }

    /**
     * 读取文件表头
     */
    public List<String> readFileHeaders(File file) {
        List<String> headers = new ArrayList<>();
        EasyExcel.read(file, new ReadListener<Map<Integer, String>>() {
            @Override
            public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
                for (Map.Entry<Integer, ReadCellData<?>> entry : headMap.entrySet()) {
                    headers.add(entry.getValue().getStringValue().trim());
                }
            }

            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                // 只读取表头，不处理数据
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 完成后不做处理
            }
        }).sheet().doRead();
        return headers;
    }

    protected PreparedStatement prepareInsertStatement(ImportContext importContext) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(importContext.getTableName()).append(" (");

        // 使用目标字段列表构建 INSERT 语句
        List<String> targetColumns = importContext.getHeaderList();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append(targetColumns.get(i));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append("?");
        }
        sql.append(")");
        return importContext.getConnection().prepareStatement(sql.toString());
    }
}
