package ai.chat2db.server.web.api.controller.config.response;

import java.util.ArrayList;
import java.util.List;

import ai.chat2db.server.web.api.controller.config.request.ModelItemRequest;
import lombok.Data;

@Data
public class ModelServiceResponse {
    private String id;
    private String name;
    private String provider;
    private String apiKey;
    private String apiHost;
    private String httpProxyHost;
    private String httpProxyPort;
    private String organizationId;
    private String projectId;
    private List<ModelItemRequest> modelList = new ArrayList<>();
}
