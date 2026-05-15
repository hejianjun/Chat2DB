package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.web.api.controller.task.request.FieldMapping;
import lombok.Builder;
import lombok.Data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Data
@Builder
public class ImportContext {
    private Long taskId;
    private String tableName;
    private List<String> headerList;
    private Map<String, Integer> columnOrderMap;
    private int columnCount;
    private Connection connection;
    private Consumer<Integer> progressUpdater;

    /**
     * 字段映射配置列表
     */
    private List<FieldMapping> fieldMappings;

    /**
     * 源字段到目标字段的映射 Map
     * key: 源字段名, value: 目标字段名
     */
    private Map<String, String> sourceToTargetMap;
}
