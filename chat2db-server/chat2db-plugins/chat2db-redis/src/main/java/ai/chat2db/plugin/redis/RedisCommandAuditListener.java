package ai.chat2db.plugin.redis;

import ai.chat2db.spi.redis.RedisCommandAuditEvent;
import ai.chat2db.spi.redis.RedisCommandAuditRegistry;
import ai.chat2db.spi.sql.ConnectInfo;
import io.lettuce.core.event.command.CommandFailedEvent;
import io.lettuce.core.event.command.CommandListener;
import io.lettuce.core.event.command.CommandStartedEvent;
import io.lettuce.core.event.command.CommandSucceededEvent;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.RedisCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisCommandAuditListener implements CommandListener {

    private static final String STARTED_AT_KEY = "chat2db.redis.audit.startedAt";
    private static final String SOURCE = "lettuce-command-listener";
    private static final Set<String> WRITE_COMMANDS = Set.of(
            "SET", "DEL", "UNLINK", "EXPIRE", "PEXPIRE", "EXPIREAT", "PEXPIREAT", "PERSIST", "RENAME",
            "RENAMENX", "HSET", "HDEL", "LPUSH", "RPUSH", "LPOP", "RPOP", "SADD", "SREM", "ZADD", "ZREM",
            "INCR", "DECR", "INCRBY", "DECRBY", "APPEND", "SETEX", "PSETEX"
    );

    private final ConnectInfo connectInfo;

    public RedisCommandAuditListener(ConnectInfo connectInfo) {
        // Lettuce may publish command callbacks after the web request thread has finished.
        this.connectInfo = connectInfo == null ? null : connectInfo.copy();
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        try {
            event.getContext().put(STARTED_AT_KEY, System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("Redis command audit start context write failed", e);
        }
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        publish(event.getCommand(), "success", event.getDuration(TimeUnit.MILLISECONDS), null);
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        publish(event.getCommand(), "fail", duration(event.getContext()), event.getCause());
    }

    public static boolean shouldAudit(String commandName) {
        return WRITE_COMMANDS.contains(StringUtils.upperCase(commandName));
    }

    static String getCommandName(RedisCommand<Object, Object, Object> command) {
        if (command == null || command.getType() == null) {
            return "";
        }
        return command.getType().name().toUpperCase(Locale.ROOT);
    }

    static String toCommandLine(RedisCommand<Object, Object, Object> command) {
        if (command == null) {
            return "";
        }
        ByteBuf buffer = Unpooled.buffer();
        try {
            command.encode(buffer);
            String resp = buffer.toString(StandardCharsets.UTF_8);
            List<String> args = RespCommandLineParser.parse(resp);
            if (args.isEmpty()) {
                return getCommandName(command);
            }
            return String.join(" ", args.stream().map(RedisCommandAuditListener::quoteArgument).toList());
        } catch (Exception e) {
            log.debug("Redis command audit encode failed", e);
            return getCommandName(command);
        } finally {
            buffer.release();
        }
    }

    private void publish(RedisCommand<Object, Object, Object> command, String status, long duration,
                         Throwable throwable) {
        try {
            String commandName = getCommandName(command);
            if (!shouldAudit(commandName)) {
                return;
            }
            // The audit command line intentionally preserves all Redis arguments, including values.
            log.info("Redis command audit captured, commandName={}, status={}", commandName, status);
            RedisCommandAuditRegistry.publish(RedisCommandAuditEvent.builder()
                    .dataSourceId(connectInfo == null ? null : connectInfo.getDataSourceId())
                    .userId(connectInfo == null ? null : connectInfo.getUserId())
                    .databaseName(connectInfo == null ? null : connectInfo.getDatabaseName())
                    .type(connectInfo == null ? null : connectInfo.getDbType())
                    .command(toCommandLine(command))
                    .status(status)
                    .useTime(duration)
                    .operationRows(getOperationRows(command))
                    .extendInfo(buildExtendInfo(commandName, throwable))
                    .build());
        } catch (Exception e) {
            log.error("Redis command audit publish failed", e);
        }
    }

    private long duration(Map<String, Object> context) {
        if (context == null) {
            return 0L;
        }
        Object startedAt = context.get(STARTED_AT_KEY);
        if (!(startedAt instanceof Long start)) {
            return 0L;
        }
        return Math.max(System.currentTimeMillis() - start, 0L);
    }

    private Long getOperationRows(RedisCommand<Object, Object, Object> command) {
        try {
            CommandOutput<Object, Object, Object> output = command.getOutput();
            if (output == null || output.get() == null) {
                return 1L;
            }
            Object value = output.get();
            if (value instanceof Number number) {
                return number.longValue();
            }
        } catch (Exception e) {
            log.debug("Redis command audit output read failed", e);
        }
        return 1L;
    }

    private String buildExtendInfo(String commandName, Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"commandName\":\"").append(escapeJson(commandName)).append("\",")
                .append("\"source\":\"").append(SOURCE).append("\"");
        if (throwable != null && StringUtils.isNotBlank(throwable.getMessage())) {
            builder.append(",\"message\":\"").append(escapeJson(throwable.getMessage())).append("\"");
        }
        builder.append('}');
        return builder.toString();
    }

    private static String quoteArgument(String value) {
        if (value == null) {
            return "";
        }
        if (StringUtils.containsAny(value, ' ', '\t', '\r', '\n', '"')) {
            return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        return value;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
}
