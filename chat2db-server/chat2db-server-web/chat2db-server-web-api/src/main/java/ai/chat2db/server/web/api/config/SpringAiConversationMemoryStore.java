package ai.chat2db.server.web.api.config;

import ai.chat2db.server.domain.api.model.AiMessage;
import ai.chat2db.server.domain.api.service.AiConversationMemoryStore;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adapts Spring AI messages to the stable conversation DTO consumed by the existing conversation APIs.
 */
@Component
public class SpringAiConversationMemoryStore implements AiConversationMemoryStore {

    private final ChatMemoryRepository repository;

    public SpringAiConversationMemoryStore(ChatMemoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AiMessage> get(String conversationId) {
        List<Message> stored = repository.findByConversationId(conversationId);
        List<AiMessage> result = new ArrayList<>();
        for (Message message : stored) {
            if (message.getMessageType() != MessageType.USER
                    && message.getMessageType() != MessageType.ASSISTANT) {
                continue;
            }
            AiMessage item = new AiMessage();
            item.setSequenceNo(result.size());
            item.setRole(message.getMessageType() == MessageType.USER ? "user" : "assistant");
            item.setContent(message.getText());
            String key = conversationId + ":" + item.getSequenceNo() + ":" + item.getRole() + ":" + item.getContent();
            item.setMessageId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
            result.add(item);
        }
        return result;
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    @Override
    public void discardIncompleteTurn(String conversationId) {
        List<Message> messages = new ArrayList<>(repository.findByConversationId(conversationId));
        // The advisor stores the user message before streaming starts; remove it when no assistant reply completed.
        if (!messages.isEmpty() && messages.get(messages.size() - 1).getMessageType() == MessageType.USER) {
            messages.remove(messages.size() - 1);
            repository.saveAll(conversationId, messages);
        }
    }
}
