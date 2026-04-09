package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import org.apache.commons.lang3.StringUtils;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import ai.chat2db.server.tools.common.util.EasyEnumUtils;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BuildPromptAction extends BaseChatAction {

    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        if (ctx.isCancelled()) {
            return;
        }

        try {
            sendStateEvent(ctx.getSseEmitter(), ChatState.BUILDING_PROMPT, "正在构建提示...");

            ChatQueryRequest request = ctx.getRequest();
            String schemaDdl = ctx.getSchemaDdl();
            String prompt = StringUtils.defaultString(request.getMessage(), "");
            String ext = StringUtils.isNotBlank(request.getExt()) ? request.getExt() : "";

            String promptType = StringUtils.isBlank(request.getPromptType())
                ? PromptType.NL_2_SQL.getCode()
                : request.getPromptType();
            PromptType pType = EasyEnumUtils.getEnum(PromptType.class, promptType);

            String dataSourceType = "MYSQL";
            if (request.getDataSourceId() != null && ctx.getSchemaDdl() != null) {
                dataSourceType = guessDataSourceType(schemaDdl);
            }

            String builtPrompt;
            if (PromptType.TEXT_GENERATION.getCode().equals(request.getPromptType())) {
                builtPrompt = prompt;
            } else {
                builtPrompt = buildPromptWithSchema(prompt, ext, pType, dataSourceType, schemaDdl, request);
            }

            builtPrompt = builtPrompt.replaceAll("[\r\t]", "").replaceAll("#", "");

            ctx.setBuiltPrompt(builtPrompt);

            context.getStateMachine().sendEvent(ChatEvent.PROMPT_BUILT);
        } catch (Exception e) {
            log.error("Build prompt failed", e);
            sendError(ctx.getSseEmitter(), "构建提示失败: " + e.getMessage());
            context.getStateMachine().sendEvent(ChatEvent.PROMPT_BUILD_FAILED);
        }
    }

    private String buildPromptWithSchema(String prompt, String ext, PromptType pType,
            String dataSourceType, String schemaDdl, ChatQueryRequest request) {
        String schemaProperty;
        if (StringUtils.isNotEmpty(schemaDdl)) {
            schemaProperty = String.format(
                "### 请根据以下table properties和SQL input%s. %s\n#\n### %s SQL tables, with their properties:\n#\n# "
                    + "%s\n#\n#\n### SQL input: %s",
                pType.getDescription(), ext, dataSourceType, schemaDdl, prompt);
        } else {
            schemaProperty = String.format("### 请根据以下SQL input%s. %s\n#\n### SQL input: %s",
                pType.getDescription(), ext, prompt);
        }

        if (pType == PromptType.SQL_2_SQL) {
            String destSqlType = StringUtils.isNotBlank(request.getDestSqlType())
                ? request.getDestSqlType()
                : dataSourceType;
            schemaProperty = String.format("%s\n#\n### 目标SQL类型: %s", schemaProperty, destSqlType);
        }

        return schemaProperty;
    }

    private String guessDataSourceType(String schemaDdl) {
        if (schemaDdl == null) return "MYSQL";
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