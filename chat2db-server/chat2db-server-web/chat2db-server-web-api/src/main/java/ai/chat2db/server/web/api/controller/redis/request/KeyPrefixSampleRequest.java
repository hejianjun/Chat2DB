package ai.chat2db.server.web.api.controller.redis.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KeyPrefixSampleRequest extends DataSourceBaseRequest {

    @NotBlank
    private String prefix;
}
