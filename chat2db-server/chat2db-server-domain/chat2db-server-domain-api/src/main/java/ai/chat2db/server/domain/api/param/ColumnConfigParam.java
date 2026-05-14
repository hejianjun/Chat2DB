package ai.chat2db.server.domain.api.param;

import lombok.Data;

/**
 * 列配置参数
 */
@Data
public class ColumnConfigParam {

    /**
     * 列名
     */
    private String columnName;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 数据生成类型
     */
    private String generationType;

    /**
     * 列注释
     */
    private String comment;

    /**
     * 是否可为空
     */
    private Boolean nullable;

    /**
     * 最大长度
     */
    private Integer maxLength;

    /**
     * 小数位数
     */
    private Integer scale;
}
