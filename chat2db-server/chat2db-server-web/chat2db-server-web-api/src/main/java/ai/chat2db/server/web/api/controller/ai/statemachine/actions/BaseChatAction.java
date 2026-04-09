package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.io.IOException;

import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.alibaba.fastjson2.JSONObject;

import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseChatAction implements Action<ChatState, ChatEvent> {

    protected ChatContext getChatContext(StateContext<ChatState, ChatEvent> context) {
        return (ChatContext) context.getExtendedState().getVariables().get("chatContext");
    }

    protected void sendStateEvent(SseEmitter emitter, ChatState state, String message) {
        try {
            JSONObject data = new JSONObject();
            data.put("state", state.name());
            data.put("message", message);
            emitter.send(SseEmitter.event()
                .name("state")
                .data(data.toJSONString()));
        } catch (IOException e) {
            log.error("Failed to send state event", e);
        }
    }

    protected void sendError(SseEmitter emitter, String errorMessage) {
        try {
            JSONObject data = new JSONObject();
            data.put("error", errorMessage);
            emitter.send(SseEmitter.event()
                .name("error")
                .data(data.toJSONString()));
        } catch (IOException e) {
            log.error("Failed to send error event", e);
        }
    }

    protected void sendTablesSelected(SseEmitter emitter, java.util.List<String> tables) {
        try {
            JSONObject data = new JSONObject();
            data.put("tables", tables);
            emitter.send(SseEmitter.event()
                .name("tables_selected")
                .data(data.toJSONString()));
        } catch (IOException e) {
            log.error("Failed to send tables_selected event", e);
        }
    }

    protected void sendSchemaFetched(SseEmitter emitter, String ddl) {
        try {
            JSONObject data = new JSONObject();
            data.put("ddl", ddl);
            emitter.send(SseEmitter.event()
                .name("schema_fetched")
                .data(data.toJSONString()));
        } catch (IOException e) {
            log.error("Failed to send schema_fetched event", e);
        }
    }
}