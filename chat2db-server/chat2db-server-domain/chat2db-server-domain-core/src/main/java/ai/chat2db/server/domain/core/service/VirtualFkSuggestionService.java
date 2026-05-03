package ai.chat2db.server.domain.core.service;

import ai.chat2db.spi.model.VirtualForeignKeySuggestion;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VirtualFkSuggestionService {

    public List<VirtualForeignKeySuggestion> suggest(String sql) {
        if (sql == null || sql.trim().isEmpty()) return Collections.emptyList();
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            return suggest(stmt);
        } catch (JSQLParserException e) {
            return Collections.emptyList();
        }
    }

    public List<VirtualForeignKeySuggestion> suggest(Statement stmt) {
        List<VirtualForeignKeySuggestion> suggestions = new ArrayList<>();
        if (stmt instanceof Select selectStmt) {
            SelectBody body = selectStmt.getSelectBody();
            if (body instanceof PlainSelect plainSelect) {
                Map<String, String> aliasMap = buildAliasMap(plainSelect);
                extractSuggestions(plainSelect, aliasMap, suggestions);
            }
        }
        return suggestions;
    }

    private Map<String, String> buildAliasMap(PlainSelect plainSelect) {
        Map<String, String> map = new HashMap<>();
        addFromItem(plainSelect.getFromItem(), map);
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                addFromItem(join.getRightItem(), map);
            }
        }
        return map;
    }

    private void addFromItem(FromItem item, Map<String, String> map) {
        if (item instanceof Table table) {
            String name = table.getName();
            map.put(name, name);
            if (table.getAlias() != null) {
                map.put(table.getAlias().getName(), name);
            }
        }
    }

    private void extractSuggestions(PlainSelect plainSelect, Map<String, String> aliasMap, List<VirtualForeignKeySuggestion> suggestions) {
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.getOnExpression() != null) {
                    processExpression(join.getOnExpression(), aliasMap, suggestions);
                }
            }
        }
    }

    private void processExpression(Expression expr, Map<String, String> aliasMap, List<VirtualForeignKeySuggestion> suggestions) {
        if (expr instanceof EqualsTo equalsTo) {
            processEquality(equalsTo.getLeftExpression(), equalsTo.getRightExpression(), aliasMap, suggestions);
        } else if (expr instanceof AndExpression andExpr) {
            processExpression(andExpr.getLeftExpression(), aliasMap, suggestions);
            processExpression(andExpr.getRightExpression(), aliasMap, suggestions);
        } else if (expr instanceof OrExpression orExpr) {
            processExpression(orExpr.getLeftExpression(), aliasMap, suggestions);
            processExpression(orExpr.getRightExpression(), aliasMap, suggestions);
        }
    }

    private void processEquality(Expression left, Expression right, Map<String, String> aliasMap, List<VirtualForeignKeySuggestion> suggestions) {
        if (left instanceof Column cLeft && right instanceof Column cRight) {
            if (cLeft.getTable() == null || cRight.getTable() == null) return;

            String lTableAlias = cLeft.getTable().getName();
            String lCol = cLeft.getColumnName();
            String rTableAlias = cRight.getTable().getName();
            String rCol = cRight.getColumnName();

            String lRealTable = aliasMap.get(lTableAlias);
            String rRealTable = aliasMap.get(rTableAlias);

            if (lRealTable != null && rRealTable != null && !lRealTable.equalsIgnoreCase(rRealTable)) {
                boolean leftIsFK = lCol.toLowerCase().endsWith("_id");
                boolean rightIsFK = rCol.toLowerCase().endsWith("_id");

                if (leftIsFK && !rightIsFK) {
                    addSuggestion(lRealTable, lCol, rRealTable, rCol, suggestions);
                } else if (!leftIsFK && rightIsFK) {
                    addSuggestion(rRealTable, rCol, lRealTable, lCol, suggestions);
                }
            }
        }
    }

    private void addSuggestion(String srcTable, String srcCol, String tgtTable, String tgtCol, List<VirtualForeignKeySuggestion> suggestions) {
        for (VirtualForeignKeySuggestion s : suggestions) {
            if (s.getSourceTable().equalsIgnoreCase(srcTable) &&
                s.getSourceColumn().equalsIgnoreCase(srcCol) &&
                s.getTargetTable().equalsIgnoreCase(tgtTable) &&
                s.getTargetColumn().equalsIgnoreCase(tgtCol)) {
                return;
            }
        }
        suggestions.add(VirtualForeignKeySuggestion.builder()
                .sourceTable(srcTable)
                .sourceColumn(srcCol)
                .targetTable(tgtTable)
                .targetColumn(tgtCol)
                .reason("JOIN condition")
                .build());
    }
}
