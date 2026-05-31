package ai.chat2db.server.web.api.controller.task.biz.doc;

import ai.chat2db.server.domain.api.enums.ExportTypeEnum;
import ai.chat2db.server.domain.api.model.IndexInfo;
import ai.chat2db.server.domain.api.model.TableParameter;
import ai.chat2db.server.domain.api.model.ForeignKeyInfo;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.server.web.api.controller.rdb.doc.constant.PatternConstant;
import ai.chat2db.server.web.api.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MarkdownSchemaDocExportStrategy extends AbstractSchemaDocExportStrategy {

    @Override
    public boolean supports(String exportType) {
        return ExportTypeEnum.MARKDOWN.getCode().equals(exportType);
    }

    @Override
    protected void doExport(OutputStream outputStream, SchemaDocExportContext context) throws Exception {
        Map<String, List<Map.Entry<String, List<TableParameter>>>> allMap = context.getTableParameterMap().entrySet()
                .stream().collect(Collectors.groupingBy(v -> v.getKey().split("---")[0]));

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            for (Map.Entry<String, List<Map.Entry<String, List<TableParameter>>>> myMap : allMap.entrySet()) {
                String database = myMap.getKey();
                String title = String.format(PatternConstant.TITLE, I18nUtils.getMessage("main.databaseText") + database);
                writer.write(title);
                writeLineSeparator(writer, 2);

                for (Map.Entry<String, List<TableParameter>> parameterMap : myMap.getValue()) {
                    String tableName = parameterMap.getKey().split("---")[1];
                    writer.write(String.format(PatternConstant.CATALOG, tableName));
                    writeLineSeparator(writer, 1);

                    if (!context.getIndexMap().isEmpty()) {
                        writer.write(PatternConstant.ALL_INDEX_TABLE_HEADER);
                        writeLineSeparator(writer, 1);
                        writer.write(PatternConstant.INDEX_TABLE_SEPARATOR);
                        writeLineSeparator(writer, 1);
                        String name = parameterMap.getKey().split("\\[")[0];
                        List<IndexInfo> indexInfoVOList = context.getIndexMap().get(name);
                        if (indexInfoVOList != null) {
                            for (IndexInfo indexInfo : indexInfoVOList) {
                                writer.write(String.format(PatternConstant.INDEX_TABLE_BODY, getIndexValues(indexInfo)));
                                writeLineSeparator(writer, 1);
                            }
                        }
                        writeLineSeparator(writer, 1);
                    }
                    writeLineSeparator(writer, 2);

                    writer.write(PatternConstant.ALL_TABLE_HEADER);
                    writeLineSeparator(writer, 1);
                    writer.write(PatternConstant.TABLE_SEPARATOR);
                    writeLineSeparator(writer, 1);

                    List<TableParameter> exportList = parameterMap.getValue();
                    for (TableParameter tableParameter : exportList) {
                        writer.write(String.format(PatternConstant.TABLE_BODY, getColumnValues(tableParameter)));
                        writeLineSeparator(writer, 1);
                    }
                    writeLineSeparator(writer, 2);
                }

                // 导出表间关系
                List<ForeignKeyInfo> foreignKeyList = context.getForeignKeyList();
                if (foreignKeyList != null && !foreignKeyList.isEmpty()) {
                    List<ForeignKeyInfo> dbForeignKeys = foreignKeyList.stream()
                            .filter(fk -> database.equals(fk.getTableName()) || database.equals(fk.getReferencedTable()))
                            .collect(Collectors.toList());
                    if (!dbForeignKeys.isEmpty()) {
                        writer.write("## " + I18nUtils.getMessage("workspace.tableRelation.title"));
                        writeLineSeparator(writer, 2);
                        writer.write("| " + I18nUtils.getMessage("workspace.tableRelation.masterTable") +
                                " | " + I18nUtils.getMessage("workspace.tableRelation.uniqueColumn") +
                                " | " + I18nUtils.getMessage("workspace.tableRelation.childTable") +
                                " | " + I18nUtils.getMessage("workspace.tableRelation.relationColumn") +
                                " | " + I18nUtils.getMessage("editTable.label.sourceType") +
                                " | " + I18nUtils.getMessage("editTable.label.comment") + " |");
                        writeLineSeparator(writer, 1);
                        writer.write("|---|---|---|---|---|---|");
                        writeLineSeparator(writer, 1);
                        for (ForeignKeyInfo fk : dbForeignKeys) {
                            writer.write(String.format("| %s | %s | %s | %s | %s | %s |",
                                    dealWith(fk.getReferencedTable()),
                                    dealWith(fk.getReferencedColumnName()),
                                    dealWith(fk.getTableName()),
                                    dealWith(fk.getColumnName()),
                                    dealWith(fk.getSourceType()),
                                    dealWith(fk.getComment())));
                            writeLineSeparator(writer, 1);
                        }
                        writeLineSeparator(writer, 2);
                    }
                }
            }
            writer.flush();
        }
    }

    private void writeLineSeparator(BufferedWriter writer, int number) throws Exception {
        for (int i = 0; i < number; i++) {
            writer.write(System.lineSeparator());
        }
    }

    @Override
    protected String dealWith(String source) {
        return StringUtils.isNullForHtml(source);
    }
}
