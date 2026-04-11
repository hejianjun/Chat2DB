package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.alibaba.fastjson2.JSONObject;

import ai.chat2db.server.web.api.controller.ai.prompt.PromptValidator;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * AI 流式输出动作
 */
@Component
@Slf4j
public class StreamAction extends BaseChatAction {

    @Autowired
    private PromptValidator promptValidator;

    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        log.info("[StreamAction] execute called");
        ChatContext ctx = getChatContext(context);
        log.info("[StreamAction] uid: {}, cancelled: {}", ctx.getUid(), ctx.isCancelled());
        if (ctx.isCancelled()) {
            log.info("[StreamAction] cancelled, returning");
            return;
        }

        String prompt = ctx.getBuiltPrompt();
        log.info("[StreamAction] Prompt length: {}", prompt != null ? prompt.length() : 0);

        if (!promptValidator.isValidLength(prompt)) {
            log.warn("[StreamAction] Prompt exceeds max length for uid: {}", ctx.getUid());
            sendError(ctx.getSseEmitter(), "提示语超出最大长度");
            context.getStateMachine().sendEvent(
                    MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
            );
            return;
        }

        try {
            sendStateEvent(ctx.getSseEmitter(), ChatState.STREAMING, "AI 正在生成...");
            log.info("[StreamAction] Starting AI stream for uid: {}", ctx.getUid());

            Flux<ChatResponse> flux = ctx.getChatClient().prompt()
                    .user(prompt)
                    .stream()
                    .chatResponse();
            flux.publishOn(Schedulers.boundedElastic())
                    .doOnNext(chatResponse -> {
                        if (ctx.isCancelled()) {
                            log.info("[StreamAction] Stream cancelled by user for uid: {}", ctx.getUid());
                            throw new RuntimeException("Cancelled by user");
                        }
                        
                        JSONObject data = new JSONObject();
                        Generation generation = chatResponse.getResult();
                        
                        if (generation != null) {
                            String content = generation.getOutput().getText();
                            String thinking = null;
                            
                            if (generation.getMetadata() != null) {
                                var metadata = generation.getMetadata();
                                if (metadata.containsKey("thinking")) {
                                    Object thinkingObj = metadata.get("thinking");
                                    if (thinkingObj instanceof String) {
                                        thinking = (String) thinkingObj;
                                    } else if (thinkingObj != null) {
                                        thinking = thinkingObj.toString();
                                    }
                                }
                            }
                            
                            if (content != null && !content.isEmpty()) {
                                data.put("content", content);
                            }
                            if (thinking != null && !thinking.isEmpty()) {
                                data.put("thinking", thinking);
                                log.debug("[StreamAction] Thinking content: {}", thinking);
                            }
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
                    })
                    .doOnError(error -> {
                        log.error("[StreamAction] Stream error for uid: {}", ctx.getUid(), error);
                        if (!ctx.isCancelled()) {
                            sendError(ctx.getSseEmitter(), "AI 调用失败：" + error.getMessage());
                        }
                        try {
                            ctx.getSseEmitter().completeWithError(error);
                        } catch (Exception ignored) {
                        }
                        context.getStateMachine().sendEvent(
                                MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
                        );
                    })
                    .doOnComplete(() -> {
                        log.info("[StreamAction] Stream completed for uid: {}", ctx.getUid());
                        try {
                            ctx.getSseEmitter().send(SseEmitter.event()
                                    .id("[DONE]")
                                    .data("[DONE]")
                                    .reconnectTime(3000));
                        } catch (IOException ignored) {
                        } finally {
                            ctx.getSseEmitter().complete();
                        }
                        context.getStateMachine().sendEvent(
                                MessageBuilder.withPayload(ChatEvent.STREAM_FINISHED).build()
                        );
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("[StreamAction] Start streaming failed for uid: {}", ctx.getUid(), e);
            CompletableFuture.runAsync(() -> {
                sendError(ctx.getSseEmitter(), "启动 AI 流式调用失败：" + e.getMessage());
                context.getStateMachine().sendEvent(
                        MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
                );
            });
        }
    }
}
