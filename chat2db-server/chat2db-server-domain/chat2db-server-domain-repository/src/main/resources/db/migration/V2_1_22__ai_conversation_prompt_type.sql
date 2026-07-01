ALTER TABLE `ai_conversation`
    ADD COLUMN `prompt_type` varchar(32) DEFAULT NULL COMMENT '会话使用的 PromptType';
