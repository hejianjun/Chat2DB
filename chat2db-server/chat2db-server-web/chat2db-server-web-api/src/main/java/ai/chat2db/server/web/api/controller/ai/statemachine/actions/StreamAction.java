package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import ai.chat2db.server.domain.api.service.AiConversationMemoryStore;
import ai.chat2db.server.domain.api.service.AiConversationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.alibaba.fastjson2.JSONObject;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptValidator;
import ai.chat2db.server.web.api.controller.ai.request.ChatImageRequest;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * AI 流式输出动作
 */
@Component
@Slf4j
public class StreamAction extends BaseChatAction {

    @Autowired
    private PromptValidator promptValidator;

    @Autowired
    private AiConversationService aiConversationService;

    @Autowired
    private AiConversationMemoryStore memoryStore;

    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        restoreTrace(ctx);
        if (ctx.isCancelled()) {
            log.info("[StreamAction] Skip cancelled request, uid: {}", ctx.getUid());
            removeContext();
            return;
        }

        String prompt = ctx.getBuiltPrompt();

        if (!promptValidator.isValidLength(prompt)) {
            log.warn("[StreamAction] Prompt exceeds max length, uid: {}, promptLength: {}",
                    ctx.getUid(), prompt == null ? 0 : prompt.length());
            sendError(ctx.getSseEmitter(), "提示语超出最大长度");
            propagateTrace(context.getStateMachine().sendEvent(
                    Mono.just(MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build())
            ), ctx).subscribe();
            removeContext();
            return;
        }

        try {
            sendStateEvent(ctx.getSseEmitter(), ChatState.STREAMING, "AI 正在生成...");
            log.info("[StreamAction] Starting AI stream, uid: {}, promptLength: {}",
                    ctx.getUid(), prompt == null ? 0 : prompt.length());

            ChatClient.ChatClientRequestSpec requestSpec = ctx.getChatClient().prompt().system(prompt);
            List<Media> media = buildMedia(ctx.getRequest().getImages());
            String userText = ctx.getRequest().getMessage();
            if (media.isEmpty()) {
                requestSpec = requestSpec.user(userText);
            } else {
                String multimodalText = userText == null || userText.isBlank() ? "请分析这些图片" : userText;
                requestSpec = requestSpec.user(user -> user.text(multimodalText).media(media.toArray(new Media[0])));
            }

            Flux<ChatResponse> flux = requestSpec
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID, ctx.getRequest().getConversationId()))
                    .stream()
                    .chatResponse();
            propagateTrace(flux.publishOn(Schedulers.boundedElastic()), ctx)
                    .doOnNext(chatResponse -> processChatResponse(ctx, chatResponse))
                    .doOnError(error -> handleStreamError(ctx, error, context))
                    .doOnComplete(() -> handleStreamComplete(ctx, context))
                    .subscribe();

        } catch (Exception e) {
            log.error("[StreamAction] Start streaming failed for uid: {}", ctx.getUid(), e);
            sendError(ctx.getSseEmitter(), "启动 AI 流式调用失败：" + e.getMessage());
            propagateTrace(context.getStateMachine().sendEvent(
                    Mono.just(MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build())
            ), ctx).subscribe();
        } finally {
            removeContext();
        }
    }

    /**
     * 处理每个聊天响应
     */
    private void processChatResponse(ChatContext ctx, ChatResponse chatResponse) {
        if (ctx.isCancelled()) {
            log.info("[StreamAction] Stream cancelled by user for uid: {}", ctx.getUid());
            throw new RuntimeException("Cancelled by user");
        }

        JSONObject data = new JSONObject();
        Generation generation = chatResponse.getResult();

        String content = generation.getOutput().getText();
        if (content != null && !content.isEmpty()) {
            data.put("content", content);
            appendContent(ctx, content);
        }

        if (!data.isEmpty()) {
            try {
                ctx.getSseEmitter().send(SseEmitter.event()
                        .id(ctx.getUid())
                        .name("message")
                        .data(data.toJSONString())
                        .reconnectTime(3000));
            } catch (IOException e) {
                log.error("[StreamAction] Failed to send SSE message for uid: {}", ctx.getUid(), e);
                throw new RuntimeException(e);
            }
        }
    }

    private synchronized void appendContent(ChatContext ctx, String content) {
        String current = ctx.getCurrentContent();
        ctx.setCurrentContent(current == null ? content : current + content);
    }

    private List<Media> buildMedia(List<ChatImageRequest> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .map(image -> Media.builder()
                        .mimeType(MimeTypeUtils.parseMimeType(image.getMimeType()))
                        .data(new ByteArrayResource(Base64.getDecoder().decode(image.getData())))
                        .name(image.getName())
                        .build())
                .toList();
    }

    /**
     * 处理流式错误
     */
    private void handleStreamError(ChatContext ctx, Throwable error, StateContext<ChatState, ChatEvent> context) {
        log.error("[StreamAction] Stream error for uid: {}", ctx.getUid(), error);
        memoryStore.discardIncompleteTurn(ctx.getRequest().getConversationId());
        if (!ctx.isCancelled()) {
            sendError(ctx.getSseEmitter(), "AI 调用失败：" + error.getMessage());
        }
        try {
            ctx.getSseEmitter().completeWithError(error);
        } catch (Exception ignored) {
            // 忽略完成时的异常
        }
        context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
        );
    }

    /**
     * 处理流式完成
     */
    private void handleStreamComplete(ChatContext ctx, StateContext<ChatState, ChatEvent> context) {
        log.info("[StreamAction] Stream completed for uid: {}", ctx.getUid());
        try {
            persistTurn(ctx);
            ctx.getSseEmitter().send(SseEmitter.event()
                    .id("[DONE]")
                    .data("[DONE]")
                    .reconnectTime(3000));
        } catch (IOException ignored) {
            // 忽略发送完成消息时的异常
        } finally {
            ctx.getSseEmitter().complete();
        }
        context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.STREAM_FINISHED).build()
        );
    }

    private void persistTurn(ChatContext ctx) {
        ChatQueryRequest request = ctx.getRequest();
        if (request == null) {
            return;
        }
        String conversationId = request.getConversationId();
        String assistantContent = ctx.getCurrentContent();
        if (conversationId == null || conversationId.isEmpty()
                || assistantContent == null || assistantContent.isEmpty()) {
            return;
        }
        if (isSimpleTask(request.getPromptType())) {
            log.debug("[StreamAction] Skip persist for transient prompt, uid: {}", ctx.getUid());
            return;
        }
        buildContext(ctx);
        try {
            aiConversationService.updateAfterMessageTurn(conversationId, assistantContent);
        } catch (Exception e) {
            log.error("[StreamAction] Failed to persist AI conversation turn for uid: {}", ctx.getUid(), e);
        } finally {
            removeContext();
        }
    }

    private boolean isSimpleTask(String promptType) {
        if (promptType == null || promptType.isEmpty()) {
            return false;
        }
        try {
            return PromptType.valueOf(promptType).isSimpleTask();
        } catch (IllegalArgumentException e) {
            log.warn("[StreamAction] Invalid promptType: {}", promptType);
            return false;
        }
    }
}
