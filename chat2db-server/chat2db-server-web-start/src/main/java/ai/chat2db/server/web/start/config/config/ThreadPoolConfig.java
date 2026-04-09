package ai.chat2db.server.web.start.config.config;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "thread-pool")
public class ThreadPoolConfig {
    int coreSize;
    int maxSize;
    int keepAlive;
    int queueCapacity;

    @Bean("indexUpdateExecutor")
    public ExecutorService indexUpdateExecutor(){
        return new ThreadPoolExecutor(
            coreSize,
            maxSize,
            keepAlive, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            new ThreadFactoryBuilder()
                .setNameFormat("index-updater-%d")
                .setUncaughtExceptionHandler((t, e) -> 
                    log.error("Thread {} failed", t.getName(), e))
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
}