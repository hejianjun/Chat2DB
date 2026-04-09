package ai.chat2db.server.web.api.controller.ai.statemachine;

/**
 * 聊天状态机事件枚举
 * 定义了AI对话过程中可能发生的各种事件
 */
public enum ChatEvent {
    /** 请求将自然语言转换为SQL */
    REQUEST_NL_TO_SQL,
    /** 请求解释SQL */
    REQUEST_EXPLAIN_SQL,
    /** 请求优化SQL */
    REQUEST_OPTIMIZE_SQL,
    /** 请求转换SQL */
    REQUEST_CONVERT_SQL,
    /** 请求文本生成 */
    REQUEST_TEXT_GENERATION,
    /** 请求生成标题 */
    REQUEST_GENERATE_TITLE,
    /** 请求猜测注释 */
    REQUEST_GUESS_COMMENT,
    /** 表已提供 */
    TABLES_PROVIDED,
    /** 表未提供 */
    TABLES_NOT_PROVIDED,
    /** 自动选择完成 */
    AUTO_SELECT_DONE,
    /** Schema已获取 */
    SCHEMA_FETCHED,
    /** Prompt已构建 */
    PROMPT_BUILT,
    /** 流式响应完成 */
    STREAM_FINISHED,
    /** 自动选择失败 */
    AUTO_SELECT_FAILED,
    /** 获取Schema失败 */
    FETCH_SCHEMA_FAILED,
    /** Prompt构建失败 */
    PROMPT_BUILD_FAILED,
    /** AI调用失败 */
    AI_CALL_FAILED,
    /** 取消操作 */
    CANCEL
}