package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.spi.jdbc.DefaultValueHandler;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.visitor.VisitorFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class InsertExportStrategy extends AbstractExportStrategy {

    private static final SQLUtils.FormatOption INSERT_FORMAT_OPTION = new SQLUtils.FormatOption(true, false);

    static {
        INSERT_FORMAT_OPTION.config(VisitorFeature.OutputNameQuote, true);
    }

    @Override
    public void doExport(ResultSet rs, ResultSetMetaData metaData, List<String> headerNames,
                         int columnCount, DefaultValueHandler valueHandler,
                         ExportContext exportContext) throws SQLException {

        try (PrintWriter printWriter = new PrintWriter(exportContext.getFile(), StandardCharsets.UTF_8)) {

            List<SQLIdentifierExpr> headerList = new ArrayList<>(columnCount);
            for (String headerName : headerNames) {
                headerList.add(new SQLIdentifierExpr(headerName));
            }

            int processedCount = 0;
            while (rs.next()) {
                SQLInsertStatement sqlInsertStatement = new SQLInsertStatement();
                sqlInsertStatement.setDbType(exportContext.getDbType());
                sqlInsertStatement.setTableSource(new SQLExprTableSource(exportContext.getTableName()));
                sqlInsertStatement.getColumns().addAll(headerList);

                SQLInsertStatement.ValuesClause valuesClause = new SQLInsertStatement.ValuesClause();
                for (int i = 1; i <= columnCount; i++) {
                    valuesClause.addValue(valueHandler.getString(rs, i, false));
                }
                sqlInsertStatement.setValues(valuesClause);

                printWriter.println(SQLUtils.toSQLString(sqlInsertStatement, exportContext.getDbType(), INSERT_FORMAT_OPTION) + ";");
                processedCount++;
                updateProgress(processedCount, exportContext);
            }
            updateProgress(processedCount, exportContext);
        } catch (IOException e) {
            throw new SQLException("Failed to write INSERT export file", e);
        }
    }

    @Override
    public boolean supports(String exportType) {
        return "INSERT".equalsIgnoreCase(exportType);
    }
}
