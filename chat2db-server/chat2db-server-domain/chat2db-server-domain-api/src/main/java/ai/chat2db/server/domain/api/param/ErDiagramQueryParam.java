package ai.chat2db.server.domain.api.param;

import java.io.Serial;

import jakarta.validation.constraints.NotNull;

import ai.chat2db.server.tools.base.wrapper.param.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * ER图查询参数
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ErDiagramQueryParam extends QueryParam {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据源ID
     */
    @NotNull
    private Long dataSourceId;

    /**
     * 数据库名
     */
    @NotNull
    private String databaseName;

    /**
     * Schema名（Oracle/PostgreSQL等需要）
     */
    private String schemaName;

    /**
     * 表名过滤条件，支持模糊匹配
     */
    private String tableNameFilter;

    /**
     * 是否包含虚拟外键，默认为true
     */
    private Boolean includeVirtualFk;

    /**
     * 是否同步数据库真实外键到本地，默认为false
     */
    private Boolean syncForeignKeys;
}