package ai.chat2db.server.web.api.controller.redis.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

@Data
public class KeyPrefixQueryRequest extends DataSourceBaseRequest {

    private String searchKey;
}
