package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.io.IOException;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.alibaba.fastjson2.JSONObject;
import com.unfbx.chatgpt.entity.chat.Message;

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
        ChatContext ctx = getChatContext(context);
        if (ctx.isCancelled()) {
            return;
        }

        String prompt = ctx.getBuiltPrompt();

        // 使用 PromptValidator 验证长度
        if (!promptValidator.isValidLength(prompt)) {
            sendError(ctx.getSseEmitter(), "提示语超出最大长度");
            context.getStateMachine().sendEvent(
                    MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
            ).subscribe();
            return;
        }

        try {
            sendStateEvent(ctx.getSseEmitter(), ChatState.STREAMING, "AI 正在生成...");

            Flux<String> flux = ctx.getChatClient().prompt()
                    .user(prompt)
                    .stream()
                    .content();

            flux.publishOn(Schedulers.boundedElastic())
                    .filter(Objects::nonNull)
                    .doOnNext(content -> {
                        if (ctx.isCancelled()) {
                            throw new RuntimeException("Cancelled by user");
                        }
                        try {
                            Message message = Message.builder()
                                    .content(content)
                                    .role(Message.Role.ASSISTANT)
                                    .build();
                            JSONObject data = new JSONObject();
                            data.put("content", content);
                            ctx.getSseEmitter().send(SseEmitter.event()
                                    .id(ctx.getUid())
                                    .name("message")
                                    .data(data.toJSONString())
                                    .reconnectTime(3000));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnError(error -> {
                        log.error("Stream error", error);
                        if (!ctx.isCancelled()) {
                            sendError(ctx.getSseEmitter(), "AI 调用失败：" + error.getMessage());
                        }
                        try {
                            ctx.getSseEmitter().completeWithError(error);
                        } catch (Exception ignored) {
                        }
                        context.getStateMachine().sendEvent(
                                MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
                        ).subscribe();
                    })
                    .doOnComplete(() -> {
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
                        ).subscribe();
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Start streaming failed", e);
            sendError(ctx.getSseEmitter(), "启动 AI 流式调用失败：" + e.getMessage());
            context.getStateMachine().sendEvent(
                    MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
            ).subscribe();
        }
    }
}
