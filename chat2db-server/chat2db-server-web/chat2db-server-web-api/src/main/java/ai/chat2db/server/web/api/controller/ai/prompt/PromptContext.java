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
     * SQL执行计划结果 (EXPLAIN)
     */
    private String explainPlan;

    /**
     * 数据源类型
     */
    private String dataSourceType;

    /**
     * 目标 SQL 类型（用于 SQL 转换）
     */
    private String targetSqlType;

    /**
     * 源文件字段列表（用于字段映射推荐）
     */
    private String sourceFields;

    /**
     * Redis key 前缀与注释上下文
     */
    private String redisKeyPrefixContext;

    /**
     * 前端会话 ID，用于同一 AI 面板内的连续对话
     */
    private String conversationId;

    /**
     * 选表阶段只读的最近对话；正式生成阶段由 ChatMemory 提供历史。
     */
    private String history;

    /**
     * 选表阶段参考的最近 SQL/MQL。
     */
    private String previousSql;

    /**
     * Legacy field retained only for binary compatibility.
     */
    @Deprecated
    private Boolean isRevision;

}
