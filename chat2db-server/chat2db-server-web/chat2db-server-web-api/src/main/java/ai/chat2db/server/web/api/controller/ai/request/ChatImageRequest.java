package ai.chat2db.server.web.api.controller.ai.request;

import lombok.Data;

/**
 * AI 聊天图片输入。
 */
@Data
public class ChatImageRequest {

    /**
     * 图片名称，仅用于模型侧标识。
     */
    private String name;

    /**
     * 图片 MIME 类型。
     */
    private String mimeType;

    /**
     * 不包含 data URL 前缀的 Base64 图片数据。
     */
    private String data;
}
