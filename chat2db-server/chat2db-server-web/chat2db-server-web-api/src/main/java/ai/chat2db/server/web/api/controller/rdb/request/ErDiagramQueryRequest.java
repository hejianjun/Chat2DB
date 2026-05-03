package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ER图查询请求
 */
@Data
public class ErDiagramQueryRequest extends DataSourceBaseRequest {

    /**
     * 表名过滤条件，支持模糊匹配
     */
    private String tableNameFilter;

    /**
     * 是否包含虚拟外键（根据命名规范推断的外键），默认为true
     */
    private Boolean includeVirtualFk = true;

    /**
     * 是否同步数据库真实外键到本地，默认为false
     */
    private Boolean syncForeignKeys = false;
}