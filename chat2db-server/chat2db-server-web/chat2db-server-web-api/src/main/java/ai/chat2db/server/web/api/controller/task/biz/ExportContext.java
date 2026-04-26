package ai.chat2db.server.web.api.controller.task.biz;

import com.alibaba.druid.DbType;
import lombok.Builder;
import lombok.Data;

import java.io.File;
import java.util.function.Consumer;

@Data
@Builder
public class ExportContext {
    private String sql;
    private File file;
    private DbType dbType;
    private String tableName;
    private Long taskId;
    private Consumer<Integer> progressUpdater;
}
