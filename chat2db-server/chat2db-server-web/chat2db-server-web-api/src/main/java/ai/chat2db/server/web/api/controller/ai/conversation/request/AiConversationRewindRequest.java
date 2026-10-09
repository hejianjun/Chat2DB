package ai.chat2db.server.web.api.controller.ai.conversation.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiConversationRewindRequest {
    @NotBlank
    private String expectedContent;

    private boolean completedTurn = true;
}
