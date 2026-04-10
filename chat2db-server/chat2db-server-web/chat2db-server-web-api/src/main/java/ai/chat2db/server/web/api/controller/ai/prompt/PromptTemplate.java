package ai.chat2db.server.web.api.controller.ai.prompt;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import lombok.Builder;
import lombok.Data;

/**
 * 提示词模板值对象
 */
@Data
@Builder
public class PromptTemplate {

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板内容
     */
    private String template;

    /**
     * 提示词类型
     */
    private PromptType promptType;

    /**
     * 模板描述
     */
    private String description;
}
