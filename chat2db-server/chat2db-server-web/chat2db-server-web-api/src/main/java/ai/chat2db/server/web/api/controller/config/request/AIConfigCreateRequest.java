
package ai.chat2db.server.web.api.controller.config.request;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AIConfigCreateRequest {

    private String apiKey;

    private String secretKey;

    private String apiHost;

    private String httpProxyHost;

    private String httpProxyPort;

    @NotNull
    private String aiSqlSource;

    private Boolean stream = Boolean.TRUE;

    private String model;

    private String embeddingModel;

    private String temperature;

    private String maxTokens;

    private String topP;

    private String topK;

    private String stopSequences;

    private String betaVersion;

    private String n;

    private String stop;

    private String presencePenalty;

    private String frequencyPenalty;

    private String logitBias;

    private String user;

    private String organizationId;

    private String projectId;
}
