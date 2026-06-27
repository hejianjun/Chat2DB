package ai.chat2db.server.web.api.controller.config.request;

import lombok.Data;

@Data
public class ModelItemRequest {
    private String id;
    private String name;
    private String model;
    /**
     * Maximum context size advertised by the model. Null keeps the server default of 32768 tokens.
     */
    private Integer contextWindowTokens;
}
