package ai.chat2db.server.domain.api.model;

import lombok.Data;

/**
 * AI 消息 DTO
 */
@Data
public class AiMessage {

    private String messageId;
    private String role;
    private String content;
    private Integer sequenceNo;
}
