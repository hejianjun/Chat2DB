package ai.chat2db.server.web.api.config;

import ai.chat2db.server.domain.api.model.AiMessage;
import ai.chat2db.server.domain.api.service.AiConversationMemoryStore;
import ai.chat2db.server.tools.base.excption.BusinessException;
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

    @Override
    public void rewindLastTurn(String conversationId, String expectedContent, boolean completedTurn) {
        List<Message> messages = new ArrayList<>(repository.findByConversationId(conversationId));
        // 失败问题可能已被流式错误处理清理，不能回退之前成功的问答。
        if (!completedTurn && (messages.isEmpty()
                || messages.get(messages.size() - 1).getMessageType() != MessageType.USER)) {
            return;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getMessageType() != MessageType.USER) {
                continue;
            }
            // 校验最后一个问题，避免旧页面误删其他轮次的对话。
            if (!java.util.Objects.equals(expectedContent, messages.get(i).getText())) {
                throw new BusinessException("ai.conversationMessageChanged");
            }
            repository.saveAll(conversationId, new ArrayList<>(messages.subList(0, i)));
            return;
        }
        throw new BusinessException("ai.conversationMessageChanged");
    }
}
