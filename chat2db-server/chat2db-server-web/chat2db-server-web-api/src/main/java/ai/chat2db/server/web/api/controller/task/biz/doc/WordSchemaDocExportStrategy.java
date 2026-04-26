package ai.chat2db.server.web.api.controller.task.biz.doc;

import ai.chat2db.server.domain.api.enums.ExportTypeEnum;
import ai.chat2db.server.domain.api.model.IndexInfo;
import ai.chat2db.server.domain.api.model.TableParameter;
import ai.chat2db.server.tools.common.config.GlobalDict;
import ai.chat2db.server.web.api.controller.rdb.doc.constant.CommonConstant;
import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.data.Includes;
import com.deepoove.poi.data.RowRenderData;
import com.deepoove.poi.data.Rows;
import com.deepoove.poi.data.Tables;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WordSchemaDocExportStrategy extends AbstractSchemaDocExportStrategy {

    @Override
    public boolean supports(String exportType) {
        return ExportTypeEnum.WORD.getCode().equals(exportType);
    }

    @Override
    protected void doExport(OutputStream outputStream, SchemaDocExportContext context) throws Exception {
        boolean isExportIndex = context.getExportOptions().getIsExportIndex();
        InputStream filePath = this.getClass().getClassLoader().getResourceAsStream("template/" + GlobalDict.TEMPLATE_FILE.get(1));
        InputStream subFile = this.getClass().getClassLoader().getResourceAsStream("template/" + GlobalDict.TEMPLATE_FILE.get(2));
        Map<String, List<Map.Entry<String, List<TableParameter>>>> allMap = context.getTableParameterMap().entrySet()
                .stream().collect(Collectors.groupingBy(v -> v.getKey().split("---")[0]));
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> myDataMap = new HashMap<>(2);

        RowRenderData indexHeaderRow = Rows.of(CommonConstant.INDEX_HEAD_NAMES).center().textBold().textColor("000000").bgColor("bfbfbf").create();
        RowRenderData tableHeaderRow = Rows.of(CommonConstant.COLUMN_HEAD_NAMES).center().textBold().textColor("000000").bgColor("bfbfbf").create();

        for (Map.Entry<String, List<Map.Entry<String, List<TableParameter>>>> myMap : allMap.entrySet()) {
            String database = myMap.getKey();
            int i = 1;
            for (Map.Entry<String, List<TableParameter>> parameterMap : myMap.getValue()) {
                Map<String, Object> tableData = new HashMap<>(8);
                if (isExportIndex) {
                    String name = parameterMap.getKey().split("\\[")[0];
                    List<IndexInfo> indexInfoVOList = context.getIndexMap().get(name);
                    List<RowRenderData> rowList = getIndexValues(indexInfoVOList, indexHeaderRow);
                    tableData.put("indexTable", Tables.create(rowList.toArray(new RowRenderData[0])));
                }
                if (i == 1) {
                    Map<String, String> map = new HashMap<>(2);
                    map.put("dataBase", database);
                    tableData.put("ifDatabase", map);
                }
                String tableName = parameterMap.getKey().split("---")[1];
                tableData.put("number", i);
                tableData.put("name", tableName);
                List<TableParameter> tableParameterList = parameterMap.getValue();
                List<RowRenderData> rowList = getColumnValues(tableParameterList, tableHeaderRow);
                tableData.put("table", Tables.create(rowList.toArray(new RowRenderData[0])));
                i++;
                list.add(tableData);
            }
        }
        myDataMap.put("mydata", Includes.ofStream(subFile).setRenderModel(list).create());
        XWPFTemplate template = XWPFTemplate.compile(filePath).render(myDataMap);
        ai.chat2db.server.web.api.util.AddToTopic.generateTOC(template.getXWPFDocument(), outputStream);
    }

    private List<RowRenderData> getColumnValues(List<TableParameter> list, RowRenderData tableHeaderRow) {
        List<RowRenderData> rowRenderDataList = new ArrayList<>();
        rowRenderDataList.add(tableHeaderRow);
        for (TableParameter tableParameter : list) {
            String[] values = Arrays.stream(getColumnValues(tableParameter)).toArray(String[]::new);
            rowRenderDataList.add(Rows.of(values).center().create());
        }
        return rowRenderDataList;
    }

    private List<RowRenderData> getIndexValues(List<IndexInfo> list, RowRenderData tableHeaderRow) {
        List<RowRenderData> rowRenderDataList = new ArrayList<>();
        rowRenderDataList.add(tableHeaderRow);
        if (list == null || list.isEmpty()) {
            String[] values = Arrays.stream(getIndexValues(new IndexInfo())).toArray(String[]::new);
            rowRenderDataList.add(Rows.of(values).center().create());
            return rowRenderDataList;
        }
        for (IndexInfo indexInfo : list) {
            String[] values = Arrays.stream(getIndexValues(indexInfo)).toArray(String[]::new);
            rowRenderDataList.add(Rows.of(values).center().create());
        }
        return rowRenderDataList;
    }
}
