package ai.chat2db.server.web.api.controller.config.request;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ModelServiceUpsertRequest {
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
