package ai.chat2db.server.web.api.controller.config.request;

import lombok.Data;

@Data
public class ModelItemRequest {
    private String id;
    private String name;
    private String model;
}
