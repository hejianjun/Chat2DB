package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptBuilder;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptContext;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;

/**
 * 构建提示词动作
 */
@Component
@Slf4j
public class BuildPromptAction extends BaseChatAction {

    @Autowired
    private PromptBuilder promptBuilder;

    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext chatContext = getChatContext(context);
        if (chatContext.isCancelled()) {
            return;
        }

        try {
            sendStateEvent(chatContext.getSseEmitter(),
                    ChatState.BUILDING_PROMPT, "正在构建提示...");

            ChatQueryRequest request = chatContext.getRequest();
            String schemaDdl = chatContext.getSchemaDdl();

            PromptType promptType = determinePromptType(request);

            PromptContext promptContext = PromptContext.builder()
                    .promptType(promptType)
                    .message(request.getMessage())
                    .ext(request.getExt())
                    .schemaDdl(schemaDdl)
                    .dataSourceType(guessDataSourceType(schemaDdl))
                    .targetSqlType(request.getDestSqlType())
                    .forTableSelection(false)
                    .build();

            String builtPrompt = promptBuilder.context(promptContext).build();
            chatContext.setBuiltPrompt(builtPrompt);

            context.getStateMachine().sendEvent(
                    MessageBuilder.withPayload(ChatEvent.PROMPT_BUILT).build()
            );

        } catch (Exception e) {
            log.error("Build prompt failed", e);
            sendError(getChatContext(context).getSseEmitter(),
                    "构建提示失败：" + e.getMessage());
            context.getStateMachine().sendEvent(
                    MessageBuilder.withPayload(ChatEvent.PROMPT_BUILD_FAILED).build()
            );
        }
    }

    private PromptType determinePromptType(ChatQueryRequest request) {
        String promptType = StringUtils.isBlank(request.getPromptType())
                ? PromptType.NL_2_SQL.getCode()
                : request.getPromptType();
        return PromptType.valueOf(promptType);
    }

    private String guessDataSourceType(String schemaDdl) {
        if (StringUtils.isEmpty(schemaDdl)) {
            return "MYSQL";
        }
        String upper = schemaDdl.toUpperCase();
        if (upper.contains("MYSQL") || upper.contains("AUTO_INCREMENT")) {
            return "MYSQL";
        } else if (upper.contains("POSTGRES") || upper.contains("SERIAL")) {
            return "POSTGRESQL";
        } else if (upper.contains("ORACLE") || upper.contains("NUMBER(")) {
            return "ORACLE";
        }
        return "MYSQL";
    }
}
