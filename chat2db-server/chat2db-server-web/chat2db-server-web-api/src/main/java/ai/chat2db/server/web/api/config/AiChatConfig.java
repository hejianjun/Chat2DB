package ai.chat2db.server.web.api.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
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

/**
 * AI 聊天客户端配置类
 * 用于创建和配置不同 AI 提供商（OpenAI、Anthropic 等）的聊天客户端
 */
@Service
public class AiChatConfig {

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
     * 创建聊天客户端实例
     * 根据配置的 AI 来源（如 OPENAI、ANTHROPIC）创建对应的聊天客户端
     *
     * @return ChatClient 聊天客户端实例
     */
    public ChatClient createChatClient() {
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
}
