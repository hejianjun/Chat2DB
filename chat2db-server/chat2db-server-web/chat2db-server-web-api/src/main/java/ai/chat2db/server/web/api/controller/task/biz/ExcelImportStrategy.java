package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.listener.ReadListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ExcelImportStrategy implements ImportStrategy {

    @Override
    public void importData(File file, ImportContext importContext) {
        final AtomicInteger processedCount = new AtomicInteger(0);

        try {
            PreparedStatement ps = prepareInsertStatement(importContext);

            EasyExcel.read(file, new ReadListener<Map<Integer, String>>() {
                @Override
                public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context)  {
                    if (headMap.size() != importContext.getColumnCount()) {
                        throw new BusinessException("dataSource.importColumnMismatch");
                    }
                }

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    try {
                        for (int i = 0; i < importContext.getColumnCount(); i++) {
                            ps.setObject(i + 1, data.get(i));
                        }
                        ps.addBatch();
                        int count = processedCount.incrementAndGet();

                        if (count % 200 == 0) {
                            ps.executeBatch();
                            ps.clearBatch();
                            importContext.getProgressUpdater().accept(count);
                        }
                    } catch (SQLException e) {
                        log.error("import excel error", e);
                        throw new BusinessException("dataSource.importError");
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    try {
                        ps.executeBatch();
                        ps.close();
                        importContext.getProgressUpdater().accept(processedCount.get());
                    } catch (SQLException e) {
                        log.error("import excel error", e);
                        throw new BusinessException("dataSource.importError");
                    }
                }
            }).sheet().doRead();
        } catch (Exception e) {
            log.error("import excel error", e);
            throw new BusinessException("dataSource.importError");
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "XLSX".equalsIgnoreCase(fileType) || "XLS".equalsIgnoreCase(fileType);
    }

    private PreparedStatement prepareInsertStatement(ImportContext importContext) throws SQLException {
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
