package ai.chat2db.server.web.api.config;

import ai.chat2db.server.domain.repository.Dbutils;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Binds Spring AI chat memory to Chat2DB's embedded database instead of Boot's fallback in-memory datasource.
 */
@Configuration
public class ChatMemoryRepositoryConfig {

    @Bean
    @Primary
    public ChatMemoryRepository chatMemoryRepository() {
        return JdbcChatMemoryRepository.builder()
                .dataSource(Dbutils.getDataSource())
                .build();
    }
}
