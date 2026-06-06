package ai.chat2db.spi.redis;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface RedisCommandMonitor {

    void monitor(String databaseName, Consumer<String> lineConsumer, BooleanSupplier running);
}
