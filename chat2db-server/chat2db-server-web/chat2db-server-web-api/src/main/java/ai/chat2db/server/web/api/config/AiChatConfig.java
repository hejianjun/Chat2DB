package ai.chat2db.server.web.api.config;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.google.common.collect.ImmutableMap;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AiChatConfig {
    private static final String MODEL_SERVICE_CONFIG_CODE = "ai.model.services";
    private static final String MODEL_DEFAULT_CONFIG_CODE = "ai.model.default";
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final Pattern VERSION_PATH_PATTERN = Pattern.compile(".*/v\\d+(?:\\.\\d+)?/?$");
    private static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 32768;
    private static final double HISTORY_BUDGET_RATIO = 0.25;

    @Autowired
    private ConfigService configService;

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    public ChatClient createChatClient(PromptType promptType) {
        ModelRuntimeConfig runtimeConfig = loadModelRuntimeConfig();
        boolean useFastModel = promptType != null && promptType.isSimpleTask()
                && runtimeConfig.fastModel != null && runtimeConfig.fastService != null;
        if (useFastModel) {
            return createChatClientByModel(runtimeConfig.fastService, runtimeConfig.fastModel, true, false);
        }
        boolean enableMemory = promptType == null || !promptType.isSimpleTask();
        return createChatClientByModel(runtimeConfig.defaultService, runtimeConfig.defaultModel, false, enableMemory);
    }

    public ChatClient createFastChatClient() {
        ModelRuntimeConfig runtimeConfig = loadModelRuntimeConfig();
        if (runtimeConfig.fastModel != null && runtimeConfig.fastService != null) {
            return createChatClientByModel(runtimeConfig.fastService, runtimeConfig.fastModel, true, false);
        }
        return createChatClientByModel(runtimeConfig.defaultService, runtimeConfig.defaultModel, false, false);
    }

    public ChatResponse testModelService(String provider, String apiHost, String completionsPath, String apiKey,
            String model) {
        ModelServiceConfig service = new ModelServiceConfig();
        service.setProvider(provider);
        service.setApiHost(apiHost);
        service.setCompletionsPath(completionsPath);
        service.setApiKey(apiKey);
        ModelItem modelItem = new ModelItem();
        modelItem.setModel(model);
        return createChatClientByModel(service, modelItem, true, false)
                .prompt("ping")
                .call()
                .chatResponse();
    }

    private ChatClient createChatClientByModel(ModelServiceConfig service,
            ModelItem modelItem,
            boolean fastMode,
            boolean enableMemory) {
        AiSqlSourceEnum aiSqlSourceEnum = AiSqlSourceEnum.getByName(service.getProvider());
        if (aiSqlSourceEnum == null) {
            aiSqlSourceEnum = AiSqlSourceEnum.OPENAI;
        }

        if (aiSqlSourceEnum == AiSqlSourceEnum.ANTHROPIC) {
            AnthropicApi anthropicApi = AnthropicApi.builder()
                    .apiKey(service.getApiKey())
                    .webClientBuilder(createLoggingWebClientBuilder())
                    .build();
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .model(modelItem.getModel())
                    .temperature(fastMode ? 0.5 : 0.7)
                    .maxTokens(fastMode ? 1024 : 4096)
                    .build();
            AnthropicChatModel chatModel = AnthropicChatModel.builder()
                    .anthropicApi(anthropicApi)
                    .defaultOptions(options)
                    .build();
            return buildChatClient(chatModel, modelItem, enableMemory);
        }

        OpenAiEndpoint endpoint = resolveOpenAiEndpoint(service.getApiHost(), service.getCompletionsPath());
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(endpoint.baseUrl)
                .completionsPath(endpoint.completionsPath)
                .apiKey(service.getApiKey())
                .webClientBuilder(createLoggingWebClientBuilder())
                .build();
        Map<String, Object> extraBody = new HashMap<>();
        if (StringUtils.contains(modelItem.getModel(), "qwen")) {
            extraBody.put("enable_thinking", !fastMode);
        } else if (StringUtils.contains(modelItem.getModel(), "hy")) {
            extraBody.put("reasoning", ImmutableMap.of("enable", !fastMode));
        } else if (fastMode) {
            extraBody.put("thinking", ImmutableMap.of("type", "disabled"));
            extraBody.put("reasoning_effort", "none");
        }
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelItem.getModel())
                .temperature(fastMode ? 0.5 : 0.7)
                .maxTokens(fastMode ? 1024 : 4096)
                .extraBody(extraBody)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        return buildChatClient(chatModel, modelItem, enableMemory);
    }

    private ChatClient buildChatClient(org.springframework.ai.chat.model.ChatModel chatModel,
            ModelItem modelItem,
            boolean enableMemory) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (enableMemory) {
            // Reserve most of the model context for schema DDL, the current request, and
            // the generated answer.
            int contextWindowTokens = modelItem.getContextWindowTokens() == null
                    || modelItem.getContextWindowTokens() <= 0
                            ? DEFAULT_CONTEXT_WINDOW_TOKENS
                            : modelItem.getContextWindowTokens();
            int historyBudget = Math.max(1, (int) (contextWindowTokens * HISTORY_BUDGET_RATIO));
            TokenWindowChatMemory chatMemory = new TokenWindowChatMemory(chatMemoryRepository, historyBudget);
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        return builder.build();
    }

    private WebClient.Builder createLoggingWebClientBuilder() {
        return WebClient.builder()
                .clientConnector(new LlmHttpLoggingConnector(new ReactorClientHttpConnector()));
    }

    private ModelRuntimeConfig loadModelRuntimeConfig() {
        String serviceJson = getConfigValue(MODEL_SERVICE_CONFIG_CODE);
        String defaultJson = getConfigValue(MODEL_DEFAULT_CONFIG_CODE);
        if (serviceJson.isEmpty() || defaultJson.isEmpty()) {
            throw new IllegalStateException("Model service or default model config is empty.");
        }

        List<ModelServiceConfig> serviceList = JSON.parseObject(serviceJson, new TypeReference<>() {
        });
        DefaultModelConfig defaultModelConfig = JSON.parseObject(defaultJson, DefaultModelConfig.class);
        if (serviceList == null || serviceList.isEmpty() || defaultModelConfig == null
                || defaultModelConfig.getDefaultModelId() == null || defaultModelConfig.getDefaultModelId().isEmpty()) {
            throw new IllegalStateException("Default model config is invalid.");
        }

        ModelLocateResult defaultResult = locateModel(serviceList, defaultModelConfig.getDefaultModelId());
        if (defaultResult == null) {
            throw new IllegalStateException("Default model not found in model services.");
        }

        ModelLocateResult fastResult = null;
        if (defaultModelConfig.getFastModelId() != null && !defaultModelConfig.getFastModelId().isEmpty()) {
            fastResult = locateModel(serviceList, defaultModelConfig.getFastModelId());
        }

        ModelRuntimeConfig runtimeConfig = new ModelRuntimeConfig();
        runtimeConfig.defaultService = defaultResult.service;
        runtimeConfig.defaultModel = defaultResult.model;
        if (fastResult != null) {
            runtimeConfig.fastService = fastResult.service;
            runtimeConfig.fastModel = fastResult.model;
        }
        return runtimeConfig;
    }

    private ModelLocateResult locateModel(List<ModelServiceConfig> serviceList, String modelId) {
        for (ModelServiceConfig service : serviceList) {
            if (service.getModelList() == null) {
                continue;
            }
            for (ModelItem model : service.getModelList()) {
                if (Objects.equals(model.getId(), modelId)) {
                    ModelLocateResult result = new ModelLocateResult();
                    result.service = service;
                    result.model = model;
                    return result;
                }
            }
        }
        return null;
    }

    private String getConfigValue(String code) {
        Config result = configService.find(code);
        if (result != null && result.getContent() != null
                && !result.getContent().isEmpty()) {
            return result.getContent();
        }
        return "";
    }

    private OpenAiEndpoint resolveOpenAiEndpoint(String apiHost, String completionsPath) {
        OpenAiEndpoint endpoint = new OpenAiEndpoint();
        endpoint.baseUrl = StringUtils.removeEnd(StringUtils.defaultIfBlank(apiHost, DEFAULT_OPENAI_BASE_URL), "/");
        endpoint.completionsPath = StringUtils.defaultIfBlank(completionsPath, DEFAULT_COMPLETIONS_PATH);

        if (StringUtils.isNotBlank(completionsPath)) {
            endpoint.completionsPath = normalizePath(completionsPath);
            URI uri = URI.create(endpoint.baseUrl);
            String path = StringUtils.removeEnd(StringUtils.defaultString(uri.getPath()), "/");
            if (StringUtils.endsWith(path, "/chat/completions") || StringUtils.startsWith(endpoint.completionsPath,
                    path + "/") || StringUtils.equals(endpoint.completionsPath, path)) {
                endpoint.baseUrl = buildOrigin(uri);
            }
            return endpoint;
        }

        URI uri = URI.create(endpoint.baseUrl);
        String path = StringUtils.removeEnd(StringUtils.defaultString(uri.getPath()), "/");
        if (StringUtils.endsWith(path, "/chat/completions")) {
            endpoint.baseUrl = buildOrigin(uri);
            endpoint.completionsPath = path;
        } else if (StringUtils.isNotBlank(path) && VERSION_PATH_PATTERN.matcher(path).matches()) {
            endpoint.baseUrl = buildOrigin(uri);
            endpoint.completionsPath = path + "/chat/completions";
        }
        return endpoint;
    }

    private String normalizePath(String path) {
        return StringUtils.prependIfMissing(StringUtils.removeEnd(path, "/"), "/");
    }

    private String buildOrigin(URI uri) {
        StringBuilder origin = new StringBuilder();
        origin.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() > -1) {
            origin.append(":").append(uri.getPort());
        }
        return origin.toString();
    }

    @Data
    private static class DefaultModelConfig {
        private String defaultModelId;
        private String fastModelId;
    }

    @Data
    private static class ModelItem {
        private String id;
        private String model;
        private Integer contextWindowTokens;
    }

    @Data
    private static class ModelServiceConfig {
        private String provider;
        private String apiKey;
        private String apiHost;
        private String completionsPath;
        private List<ModelItem> modelList = new ArrayList<>();
    }

    private static class OpenAiEndpoint {
        private String baseUrl;
        private String completionsPath;
    }

    private static class ModelLocateResult {
        private ModelServiceConfig service;
        private ModelItem model;
    }

    private static class ModelRuntimeConfig {
        private ModelServiceConfig defaultService;
        private ModelItem defaultModel;
        private ModelServiceConfig fastService;
        private ModelItem fastModel;
    }
}
