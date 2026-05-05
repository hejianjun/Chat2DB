package ai.chat2db.server.web.api.controller.config.request;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ModelServiceTestRequest {
    private String provider;
    private String apiKey;
    private String apiHost;
    private List<ModelItemRequest> modelList = new ArrayList<>();
}
