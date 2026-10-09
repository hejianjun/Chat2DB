package ai.chat2db.server.web.api.config;

import ai.chat2db.server.tools.base.excption.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiConversationMemoryStoreTest {
    private final InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
    private final SpringAiConversationMemoryStore store = new SpringAiConversationMemoryStore(repository);

    @Test
    void removesOnlyNewestTurnAndPreservesEarlierContext() {
        repository.saveAll("chat", List.of(new SystemMessage("system"), new UserMessage("first"),
            new AssistantMessage("first answer"), new UserMessage("last"), new AssistantMessage("old answer")));

        store.rewindLastTurn("chat", "last", true);

        assertEquals(3, repository.findByConversationId("chat").size());
        assertEquals("system", repository.findByConversationId("chat").get(0).getText());
        assertEquals(List.of("first", "first answer"), store.get("chat").stream().map(m -> m.getContent()).toList());
    }

    @Test
    void canEditFirstTurnWithoutLeavingAnOrphanReply() {
        repository.saveAll("chat", List.of(new UserMessage("first"), new AssistantMessage("answer")));
        store.rewindLastTurn("chat", "first", true);
        assertTrue(store.get("chat").isEmpty());
    }

    @Test
    void rejectsStaleEditsWithoutChangingMemory() {
        repository.saveAll("chat", List.of(new UserMessage("new question"), new AssistantMessage("answer")));
        assertThrows(BusinessException.class, () -> store.rewindLastTurn("chat", "old question", true));
        assertEquals(2, store.get("chat").size());
    }

    @Test
    void rejectsMissingHistory() {
        assertThrows(BusinessException.class, () -> store.rewindLastTurn("missing", "question", true));
    }

    @Test
    void editingFailedQuestionPreservesLastCompletedTurn() {
        repository.saveAll("chat", List.of(new UserMessage("same question"), new AssistantMessage("answer")));
        store.rewindLastTurn("chat", "same question", false);
        assertEquals(2, store.get("chat").size());
        store.rewindLastTurn("missing", "failed question", false);
        assertTrue(store.get("missing").isEmpty());
    }

    @Test
    void removesMatchingIncompleteQuestionBeforeResending() {
        repository.saveAll("chat", List.of(new UserMessage("first"), new AssistantMessage("answer"),
            new UserMessage("failed question")));
        store.rewindLastTurn("chat", "failed question", false);
        assertEquals(2, store.get("chat").size());
        assertEquals("answer", store.get("chat").get(1).getContent());
    }
}
