package ai.chat2db.server.web.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenWindowChatMemoryTest {

    @Test
    void keepsOnlyCompleteNewestTurnsWithinBudget() {
        TokenWindowChatMemory memory = new TokenWindowChatMemory(new InMemoryChatMemoryRepository(), 3);
        List<Message> result = memory.trimToBudget(List.of(
                new UserMessage("12345678"),
                new AssistantMessage("12345678"),
                new UserMessage("abcd"),
                new AssistantMessage("efgh")
        ));

        assertEquals(2, result.size());
        assertEquals("abcd", result.get(0).getText());
        assertEquals("efgh", result.get(1).getText());
    }

    @Test
    void skipsNewestTurnWhenItAloneExceedsBudget() {
        TokenWindowChatMemory memory = new TokenWindowChatMemory(new InMemoryChatMemoryRepository(), 2);
        List<Message> result = memory.trimToBudget(List.of(
                new UserMessage("this message is much too large"),
                new AssistantMessage("also too large")
        ));

        assertTrue(result.isEmpty());
    }

    @Test
    void persistsMessagesThroughRepository() {
        InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        TokenWindowChatMemory memory = new TokenWindowChatMemory(repository, 100);
        memory.add("conversation", List.of(new UserMessage("hello"), new AssistantMessage("world")));

        assertEquals(2, memory.get("conversation").size());
    }
}
