package ai.chat2db.server.web.api.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI 聊天客户端配置类
 * 用于创建和配置不同 AI 提供商（OpenAI、Anthropic 等）的聊天客户端
 */
@Service
public class AiChatConfig {
    private static final String MODEL_SERVICE_CONFIG_CODE = "ai.model.services";
    private static final String MODEL_DEFAULT_CONFIG_CODE = "ai.model.default";

    @Autowired
    private ConfigService configService;

    /**
     * 从配置服务中获取配置值
     *
     * @param code 配置项编码
     * @param defaultValue 默认值，当配置不存在时返回
     * @return 配置值或默认值
     */
    private String getConfigValue(String code, String defaultValue) {
        DataResult<Config> result = configService.find(code);
        if (result.getData() != null && result.getData().getContent() != null
                && !result.getData().getContent().isEmpty()) {
            return result.getData().getContent();
        }
        return defaultValue;
    }

    /**
     * 根据任务类型创建聊天客户端实例
     * 对于简单任务（如选表），如果配置了快速模型则使用快速模型
     *
     * @param promptType 提示类型
     * @return ChatClient 聊天客户端实例
     */
    public ChatClient createChatClient(PromptType promptType) {
        ModelRuntimeConfig runtimeConfig = loadModelRuntimeConfig();
        if (runtimeConfig != null && runtimeConfig.defaultModel != null && runtimeConfig.defaultService != null) {
            boolean useFastModel = promptType != null && promptType.isSimpleTask()
                    && runtimeConfig.fastModel != null && runtimeConfig.fastService != null;
            if (useFastModel) {
                return createChatClientByModel(runtimeConfig.fastService, runtimeConfig.fastModel, true);
            }
            return createChatClientByModel(runtimeConfig.defaultService, runtimeConfig.defaultModel, false);
        }

        return createDefaultChatClient();
    }
    
    /**
     * 检查是否配置了快速模型
     * @return true 如果快速模型已配置
     */
    private boolean isFastModelConfigured() {
        String fastApiKey = getConfigValue("ai.fast.apiKey", "");
        return fastApiKey != null && !fastApiKey.isEmpty();
    }

    /**
     * 创建快速聊天客户端实例
     * 使用专门为简单任务配置的快速模型（如 gpt-4o-mini 等轻量级模型）
     *
     * @return ChatClient 聊天客户端实例
     */
    public ChatClient createFastChatClient() {
        ModelRuntimeConfig runtimeConfig = loadModelRuntimeConfig();
        if (runtimeConfig != null && runtimeConfig.fastModel != null && runtimeConfig.fastService != null) {
            return createChatClientByModel(runtimeConfig.fastService, runtimeConfig.fastModel, true);
        }
        // 获取快速模型的 AI 来源配置，默认为 OPENAI
        String aiSqlSource = getConfigValue("ai.fast.source", AiSqlSourceEnum.OPENAI.getCode());
        AiSqlSourceEnum aiSqlSourceEnum = AiSqlSourceEnum.getByName(aiSqlSource);
        if (aiSqlSourceEnum == null) {
            aiSqlSourceEnum = AiSqlSourceEnum.OPENAI;
        }

        // 构建配置前缀
        String prefix = "ai.fast.";

        switch (aiSqlSourceEnum) {
            case ANTHROPIC:
                return createAnthropicFastChatClient(prefix);
            case OPENAI:
            default:
                return createOpenAiFastChatClient(prefix);
        }
    }

    /**
     * 创建 OpenAI 快速聊天客户端
     *
     * @param prefix 配置项前缀
     * @return ChatClient OpenAI 聊天客户端实例
     */
    private ChatClient createOpenAiFastChatClient(String prefix) {
        // 从配置中获取 OpenAI 相关参数
        String apiKey = getConfigValue(prefix + "apiKey", "");
        String apiHost = getConfigValue(prefix + "apiHost", "https://api.openai.com/");
        String model = getConfigValue(prefix + "model", "gpt-4o-mini");
        String temperatureStr = getConfigValue(prefix + "temperature", "0.5");
        String maxTokensStr = getConfigValue(prefix + "maxTokens", "1024");

        // 确保 API 主机 URL 以 "/" 结尾
        if (apiHost != null && !apiHost.endsWith("/")) {
            apiHost = apiHost + "/";
        }

        // 构建 OpenAI API 实例
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(apiHost)
                .apiKey(apiKey)
                .build();

        // 构建聊天选项，快速任务使用较低的温度和较少的 token
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(Double.parseDouble(temperatureStr))
                .maxTokens(Integer.parseInt(maxTokensStr))
                .build();

        // 构建 OpenAI 聊天模型
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        // 创建并返回聊天客户端
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建 Anthropic 快速聊天客户端
     *
     * @param prefix 配置项前缀
     * @return ChatClient Anthropic 聊天客户端实例
     */
    private ChatClient createAnthropicFastChatClient(String prefix) {
        // 从配置中获取 Anthropic 相关参数
        String apiKey = getConfigValue(prefix + "apiKey", "");
        String model = getConfigValue(prefix + "model", "claude-3-haiku-20240307");
        String temperatureStr = getConfigValue(prefix + "temperature", "0.5");
        String maxTokensStr = getConfigValue(prefix + "maxTokens", "1024");


        // 构建 Anthropic API 实例
        AnthropicApi anthropicApi = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();

        // 构建聊天选项，快速任务使用较低的温度和较少的 token
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model)
                .temperature(Double.parseDouble(temperatureStr))
                .maxTokens(Integer.parseInt(maxTokensStr))
                .build();

        // 构建 Anthropic 聊天模型
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(options)
                .build();

        // 创建并返回聊天客户端
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建默认聊天客户端实例
     * 根据配置的 AI 来源（如 OPENAI、ANTHROPIC）创建对应的聊天客户端
     *
     * @return ChatClient 聊天客户端实例
     */
    private ChatClient createDefaultChatClient() {
        ModelRuntimeConfig runtimeConfig = loadModelRuntimeConfig();
        if (runtimeConfig != null && runtimeConfig.defaultModel != null && runtimeConfig.defaultService != null) {
            return createChatClientByModel(runtimeConfig.defaultService, runtimeConfig.defaultModel, false);
        }
        // 获取 AI 来源配置，默认为 OPENAI
        String aiSqlSource = getConfigValue("ai.sql.source", AiSqlSourceEnum.OPENAI.getCode());
        AiSqlSourceEnum aiSqlSourceEnum = AiSqlSourceEnum.getByName(aiSqlSource);
        if (aiSqlSourceEnum == null) {
            aiSqlSourceEnum = AiSqlSourceEnum.OPENAI;
        }

        // 构建配置前缀，如 "ai.openai." 或 "ai.anthropic."
        String prefix = "ai." + aiSqlSource.toLowerCase() + ".";

        switch (aiSqlSourceEnum) {
            case ANTHROPIC:
                return createAnthropicChatClient(prefix);
            case OPENAI:
            default:
                return createOpenAiChatClient(prefix);
        }
    }

    /**
     * 创建 OpenAI 聊天客户端
     *
     * @param prefix 配置项前缀
     * @return ChatClient OpenAI 聊天客户端实例
     */
    private ChatClient createOpenAiChatClient(String prefix) {
        // 从配置中获取 OpenAI 相关参数，均使用指定的默认值
        String apiKey = getConfigValue(prefix + "apiKey", "");
        String apiHost = getConfigValue(prefix + "apiHost", "https://api.openai.com/");
        String model = getConfigValue(prefix + "model", "gpt-4o-mini");
        String temperatureStr = getConfigValue(prefix + "temperature", "0.7");
        String maxTokensStr = getConfigValue(prefix + "maxTokens", "4096");

        // 确保 API 主机 URL 以 "/" 结尾
        if (apiHost != null && !apiHost.endsWith("/")) {
            apiHost = apiHost + "/";
        }

        // 构建 OpenAI API 实例
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(apiHost)
                .apiKey(apiKey)
                .build();

        // 构建聊天选项，包括模型、温度和最大 token 数
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(Double.parseDouble(temperatureStr))
                .maxTokens(Integer.parseInt(maxTokensStr))
                .build();

        // 构建 OpenAI 聊天模型
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        // 创建并返回聊天客户端
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建 Anthropic 聊天客户端
     *
     * @param prefix 配置项前缀
     * @return ChatClient Anthropic 聊天客户端实例
     */
    private ChatClient createAnthropicChatClient(String prefix) {
        // 从配置中获取 Anthropic 相关参数，均使用指定的默认值
        String apiKey = getConfigValue(prefix + "apiKey", "");
        String model = getConfigValue(prefix + "model", "claude-3-5-sonnet-20241022");
        String temperatureStr = getConfigValue(prefix + "temperature", "0.7");
        String maxTokensStr = getConfigValue(prefix + "maxTokens", "4096");


        // 构建 Anthropic API 实例
        AnthropicApi anthropicApi = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();

        // 构建聊天选项，包括模型、温度和最大 token 数
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model)
                .temperature(Double.parseDouble(temperatureStr))
                .maxTokens(Integer.parseInt(maxTokensStr))
                .build();

        // 构建 Anthropic 聊天模型
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(options)
                .build();

        // 创建并返回聊天客户端
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient createChatClientByModel(ModelServiceConfig service, ModelItem modelItem, boolean fastMode) {
        AiSqlSourceEnum aiSqlSourceEnum = AiSqlSourceEnum.getByName(service.getProvider());
        if (aiSqlSourceEnum == null) {
            aiSqlSourceEnum = AiSqlSourceEnum.OPENAI;
        }

        if (aiSqlSourceEnum == AiSqlSourceEnum.ANTHROPIC) {
            AnthropicApi anthropicApi = AnthropicApi.builder()
                    .apiKey(service.getApiKey())
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
            return ChatClient.builder(chatModel).build();
        }

        String apiHost = service.getApiHost();
        if (apiHost != null && !apiHost.endsWith("/")) {
            apiHost = apiHost + "/";
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(apiHost)
                .apiKey(service.getApiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelItem.getModel())
                .temperature(fastMode ? 0.5 : 0.7)
                .maxTokens(fastMode ? 1024 : 4096)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        return ChatClient.builder(chatModel).build();
    }

    public ChatResponse testModelService(String provider, String apiHost, String apiKey, String model) {
        ModelServiceConfig service = new ModelServiceConfig();
        service.setProvider(provider);
        service.setApiHost(apiHost);
        service.setApiKey(apiKey);
        ModelItem modelItem = new ModelItem();
        modelItem.setModel(model);
        return createChatClientByModel(service, modelItem, true)
                .prompt("ping")
                .call()
                .chatResponse();
    }

    private ModelRuntimeConfig loadModelRuntimeConfig() {
        String serviceJson = getConfigValue(MODEL_SERVICE_CONFIG_CODE, "");
        String defaultJson = getConfigValue(MODEL_DEFAULT_CONFIG_CODE, "");
        if (serviceJson.isEmpty() || defaultJson.isEmpty()) {
            return null;
        }
        List<ModelServiceConfig> serviceList = JSON.parseObject(serviceJson, new TypeReference<List<ModelServiceConfig>>() {});
        DefaultModelConfig defaultModelConfig = JSON.parseObject(defaultJson, DefaultModelConfig.class);
        if (serviceList == null || serviceList.isEmpty() || defaultModelConfig == null || defaultModelConfig.getDefaultModelId() == null) {
            return null;
        }

        ModelLocateResult defaultResult = locateModel(serviceList, defaultModelConfig.getDefaultModelId());
        ModelLocateResult fastResult = null;
        if (defaultModelConfig.getFastModelId() != null && !defaultModelConfig.getFastModelId().isEmpty()) {
            fastResult = locateModel(serviceList, defaultModelConfig.getFastModelId());
        }
        if (defaultResult == null) {
            return null;
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

    @Data
    private static class DefaultModelConfig {
        private String defaultModelId;
        private String fastModelId;
    }

    @Data
    private static class ModelItem {
        private String id;
        private String name;
        private String model;
    }

    @Data
    private static class ModelServiceConfig {
        private String id;
        private String name;
        private String provider;
        private String apiKey;
        private String apiHost;
        private List<ModelItem> modelList = new ArrayList<>();
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
