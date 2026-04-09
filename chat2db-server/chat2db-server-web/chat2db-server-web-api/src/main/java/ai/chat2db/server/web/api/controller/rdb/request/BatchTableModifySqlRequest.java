package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import ai.chat2db.spi.model.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量修改表sql请求
 */
@Data
public class BatchTableModifySqlRequest extends DataSourceBaseRequest {

    /**
     * 旧的表结构
     * 为空代表新建表
     */
    private List<Table> oldTables;

    /**
     * 新的表结构
     */
    @NotNull
    private List<Table> newTables;
}
