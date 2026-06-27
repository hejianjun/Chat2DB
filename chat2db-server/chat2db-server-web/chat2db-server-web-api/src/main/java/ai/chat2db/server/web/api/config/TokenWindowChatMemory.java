package ai.chat2db.server.web.api.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository-backed chat memory bounded by an approximate token budget.
 */
public class TokenWindowChatMemory implements ChatMemory {

    private final ChatMemoryRepository repository;
    private final int tokenBudget;

    public TokenWindowChatMemory(ChatMemoryRepository repository, int tokenBudget) {
        this.repository = repository;
        this.tokenBudget = tokenBudget;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> all = new ArrayList<>(repository.findByConversationId(conversationId));
        all.addAll(messages);
        repository.saveAll(conversationId, trimToBudget(all));
    }

    @Override
    public List<Message> get(String conversationId) {
        return trimToBudget(repository.findByConversationId(conversationId));
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    List<Message> trimToBudget(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        // Select complete turns from newest to oldest so trimming never leaves an orphan assistant response.
        List<List<Message>> turns = splitIntoTurns(messages);
        List<Message> selected = new ArrayList<>();
        int usedTokens = 0;
        for (int i = turns.size() - 1; i >= 0; i--) {
            List<Message> turn = turns.get(i);
            int turnTokens = turn.stream().mapToInt(this::estimateTokens).sum();
            if (turnTokens > tokenBudget && selected.isEmpty()) {
                continue;
            }
            if (usedTokens + turnTokens > tokenBudget) {
                break;
            }
            selected.addAll(0, turn);
            usedTokens += turnTokens;
        }
        return selected;
    }

    private List<List<Message>> splitIntoTurns(List<Message> messages) {
        List<List<Message>> turns = new ArrayList<>();
        List<Message> current = null;
        for (Message message : messages) {
            if (message.getMessageType() == MessageType.SYSTEM) {
                continue;
            }
            if (message.getMessageType() == MessageType.USER || current == null) {
                current = new ArrayList<>();
                turns.add(current);
            }
            current.add(message);
        }
        return turns;
    }

    private int estimateTokens(Message message) {
        String text = message.getText();
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int asciiCharacters = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) <= 0x7f) {
                asciiCharacters++;
            }
        }
        int nonAsciiCharacters = text.length() - asciiCharacters;
        return Math.max(1, (asciiCharacters + 3) / 4 + nonAsciiCharacters);
    }
}
