package ai.chat2db.server.web.api.controller.rdb.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 数据生成规则VO
 */
@Data
public class DataGenerationRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    private Long id;

    /**
     * 数据源ID
     */
    private Long dataSourceId;

    /**
     * 数据库名
     */
    private String databaseName;

    /**
     * 模式名
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 列名
     */
    private String columnName;

    /**
     * 数据生成类型
     */
    private String generationType;

    /**
     * 生成行数
     */
    private Integer rowCount;

    /**
     * 批处理大小
     */
    private Integer batchSize;

    /**
     * 自定义参数
     */
    private String customParams;

    /**
     * 备注
     */
    private String comment;

    /**
     * 用户ID
     */
    private Long userId;
}
