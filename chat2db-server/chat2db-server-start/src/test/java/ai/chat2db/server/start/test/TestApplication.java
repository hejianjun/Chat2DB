package ai.chat2db.server.start.test;

import ai.chat2db.server.start.Chat2dbLiteApplication;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Indexed;

/**
 * 本地环境的启动。
 * 主要为了读取本地的一些配置 比如日志输出就和其他环境不一样
 *
 * @author 是仪
 */
@SpringBootTest(classes = {Chat2dbLiteApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
@Indexed
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(Chat2dbLiteApplication.class, args);
    }
}
