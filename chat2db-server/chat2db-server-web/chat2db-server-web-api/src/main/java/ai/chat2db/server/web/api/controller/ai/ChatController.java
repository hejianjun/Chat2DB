package ai.chat2db.server.web.api.controller.ai;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.config.AiChatConfig;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@RestController
@ConnectionInfoAspect
@RequestMapping("/api/ai")
@Slf4j
public class ChatController {

    @Autowired
    private StateMachineFactory<ChatState, ChatEvent> stateMachineFactory;

    @Autowired
    private AiChatConfig aiChatConfig;

    private static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();

    private final ConcurrentHashMap<String, StateMachine<ChatState, ChatEvent>> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatContext> activeContexts = new ConcurrentHashMap<>();

    @GetMapping("/chat")
    @CrossOrigin
    public SseEmitter chat(ChatQueryRequest queryRequest, @RequestHeader Map<String, String> headers)
            throws IOException {
        String uid = headers.get("uid");
        if (StrUtil.isBlank(uid)) {
            throw new ParamBusinessException("uid");
        }

        String sessionId = UUID.randomUUID().toString();
        SseEmitter sseEmitter = new SseEmitter(CHAT_TIMEOUT);

        ChatClient chatClient = aiChatConfig.createChatClient();

        ChatContext ctx = ChatContext.builder()
            .sessionId(sessionId)
            .request(queryRequest)
            .sseEmitter(sseEmitter)
            .uid(uid)
            .chatClient(chatClient)
            .cancelled(false)
            .build();

        setupSseCallbacks(sseEmitter, sessionId, ctx);

        sseEmitter.send(SseEmitter.event()
            .id(uid)
            .name("connect")
            .data(LocalDateTime.now().toString())
            .reconnectTime(3000));

        LoginUser loginUser = ContextUtils.getLoginUser();
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();

        StateMachine<ChatState, ChatEvent> stateMachine = stateMachineFactory.getStateMachine(sessionId);
        stateMachine.getExtendedState().getVariables().put("chatContext", ctx);

        activeSessions.put(sessionId, stateMachine);
        activeContexts.put(sessionId, ctx);

        CompletableFuture.runAsync(() -> {
            buildContext(loginUser, connectInfo);
            stateMachine.start();
            ChatEvent initialEvent = determineInitialEvent(queryRequest);
            stateMachine.sendEvent(MessageBuilder.withPayload(initialEvent).build());
        }).whenComplete((aVoid, throwable) -> {
            removeContext();
        });

        return sseEmitter;
    }

    private void buildContext(LoginUser loginUser, ConnectInfo connectInfo) {
        ContextUtils.setContext(Context.builder()
                .loginUser(loginUser)
                .build());
        Dbutils.setSession();
        Chat2DBContext.putContext(connectInfo);
    }

    private void removeContext() {
        Dbutils.removeSession();
        ContextUtils.removeContext();
        Chat2DBContext.removeContext();
    }

    @DeleteMapping("/chat/{sessionId}")
    @CrossOrigin
    public ResponseEntity<Void> cancelChat(@PathVariable String sessionId) {
        ChatContext ctx = activeContexts.get(sessionId);
        if (ctx != null) {
            ctx.setCancelled(true);
            try {
                ctx.getSseEmitter().complete();
            } catch (Exception ignored) {
            }
        }

        StateMachine<ChatState, ChatEvent> sm = activeSessions.get(sessionId);
        if (sm != null) {
            sm.sendEvent(MessageBuilder.withPayload(ChatEvent.CANCEL).build());
        }

        cleanupSession(sessionId);
        return ResponseEntity.ok().build();
    }

    private ChatEvent determineInitialEvent(ChatQueryRequest request) {
        boolean hasTables = CollectionUtils.isNotEmpty(request.getTableNames());
        boolean skipAutoSelect = PromptType.NL_2_COMMENT.getCode().equals(request.getPromptType())
            || PromptType.TITLE_GENERATION.getCode().equals(request.getPromptType())
            || PromptType.TEXT_GENERATION.getCode().equals(request.getPromptType());

        return (hasTables || skipAutoSelect) ? ChatEvent.TABLES_PROVIDED : ChatEvent.TABLES_NOT_PROVIDED;
    }

    private void setupSseCallbacks(SseEmitter sseEmitter, String sessionId, ChatContext ctx) {
        sseEmitter.onCompletion(() -> {
            log.info("SSE completed for session: {}", sessionId);
            cleanupSession(sessionId);
        });

        sseEmitter.onTimeout(() -> {
            log.info("SSE timeout for session: {}", sessionId);
            ctx.setCancelled(true);
            cleanupSession(sessionId);
        });

        sseEmitter.onError(throwable -> {
            log.error("SSE error for session: {}, error: {}", sessionId, throwable.getMessage());
            ctx.setCancelled(true);
            cleanupSession(sessionId);
        });
    }

    private void cleanupSession(String sessionId) {
        activeSessions.remove(sessionId);
        activeContexts.remove(sessionId);
    }
}
