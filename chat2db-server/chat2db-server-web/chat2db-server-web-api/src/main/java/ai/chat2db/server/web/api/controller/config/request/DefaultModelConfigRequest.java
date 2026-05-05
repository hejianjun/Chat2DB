package ai.chat2db.server.web.api.controller.config.request;

import lombok.Data;

@Data
public class DefaultModelConfigRequest {
    private String defaultModelId;
    private String fastModelId;
}
