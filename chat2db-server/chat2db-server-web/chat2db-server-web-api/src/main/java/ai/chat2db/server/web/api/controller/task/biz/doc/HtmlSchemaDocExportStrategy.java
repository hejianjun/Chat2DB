package ai.chat2db.server.web.api.controller.task.biz.doc;

import ai.chat2db.server.domain.api.enums.ExportTypeEnum;
import ai.chat2db.server.domain.api.model.IndexInfo;
import ai.chat2db.server.domain.api.model.TableParameter;
import ai.chat2db.server.domain.api.model.ForeignKeyInfo;
import ai.chat2db.server.tools.common.config.GlobalDict;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.server.web.api.controller.rdb.doc.constant.PatternConstant;
import ai.chat2db.server.web.api.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HtmlSchemaDocExportStrategy extends AbstractSchemaDocExportStrategy {

    @Override
    public boolean supports(String exportType) {
        return ExportTypeEnum.HTML.getCode().equals(exportType);
    }

    @Override
    protected void doExport(OutputStream outputStream, SchemaDocExportContext context) throws Exception {
        Map<String, List<Map.Entry<String, List<TableParameter>>>> allMap = context.getTableParameterMap().entrySet()
                .stream().collect(Collectors.groupingBy(v -> v.getKey().split("---")[0]));
        StringBuilder htmlText = new StringBuilder();
        StringBuilder catalogue = new StringBuilder();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            for (Map.Entry<String, List<Map.Entry<String, List<TableParameter>>>> myMap : allMap.entrySet()) {
                String database = myMap.getKey();
                String title = MessageFormat.format(PatternConstant.HTML_TITLE, I18nUtils.getMessage("main.databaseText") + database);
                catalogue.append("<li>").append(MessageFormat.format(PatternConstant.HTML_INDEX_ITEM, I18nUtils.getMessage("main.databaseText")
                        + database, I18nUtils.getMessage("main.databaseText") + database)).append("<ol>");
                htmlText.append(title).append("\n");

                for (Map.Entry<String, List<TableParameter>> parameterMap : myMap.getValue()) {
                    String tableName = parameterMap.getKey().split("---")[1];
                    catalogue.append("<li>").append(MessageFormat.format(PatternConstant.HTML_INDEX_ITEM, database + tableName, tableName));
                    htmlText.append(MessageFormat.format(PatternConstant.HTML_CATALOG, database + tableName, tableName)).append("\n<p></p>");

                    if (!context.getIndexMap().isEmpty()) {
                        htmlText.append("<table>\n");
                        htmlText.append(PatternConstant.HTML_INDEX_TABLE_HEADER);
                        String name = parameterMap.getKey().split("\\[")[0];
                        List<IndexInfo> indexInfoVOList = context.getIndexMap().get(name);
                        if (indexInfoVOList != null) {
                            for (IndexInfo indexInfo : indexInfoVOList) {
                                htmlText.append(String.format(PatternConstant.HTML_INDEX_TABLE_BODY, getIndexValues(indexInfo)));
                            }
                        }
                        htmlText.append("</table>\n");
                        htmlText.append("\n<p></p>");
                    } else {
                        htmlText.append(String.format(PatternConstant.HTML_INDEX_TABLE_BODY, getIndexValues(new IndexInfo())));
                    }

                    htmlText.append("<table>\n");
                    htmlText.append(PatternConstant.HTML_TABLE_HEADER);
                    List<TableParameter> exportList = parameterMap.getValue();
                    for (TableParameter tableParameter : exportList) {
                        htmlText.append(String.format(PatternConstant.HTML_TABLE_BODY, getColumnValues(tableParameter)));
                    }
                    htmlText.append("</table>\n");
                }

                // 导出表间关系
                List<ForeignKeyInfo> foreignKeyList = context.getForeignKeyList();
                if (foreignKeyList != null && !foreignKeyList.isEmpty()) {
                    List<ForeignKeyInfo> dbForeignKeys = foreignKeyList.stream()
                            .filter(fk -> database.equals(fk.getDatabaseName()))
                            .collect(Collectors.toList());
                    if (!dbForeignKeys.isEmpty()) {
                        htmlText.append("<h2>").append(I18nUtils.getMessage("workspace.tableRelation.title")).append("</h2>\n");
                        htmlText.append("<table>\n<tr><th>")
                                .append(I18nUtils.getMessage("workspace.tableRelation.masterTable")).append("</th><th>")
                                .append(I18nUtils.getMessage("workspace.tableRelation.uniqueColumn")).append("</th><th>")
                                .append(I18nUtils.getMessage("workspace.tableRelation.childTable")).append("</th><th>")
                                .append(I18nUtils.getMessage("workspace.tableRelation.relationColumn")).append("</th><th>")
                                .append(I18nUtils.getMessage("editTable.label.sourceType")).append("</th><th>")
                                .append(I18nUtils.getMessage("editTable.label.comment")).append("</th></tr>\n");
                        for (ForeignKeyInfo fk : dbForeignKeys) {
                            htmlText.append("<tr><td>").append(dealWith(fk.getReferencedTable())).append("</td><td>")
                                    .append(dealWith(fk.getReferencedColumnName())).append("</td><td>")
                                    .append(dealWith(fk.getTableName())).append("</td><td>")
                                    .append(dealWith(fk.getColumnName())).append("</td><td>")
                                    .append(dealWith(fk.getSourceType())).append("</td><td>")
                                    .append(dealWith(fk.getComment())).append("</td></tr>\n");
                        }
                        htmlText.append("</table>\n");
                    }
                }
                htmlText.append("<p></p>");
                catalogue.append("</ol>");
            }
            catalogue.append("</li>");

            try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/" + GlobalDict.TEMPLATE_FILE.get(0));
                 ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }
                String str = result.toString(String.valueOf(StandardCharsets.UTF_8));
                str = str.replace("${data}", htmlText).replace("${catalogue}", catalogue);
                writer.write(str);
            }
            writer.flush();
        }
    }

    @Override
    protected String dealWith(String source) {
        return StringUtils.isNullForHtml(source);
    }
}
