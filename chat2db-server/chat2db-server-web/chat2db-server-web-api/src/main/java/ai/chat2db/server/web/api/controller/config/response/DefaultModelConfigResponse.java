package ai.chat2db.server.web.api.controller.config.response;

import lombok.Data;

@Data
public class DefaultModelConfigResponse {
    private String defaultModelId = "";
    private String fastModelId = "";
}
