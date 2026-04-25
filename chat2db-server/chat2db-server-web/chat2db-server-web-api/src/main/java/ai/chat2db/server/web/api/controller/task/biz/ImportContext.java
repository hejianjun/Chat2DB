package ai.chat2db.server.web.api.controller.task.biz;

import lombok.Builder;
import lombok.Data;

import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;

@Data
@Builder
public class ImportContext {
    private Long taskId;
    private String tableName;
    private List<String> headerList;
    private int columnCount;
    private Connection connection;
    private Consumer<Integer> progressUpdater;
}
