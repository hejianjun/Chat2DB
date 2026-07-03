package ai.chat2db.server.web.api.controller.sql.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlExecuteBizServiceTest {

    private final SqlExecuteBizService service = new SqlExecuteBizService();

    @Test
    void commitsSuccessfulStatementsInConfiguredBatches() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);

        List<Integer> progress = new ArrayList<>();
        SqlExecuteBizService.SqlExecutionResult result = service.executeStatements(
                connection,
                statements(5),
                false,
                2,
                progress::add
        );

        assertEquals(5, result.getProcessedCount());
        assertEquals(5, result.getSuccessCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(List.of(5), progress);
        verify(connection, times(3)).commit();
    }

    @Test
    void rollsBackFailedStatementAndContinuesWhenSkipErrorsIsEnabled() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        Savepoint firstSavepoint = mock(Savepoint.class);
        Savepoint failedSavepoint = mock(Savepoint.class);
        Savepoint thirdSavepoint = mock(Savepoint.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.setSavepoint()).thenReturn(firstSavepoint, failedSavepoint, thirdSavepoint);
        when(statement.execute(anyString()))
                .thenReturn(true)
                .thenThrow(new SQLException("duplicate key"))
                .thenReturn(true);

        List<Integer> progress = new ArrayList<>();
        SqlExecuteBizService.SqlExecutionResult result = service.executeStatements(
                connection,
                statements(3),
                true,
                2,
                progress::add
        );

        assertEquals(3, result.getProcessedCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(1, result.getErrorDetails().size());
        assertEquals(List.of(2, 3), progress);
        verify(connection).rollback(failedSavepoint);
        verify(connection, times(3)).releaseSavepoint(org.mockito.ArgumentMatchers.any());
        verify(connection).commit();
        verify(statement, times(3)).execute(anyString());
    }

    @Test
    void stopsAtFirstErrorWhenSkipErrorsIsDisabled() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString()))
                .thenReturn(true)
                .thenThrow(new SQLException("syntax error"));
        List<SqlExecuteBizService.SqlStatementItem> statements = List.of(
                new SqlExecuteBizService.SqlStatementItem("select 1;", 1),
                new SqlExecuteBizService.SqlStatementItem("broken sql;", 8),
                new SqlExecuteBizService.SqlStatementItem("select 2;", 9)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.executeStatements(connection, statements, false, 1000, ignored -> {
                })
        );

        assertEquals("dataSource.executeSqlErrorDetail", exception.getCode());
        assertEquals(2, exception.getArgs()[0]);
        assertEquals(8, exception.getArgs()[1]);
        verify(statement, times(2)).execute(anyString());
        verify(connection, never()).commit();
    }

    private List<SqlExecuteBizService.SqlStatementItem> statements(int count) {
        List<SqlExecuteBizService.SqlStatementItem> statements = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            statements.add(new SqlExecuteBizService.SqlStatementItem("select " + index + ";", index));
        }
        return statements;
    }
}
