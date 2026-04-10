package ai.chat2db.server.web.api.controller.ai.prompt;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 提示词构建上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptContext {

    /**
     * 提示词类型
     */
    private PromptType promptType;

    /**
     * 输入消息
     */
    private String message;

    /**
     * 扩展信息（额外要求/限制）
     */
    private String ext;

    /**
     * Schema DDL
     */
    private String schemaDdl;

    /**
     * 数据源类型
     */
    private String dataSourceType;

    /**
     * 目标 SQL 类型（用于 SQL 转换）
     */
    private String targetSqlType;

    /**
     * 是否用于表选择（如果是，需要附加表选择指令）
     */
    @Builder.Default
    private Boolean forTableSelection = false;
}
