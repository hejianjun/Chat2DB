package ai.chat2db.server.web.api.controller.ai.prompt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 提示词模板配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "prompts")
public class PromptTemplateProperties {

    /**
     * 模板映射：key 为模板类型代码，value 为模板配置
     */
    private Map<String, PromptTemplateConfig> templates;

    @Data
    public static class PromptTemplateConfig {
        /**
         * 模板名称
         */
        private String name;

        /**
         * 模板描述
         */
        private String description;

        /**
         * 模板内容
         */
        private String template;
    }
}