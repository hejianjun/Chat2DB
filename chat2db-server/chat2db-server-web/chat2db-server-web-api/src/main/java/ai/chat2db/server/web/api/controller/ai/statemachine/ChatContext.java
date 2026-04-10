package ai.chat2db.server.web.api.controller.ai.statemachine;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.spi.sql.ConnectInfo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatContext {
    private String sessionId;
    private ChatQueryRequest request;
    private SseEmitter sseEmitter;
    private String uid;
    private ChatClient chatClient;
    private String builtPrompt;
    private List<String> selectedTables;
    private String schemaDdl;
    private volatile boolean cancelled;
    private LoginUser loginUser;
    private ConnectInfo connectInfo;
}