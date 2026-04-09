package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 外键删除
 */
@Data
public class KeyDeleteRequest extends DataSourceBaseRequest {

    /**
     * 外键名
     */
    @NotNull
    private String keyName;
}
