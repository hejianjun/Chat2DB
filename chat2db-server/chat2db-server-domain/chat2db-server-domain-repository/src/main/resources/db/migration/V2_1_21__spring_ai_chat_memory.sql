DELETE FROM `ai_conversation`;
DROP TABLE IF EXISTS `ai_message`;

CREATE TABLE IF NOT EXISTS `SPRING_AI_CHAT_MEMORY` (
    `conversation_id` varchar(64) NOT NULL,
    `content` longtext NOT NULL,
    `type` varchar(10) NOT NULL,
    `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX` (`conversation_id`, `timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Spring AI chat memory';
