package ai.chat2db.server.web.api.controller.rdb.request;

import lombok.Data;

import java.util.Map;

/**
 * 数据生成请求VO
 */
@Data
public class DataGenerationRequestVO {

    /**
     * 数据源ID
     */
    private Long dataSourceId;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 模式名称
     */
    private String schemaName;

    /**
     * 表名称
     */
    private String tableName;

    /**
     * 生成行数
     */
    private Integer rowCount;

    /**
     * 列配置映射 (列名 -> 数据生成类型)
     */
    private Map<String, String> columnConfigs;

    /**
     * 批量大小
     */
    private Integer batchSize = 1000;

    /**
     * 是否预览模式（只生成10行）
     */
    private Boolean previewMode = false;
}
