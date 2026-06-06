package ai.chat2db.server.test.redis;

import ai.chat2db.plugin.redis.RedisCommandParser;
import ai.chat2db.plugin.redis.RedisConnectionProvider;
import ai.chat2db.spi.sql.ConnectInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class RedisNativeClientTest {

    @Test
    void parseRedisUrlWithDatabase() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl("redis://localhost:6380/2");

        RedisConnectionProvider.RedisConnectionInfo connectionInfo = RedisConnectionProvider.parse(connectInfo);

        Assertions.assertEquals("localhost", connectionInfo.host());
        Assertions.assertEquals(6380, connectionInfo.port());
        Assertions.assertEquals(2, connectionInfo.database());
    }

    @Test
    void parseRedisUrlRejectsJdbcScheme() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl("jdbc:redis://localhost:6379/0");

        Assertions.assertThrows(IllegalArgumentException.class, () -> RedisConnectionProvider.parse(connectInfo));
    }

    @Test
    void parseCommandKeepsQuotedValues() {
        List<String> statements = RedisCommandParser.splitStatements("set key \"hello world\"; get key");

        Assertions.assertEquals(2, statements.size());
        Assertions.assertEquals(List.of("set", "key", "hello world"),
                RedisCommandParser.tokenize(statements.get(0)));
        Assertions.assertEquals(List.of("get", "key"), RedisCommandParser.tokenize(statements.get(1)));
    }
}
