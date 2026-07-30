package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.CommandResultStreamer;
import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

class CommandResultIterable implements Iterable<ExecuteResult> {

    static final int DEFAULT_BATCH_SIZE = 1000;

    private final CommandExecutor executor;
    private final String sql;
    private final Long dataSourceId;
    private final String databaseName;
    private final String schemaName;
    private final boolean allPages;
    private final int batchSize;

    CommandResultIterable(CommandExecutor executor, String sql, Long dataSourceId, String databaseName,
                          String schemaName, boolean allPages) {
        this(executor, sql, dataSourceId, databaseName, schemaName, allPages, DEFAULT_BATCH_SIZE);
    }

    CommandResultIterable(CommandExecutor executor, String sql, Long dataSourceId, String databaseName,
                          String schemaName, boolean allPages, int batchSize) {
        this.executor = executor;
        this.sql = sql;
        this.dataSourceId = dataSourceId;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.allPages = allPages;
        this.batchSize = batchSize;
    }

    @Override
    public Iterator<ExecuteResult> iterator() {
        if (allPages && executor instanceof CommandResultStreamer streamer) {
            return validatingIterator(streamer.stream(command(1), batchSize).iterator());
        }
        return new Iterator<>() {
            private int pageNo = 1;
            private ExecuteResult next;
            private boolean finished;

            @Override
            public boolean hasNext() {
                if (next == null && !finished) {
                    next = executePage(pageNo);
                }
                return next != null;
            }

            @Override
            public ExecuteResult next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                ExecuteResult current = next;
                next = null;
                boolean hasRows = current.getDataList() != null && !current.getDataList().isEmpty();
                if (!allPages || !Boolean.TRUE.equals(current.getHasNextPage()) || !hasRows) {
                    finished = true;
                } else {
                    pageNo++;
                }
                return current;
            }
        };
    }

    private ExecuteResult executePage(int pageNo) {
        List<ExecuteResult> results = executor.execute(command(pageNo));
        if (results == null || results.size() != 1) {
            throw exportError("The export query must contain exactly one statement");
        }
        return validate(results.get(0));
    }

    private Command command(int pageNo) {
        Command command = new Command();
        command.setScript(sql);
        command.setDataSourceId(dataSourceId);
        command.setDatabaseName(databaseName);
        command.setSchemaName(schemaName);
        command.setPageNo(pageNo);
        command.setPageSize(batchSize);
        return command;
    }

    private Iterator<ExecuteResult> validatingIterator(Iterator<ExecuteResult> iterator) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ExecuteResult next() {
                return validate(iterator.next());
            }
        };
    }

    private ExecuteResult validate(ExecuteResult result) {
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            throw exportError(StringUtils.defaultIfBlank(result.getMessage(), "Command execution failed"));
        }
        return result;
    }

    private BusinessException exportError(String message) {
        return new BusinessException("dataSource.exportError", new Object[]{message});
    }
}
