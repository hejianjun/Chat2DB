package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.ValueHandler;
import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class CommandResultIterableTest {

    @Test
    void fetchesAllCommandPagesLazily() {
        RecordingExecutor executor = new RecordingExecutor(List.of(
                result(true, "first"),
                result(false, "second")));
        CommandResultIterable results = new CommandResultIterable(
                executor, "db.events.find({})", 9L, "location", null, true, 1000);

        Iterator<ExecuteResult> iterator = results.iterator();
        Assertions.assertEquals(0, executor.commands.size());
        Assertions.assertEquals("first", iterator.next().getDataList().get(0).get(0));
        Assertions.assertEquals("second", iterator.next().getDataList().get(0).get(0));
        Assertions.assertFalse(iterator.hasNext());

        Assertions.assertEquals(2, executor.commands.size());
        Assertions.assertEquals(1, executor.commands.get(0).getPageNo());
        Assertions.assertEquals(2, executor.commands.get(1).getPageNo());
        Assertions.assertEquals(1000, executor.commands.get(0).getPageSize());
        Assertions.assertEquals("location", executor.commands.get(0).getDatabaseName());
    }

    @Test
    void currentPageStopsAfterOneCommand() {
        RecordingExecutor executor = new RecordingExecutor(List.of(result(true, "first")));
        CommandResultIterable results = new CommandResultIterable(
                executor, "db.events.find({}).limit(200)", 9L, "location", null, false, 1000);

        Iterator<ExecuteResult> iterator = results.iterator();
        Assertions.assertEquals("first", iterator.next().getDataList().get(0).get(0));
        Assertions.assertFalse(iterator.hasNext());
        Assertions.assertEquals(1, executor.commands.size());
    }

    @Test
    void commandFailureBecomesExportError() {
        ExecuteResult failure = ExecuteResult.builder()
                .success(false)
                .message("invalid MQL")
                .build();
        CommandResultIterable results = new CommandResultIterable(
                new RecordingExecutor(List.of(failure)), "invalid", 9L, "location", null, true, 1000);

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class, () -> results.iterator().hasNext());
        Assertions.assertEquals("dataSource.exportError", exception.getCode());
        Assertions.assertArrayEquals(new Object[]{"invalid MQL"}, exception.getArgs());
    }

    private static ExecuteResult result(boolean hasNextPage, String value) {
        return ExecuteResult.builder()
                .success(true)
                .hasNextPage(hasNextPage)
                .dataList(List.of(List.of(value)))
                .build();
    }

    private static class RecordingExecutor implements CommandExecutor {

        private final List<ExecuteResult> results;
        private final List<Command> commands = new ArrayList<>();

        private RecordingExecutor(List<ExecuteResult> results) {
            this.results = results;
        }

        @Override
        public List<ExecuteResult> execute(Command command) {
            commands.add(command);
            return List.of(results.get(commands.size() - 1));
        }

        @Override
        public ExecuteResult executeUpdate(String sql, Connection connection, int n) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecuteResult execute(String sql, Connection connection, boolean limitRowSize, Integer offset,
                                     Integer count, ValueHandler valueHandler) throws SQLException {
            throw new UnsupportedOperationException();
        }
    }
}
