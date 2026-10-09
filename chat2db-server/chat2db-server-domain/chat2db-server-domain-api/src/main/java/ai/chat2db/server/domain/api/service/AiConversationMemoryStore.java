package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.AiMessage;

import java.util.List;

/**
 * Persistent message memory used by AI conversations.
 */
public interface AiConversationMemoryStore {

    List<AiMessage> get(String conversationId);

    void clear(String conversationId);

    void discardIncompleteTurn(String conversationId);

    void rewindLastTurn(String conversationId, String expectedContent, boolean completedTurn);
}
