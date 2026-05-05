
package ai.chat2db.server.web.api.controller.config;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import ai.chat2db.server.domain.api.constant.AiConfigKeys;
import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.domain.api.model.AIConfig;
import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.param.SystemConfigParam;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.domain.core.util.PermissionUtils;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.config.AiChatConfig;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.config.request.AIConfigCreateRequest;
import ai.chat2db.server.web.api.controller.config.request.DefaultModelConfigRequest;
import ai.chat2db.server.web.api.controller.config.request.ModelItemRequest;
import ai.chat2db.server.web.api.controller.config.request.ModelServiceDeleteRequest;
import ai.chat2db.server.web.api.controller.config.request.ModelServiceUpsertRequest;
import ai.chat2db.server.web.api.controller.config.request.ModelServiceTestRequest;
import ai.chat2db.server.web.api.controller.config.request.SystemConfigRequest;
import ai.chat2db.server.web.api.controller.config.response.DefaultModelConfigResponse;
import ai.chat2db.server.web.api.controller.config.response.ModelServiceResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConnectionInfoAspect
@RequestMapping("/api/config")
@RestController
public class ConfigController {
    private static final String MODEL_SERVICE_CONFIG_CODE = "ai.model.services";
    private static final String MODEL_DEFAULT_CONFIG_CODE = "ai.model.default";

    @Autowired
    private ConfigService configService;
    @Autowired
    private AiChatConfig aiChatConfig;

    @PostMapping("/system_config")
    public ActionResult systemConfig(@RequestBody SystemConfigRequest request) {
        SystemConfigParam param = SystemConfigParam.builder()
                .code(request.getCode())
                .content(request.getContent())
                .build();
        configService.createOrUpdate(param);
        return ActionResult.isSuccess();
    }

    @PostMapping("/system_config/ai")
    public ActionResult addChatGptSystemConfig(@RequestBody AIConfigCreateRequest request) {
        PermissionUtils.checkDeskTopOrAdmin();

        String sqlSource = request.getAiSqlSource();
        AiSqlSourceEnum aiSqlSourceEnum = AiSqlSourceEnum.getByName(sqlSource);
        if (Objects.isNull(aiSqlSourceEnum)) {
            sqlSource = AiSqlSourceEnum.OPENAI.getCode();
        }
        
        String prefix = "ai." + sqlSource.toLowerCase() + ".";
        
        saveConfig(prefix + "apiKey", request.getApiKey());
        saveConfig(prefix + "apiHost", request.getApiHost());
        saveConfig(prefix + "model", request.getModel());
        saveConfig(prefix + "temperature", request.getTemperature());
        saveConfig(prefix + "maxTokens", request.getMaxTokens());
        saveConfig(prefix + "topP", request.getTopP());
        saveConfig(prefix + "topK", request.getTopK());
        saveConfig(prefix + "stopSequences", request.getStopSequences());
        saveConfig(prefix + "betaVersion", request.getBetaVersion());
        saveConfig(prefix + "httpProxyHost", request.getHttpProxyHost());
        saveConfig(prefix + "httpProxyPort", request.getHttpProxyPort());
        saveConfig(prefix + "n", request.getN());
        saveConfig(prefix + "stop", request.getStop());
        saveConfig(prefix + "presencePenalty", request.getPresencePenalty());
        saveConfig(prefix + "frequencyPenalty", request.getFrequencyPenalty());
        saveConfig(prefix + "logitBias", request.getLogitBias());
        saveConfig(prefix + "user", request.getUser());
        saveConfig(prefix + "organizationId", request.getOrganizationId());
        saveConfig(prefix + "projectId", request.getProjectId());

        SystemConfigParam param = SystemConfigParam.builder()
                .code(AiConfigKeys.AI_SQL_SOURCE).content(sqlSource)
                .build();
        configService.createOrUpdate(param);

        return ActionResult.isSuccess();
    }
    
    private void saveConfig(String code, String value) {
        if (StringUtils.isNotBlank(value)) {
            SystemConfigParam param = SystemConfigParam.builder()
                    .code(code).content(value)
                    .build();
            configService.createOrUpdate(param);
        }
    }

    @GetMapping("/system_config/{code}")
    public DataResult<Config> getSystemConfig(@PathVariable("code") String code) {
        DataResult<Config> result = configService.find(code);
        return DataResult.of(result.getData());
    }

    @GetMapping("/system_config/ai")
    public DataResult<AIConfig> getChatAiSystemConfig(String aiSqlSource) {
        DataResult<Config> dbSqlSource = configService.find(AiConfigKeys.AI_SQL_SOURCE);
        if (StringUtils.isBlank(aiSqlSource)) {
            if (Objects.nonNull(dbSqlSource.getData())) {
                aiSqlSource = dbSqlSource.getData().getContent();
            }
        }
        
        AIConfig config = new AIConfig();
        AiSqlSourceEnum aiSqlSourceEnum = AiSqlSourceEnum.getByName(aiSqlSource);
        if (Objects.isNull(aiSqlSourceEnum)) {
            aiSqlSource = AiSqlSourceEnum.OPENAI.getCode();
            config.setAiSqlSource(aiSqlSource);
            return DataResult.of(config);
        }
        
        config.setAiSqlSource(aiSqlSource);
        String prefix = "ai." + aiSqlSource.toLowerCase() + ".";
        
        config.setApiKey(getConfigValue(prefix + "apiKey"));
        config.setApiHost(getConfigValue(prefix + "apiHost"));
        config.setModel(getConfigValue(prefix + "model"));
        config.setTemperature(getConfigValue(prefix + "temperature"));
        config.setMaxTokens(getConfigValue(prefix + "maxTokens"));
        config.setTopP(getConfigValue(prefix + "topP"));
        config.setTopK(getConfigValue(prefix + "topK"));
        config.setStopSequences(getConfigValue(prefix + "stopSequences"));
        config.setBetaVersion(getConfigValue(prefix + "betaVersion"));
        config.setHttpProxyHost(getConfigValue(prefix + "httpProxyHost"));
        config.setHttpProxyPort(getConfigValue(prefix + "httpProxyPort"));
        config.setN(getConfigValue(prefix + "n"));
        config.setStop(getConfigValue(prefix + "stop"));
        config.setPresencePenalty(getConfigValue(prefix + "presencePenalty"));
        config.setFrequencyPenalty(getConfigValue(prefix + "frequencyPenalty"));
        config.setLogitBias(getConfigValue(prefix + "logitBias"));
        config.setUser(getConfigValue(prefix + "user"));
        config.setOrganizationId(getConfigValue(prefix + "organizationId"));
        config.setProjectId(getConfigValue(prefix + "projectId"));

        return DataResult.of(config);
    }

    @PostMapping("/system_config/ai/fast")
    public ActionResult addFastAiSystemConfig(@RequestBody AIConfigCreateRequest request) {
        PermissionUtils.checkDeskTopOrAdmin();

        String sqlSource = request.getAiSqlSource();
        if (StringUtils.isBlank(sqlSource)) {
            sqlSource = AiSqlSourceEnum.OPENAI.getCode();
        }
        
        String prefix = "ai.fast.";
        
        saveConfig(prefix + "source", sqlSource);
        saveConfig(prefix + "apiKey", request.getApiKey());
        saveConfig(prefix + "apiHost", request.getApiHost());
        saveConfig(prefix + "model", request.getModel());
        saveConfig(prefix + "temperature", request.getTemperature());
        saveConfig(prefix + "maxTokens", request.getMaxTokens());

        return ActionResult.isSuccess();
    }
    
    @GetMapping("/system_config/ai/fast")
    public DataResult<AIConfig> getFastAiSystemConfig(String aiSqlSource) {
        AIConfig config = new AIConfig();
        
        // 获取快速模型配置的 AI 来源
        DataResult<Config> sourceConfig = configService.find("ai.fast.source");
        if (sourceConfig.getData() != null && StringUtils.isNotBlank(sourceConfig.getData().getContent())) {
            aiSqlSource = sourceConfig.getData().getContent();
        }
        
        if (StringUtils.isBlank(aiSqlSource)) {
            aiSqlSource = AiSqlSourceEnum.OPENAI.getCode();
        }
        
        config.setAiSqlSource(aiSqlSource);
        String prefix = "ai.fast.";
        
        config.setApiKey(getConfigValue(prefix + "apiKey"));
        config.setApiHost(getConfigValue(prefix + "apiHost"));
        config.setModel(getConfigValue(prefix + "model"));
        config.setTemperature(getConfigValue(prefix + "temperature"));
        config.setMaxTokens(getConfigValue(prefix + "maxTokens"));

        return DataResult.of(config);
    }
    
    private String getConfigValue(String code) {
        DataResult<Config> result = configService.find(code);
        if (result.getData() != null && StringUtils.isNotBlank(result.getData().getContent())) {
            return result.getData().getContent();
        }
        return "";
    }

    @GetMapping("/model_service/list")
    public DataResult<List<ModelServiceResponse>> getModelServiceList() {
        return DataResult.of(readModelServices());
    }

    @PostMapping("/model_service/upsert")
    public ActionResult upsertModelService(@RequestBody ModelServiceUpsertRequest request) {
        PermissionUtils.checkDeskTopOrAdmin();
        if (StringUtils.isBlank(request.getProvider())) {
            return ActionResult.fail("INVALID_PARAM", "provider is required", "provider is required");
        }

        List<ModelServiceResponse> services = readModelServices();
        ModelServiceResponse target = null;
        if (StringUtils.isNotBlank(request.getId())) {
            for (ModelServiceResponse service : services) {
                if (StringUtils.equals(service.getId(), request.getId())) {
                    target = service;
                    break;
                }
            }
        }

        if (target == null) {
            target = new ModelServiceResponse();
            target.setId(UUID.randomUUID().toString());
            services.add(target);
        }

        target.setName(StringUtils.defaultIfBlank(request.getName(), request.getProvider() + " Service"));
        target.setProvider(request.getProvider());
        target.setApiKey(StringUtils.defaultString(request.getApiKey()));
        target.setApiHost(StringUtils.defaultString(request.getApiHost()));
        target.setHttpProxyHost(StringUtils.defaultString(request.getHttpProxyHost()));
        target.setHttpProxyPort(StringUtils.defaultString(request.getHttpProxyPort()));
        target.setOrganizationId(StringUtils.defaultString(request.getOrganizationId()));
        target.setProjectId(StringUtils.defaultString(request.getProjectId()));
        List<ModelItemRequest> modelList = request.getModelList() == null ? new ArrayList<>() : request.getModelList();
        for (ModelItemRequest modelItem : modelList) {
            if (StringUtils.isBlank(modelItem.getId())) {
                modelItem.setId(UUID.randomUUID().toString());
            }
        }
        target.setModelList(modelList);

        saveSystemConfig(MODEL_SERVICE_CONFIG_CODE, JSON.toJSONString(services));
        return ActionResult.isSuccess();
    }

    @PostMapping("/model_service/delete")
    public ActionResult deleteModelService(@RequestBody ModelServiceDeleteRequest request) {
        PermissionUtils.checkDeskTopOrAdmin();
        if (StringUtils.isBlank(request.getId())) {
            return ActionResult.fail("INVALID_PARAM", "id is required", "id is required");
        }
        List<ModelServiceResponse> services = readModelServices();
        services.removeIf(service -> StringUtils.equals(service.getId(), request.getId()));
        saveSystemConfig(MODEL_SERVICE_CONFIG_CODE, JSON.toJSONString(services));
        return ActionResult.isSuccess();
    }

    @PostMapping("/model_service/test")
    public ActionResult testModelService(@RequestBody ModelServiceTestRequest request) {
        PermissionUtils.checkDeskTopOrAdmin();
        if (StringUtils.isBlank(request.getProvider()) || StringUtils.isBlank(request.getApiHost())
                || StringUtils.isBlank(request.getApiKey())) {
            return ActionResult.fail("INVALID_PARAM", "provider/apiHost/apiKey is required",
                    "provider/apiHost/apiKey is required");
        }
        if (request.getModelList() == null || request.getModelList().isEmpty()
                || StringUtils.isBlank(request.getModelList().get(0).getModel())) {
            return ActionResult.fail("INVALID_PARAM", "at least one model is required", "at least one model is required");
        }
        String provider = request.getProvider().toUpperCase();
        String model = request.getModelList().get(0).getModel();
        try {
            aiChatConfig.testModelService(provider, request.getApiHost(), request.getApiKey(), model);
            return ActionResult.isSuccess();
        } catch (Exception e) {
            return ActionResult.fail("MODEL_SERVICE_TEST_FAILED", e.getMessage(), e.getMessage());
        }
    }

    @GetMapping("/model/default")
    public DataResult<DefaultModelConfigResponse> getDefaultModelConfig() {
        String content = getConfigValue(MODEL_DEFAULT_CONFIG_CODE);
        if (StringUtils.isBlank(content)) {
            return DataResult.of(new DefaultModelConfigResponse());
        }
        return DataResult.of(JSON.parseObject(content, DefaultModelConfigResponse.class));
    }

    @PostMapping("/model/default")
    public ActionResult setDefaultModelConfig(@RequestBody DefaultModelConfigRequest request) {
        PermissionUtils.checkDeskTopOrAdmin();
        if (StringUtils.isBlank(request.getDefaultModelId())) {
            return ActionResult.fail("INVALID_PARAM", "defaultModelId is required", "defaultModelId is required");
        }
        DefaultModelConfigResponse response = new DefaultModelConfigResponse();
        response.setDefaultModelId(request.getDefaultModelId());
        response.setFastModelId(StringUtils.defaultString(request.getFastModelId()));
        saveSystemConfig(MODEL_DEFAULT_CONFIG_CODE, JSON.toJSONString(response));
        return ActionResult.isSuccess();
    }

    private List<ModelServiceResponse> readModelServices() {
        String content = getConfigValue(MODEL_SERVICE_CONFIG_CODE);
        if (StringUtils.isBlank(content)) {
            return new ArrayList<>();
        }
        List<ModelServiceResponse> services = JSON.parseObject(content, new TypeReference<List<ModelServiceResponse>>() {
        });
        return services == null ? new ArrayList<>() : services;
    }

    private void saveSystemConfig(String code, String content) {
        SystemConfigParam param = SystemConfigParam.builder().code(code).content(content).build();
        configService.createOrUpdate(param);
    }

}
