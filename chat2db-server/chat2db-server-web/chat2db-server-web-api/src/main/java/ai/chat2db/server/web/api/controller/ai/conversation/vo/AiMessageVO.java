package ai.chat2db.server.web.api.controller.ai.conversation.vo;

import lombok.Data;

@Data
public class AiMessageVO {

    private String messageId;
    private String role;
    private String content;
    private Integer sequenceNo;
}
