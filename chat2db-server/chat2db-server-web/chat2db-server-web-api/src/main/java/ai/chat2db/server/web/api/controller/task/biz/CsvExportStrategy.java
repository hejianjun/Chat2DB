package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.web.api.model.ExcelWrapper;
import ai.chat2db.spi.jdbc.DefaultValueHandler;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvExportStrategy extends AbstractExportStrategy {

    @Override
    public void doExport(ResultSet rs, ResultSetMetaData metaData, List<String> headerNames,
                         int columnCount, DefaultValueHandler valueHandler,
                         ExportContext exportContext) throws SQLException {

        ExcelWrapper excelWrapper = new ExcelWrapper();
        try {
            ExcelWriterBuilder excelWriterBuilder = EasyExcel.write(exportContext.getFile())
                    .charset(java.nio.charset.StandardCharsets.UTF_8)
                    .excelType(ExcelTypeEnum.CSV);
            excelWriterBuilder.head(EasyCollectionUtils.toList(headerNames, name -> Lists.newArrayList(name)));
            excelWrapper.setExcelWriter(excelWriterBuilder.build());
            excelWrapper.setWriteSheet(EasyExcel.writerSheet(0).build());

            int processedCount = 0;
            while (rs.next()) {
                List<String> row = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.add(valueHandler.getString(rs, i, false));
                }
                List<List<String>> writeDataList = Lists.newArrayList();
                writeDataList.add(row);
                excelWrapper.getExcelWriter().write(writeDataList, excelWrapper.getWriteSheet());
                processedCount++;
                updateProgress(processedCount, exportContext);
            }
            updateProgress(processedCount, exportContext);
        } finally {
            if (excelWrapper.getExcelWriter() != null) {
                excelWrapper.getExcelWriter().finish();
            }
        }
    }

    @Override
    public boolean supports(String exportType) {
        return "CSV".equalsIgnoreCase(exportType);
    }
}
