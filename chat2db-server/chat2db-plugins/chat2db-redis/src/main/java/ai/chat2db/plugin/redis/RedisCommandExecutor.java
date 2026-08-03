package ai.chat2db.plugin.redis;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.ValueHandler;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.enums.SqlTypeEnum;
import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import ai.chat2db.spi.sql.Chat2DBContext;
import cn.hutool.core.date.TimeInterval;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.ProtocolKeyword;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RedisCommandExecutor implements CommandExecutor {

    private static final int DEFAULT_SCAN_COUNT = 1000;

    @Override
    public List<ExecuteResult> execute(Command command) {
        List<RedisCommandParser.StatementPosition> statements =
                RedisCommandParser.splitStatementPositions(command.getScript());
        List<ExecuteResult> results = new ArrayList<>();
        if (CollectionUtils.isEmpty(statements)) {
            return results;
        }
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisCommands<String, String> commands = context.connection().sync();
            for (int i = 0; i < statements.size(); i++) {
                RedisCommandParser.StatementPosition statement = statements.get(i);
                ExecuteResult result = executeStatement(commands, statement.statement());
                result.setStatementIndex(i + 1);
                result.setStatementStartLine(getStatementLine(command.getScriptStartLine(), statement.startLine()));
                result.setStatementEndLine(getStatementLine(command.getScriptStartLine(), statement.endLine()));
                result.setOriginalSql(statement.statement());
                results.add(result);
            }
        }
        return results;
    }

    @Override
    public ExecuteResult executeUpdate(String sql, Connection connection, int n) throws SQLException {
        return execute(sql, connection, true, null, null, null);
    }

    @Override
    public ExecuteResult execute(String sql, Connection connection, boolean limitRowSize, Integer offset, Integer count,
                                 ValueHandler valueHandler) throws SQLException {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            return executeStatement(context.connection().sync(), sql);
        }
    }

    private ExecuteResult executeStatement(RedisCommands<String, String> commands, String statement) {
        TimeInterval timer = new TimeInterval();
        ExecuteResult result = ExecuteResult.builder()
                .sql(statement)
                .success(Boolean.TRUE)
                .description("Success")
                .sqlType(SqlTypeEnum.UNKNOWN.getCode())
                .build();
        try {
            List<String> args = RedisCommandParser.tokenize(statement);
            if (CollectionUtils.isEmpty(args)) {
                result.setUpdateCount(0);
                return result;
            }
            String command = args.get(0).toUpperCase(Locale.ROOT);
            switch (command) {
                case "PING" -> oneValue(result, "result", commands.ping());
                case "SELECT" -> status(result, commands.select(parseInt(args, 1, "database")));
                case "CONFIG" -> executeConfig(commands, args, result);
                case "DBSIZE" -> oneValue(result, "size", commands.dbsize());
                case "TYPE" -> oneValue(result, "type", commands.type(required(args, 1, "key")));
                case "EXISTS" -> oneValue(result, "exists", commands.exists(tail(args, 1)));
                case "TTL" -> oneValue(result, "ttl", commands.ttl(required(args, 1, "key")));
                case "MEMORY" -> memoryUsage(commands, args, result);
                case "EXPIRE" -> oneValue(result, "result",
                        commands.expire(required(args, 1, "key"), parseLong(args, 2, "seconds")));
                case "DEL" -> oneValue(result, "deleted", commands.del(tail(args, 1)));
                case "GET" -> oneValue(result, "value", commands.get(required(args, 1, "key")));
                case "SET" -> status(result, commands.set(required(args, 1, "key"), required(args, 2, "value")));
                case "KEYS" -> listValues(result, "key", commands.keys(required(args, 1, "pattern")));
                case "SCAN" -> scan(commands, args, result);
                case "EVAL" -> scriptCommand(commands, CommandType.EVAL, args, result);
                case "EVALSHA" -> scriptCommand(commands, CommandType.EVALSHA, args, result);
                case "SCRIPT" -> scriptCommand(commands, CommandType.SCRIPT, args, result);
                case "HGET" -> oneValue(result, "value", commands.hget(required(args, 1, "key"),
                        required(args, 2, "field")));
                case "HGETALL" -> mapValues(result, "field", "value", commands.hgetall(required(args, 1, "key")));
                case "HSET" -> oneValue(result, "added", commands.hset(required(args, 1, "key"),
                        fieldValueMap(args, 2)));
                case "LRANGE" -> listValues(result, "value", commands.lrange(required(args, 1, "key"),
                        parseLong(args, 2, "start"), parseLong(args, 3, "stop")));
                case "LPUSH" -> oneValue(result, "length", commands.lpush(required(args, 1, "key"), tail(args, 2)));
                case "RPUSH" -> oneValue(result, "length", commands.rpush(required(args, 1, "key"), tail(args, 2)));
                case "SMEMBERS" -> listValues(result, "member", commands.smembers(required(args, 1, "key")));
                case "SADD" -> oneValue(result, "added", commands.sadd(required(args, 1, "key"), tail(args, 2)));
                case "ZRANGE" -> listValues(result, "member", commands.zrange(required(args, 1, "key"),
                        parseLong(args, 2, "start"), parseLong(args, 3, "stop")));
                case "ZADD" -> oneValue(result, "added", commands.zadd(required(args, 1, "key"),
                        parseDouble(args, 2, "score"), required(args, 3, "member")));
                default -> dispatchCommand(commands, command, args, result);
            }
        } catch (Exception e) {
            result.setSuccess(Boolean.FALSE);
            result.setMessage(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        } finally {
            result.setDuration(timer.interval());
        }
        return result;
    }

    private int getStatementLine(Integer scriptStartLine, int statementLine) {
        return statementLine + Math.max((scriptStartLine == null ? 1 : scriptStartLine) - 1, 0);
    }

    private void executeConfig(RedisCommands<String, String> commands, List<String> args, ExecuteResult result) {
        if (args.size() >= 3 && "GET".equalsIgnoreCase(args.get(1))) {
            mapValues(result, "parameter", "value", commands.configGet(args.get(2)));
            return;
        }
        throw new IllegalArgumentException("Only CONFIG GET is supported");
    }

    private void memoryUsage(RedisCommands<String, String> commands, List<String> args, ExecuteResult result) {
        if (args.size() < 3 || !"USAGE".equalsIgnoreCase(args.get(1))) {
            throw new IllegalArgumentException("Only MEMORY USAGE key [SAMPLES count] is supported");
        }
        String key = required(args, 2, "key");
        if (args.size() == 3) {
            oneValue(result, "bytes", commands.memoryUsage(key));
            return;
        }
        if (args.size() != 5 || !"SAMPLES".equalsIgnoreCase(args.get(3))) {
            throw new IllegalArgumentException("Redis MEMORY USAGE syntax: MEMORY USAGE key [SAMPLES count]");
        }
        long samples = parseLong(args, 4, "count");
        if (samples < 0) {
            throw new IllegalArgumentException("Redis MEMORY USAGE SAMPLES count must be non-negative");
        }
        CommandArgs<String, String> commandArgs = new CommandArgs<>(StringCodec.UTF8)
                .add("USAGE")
                .add(key)
                .add("SAMPLES")
                .add(samples);
        Object value = commands.dispatch(CommandType.MEMORY, new FlexibleRedisOutput(StringCodec.UTF8), commandArgs);
        oneValue(result, "bytes", value);
    }

    private void scan(RedisCommands<String, String> commands, List<String> args, ExecuteResult result) {
        String cursor = ScanCursor.INITIAL.getCursor();
        ScanArgs scanArgs = new ScanArgs().limit(DEFAULT_SCAN_COUNT);
        int optionStart = 1;
        if (args.size() > 1) {
            String firstArg = args.get(1);
            if (StringUtils.isNumeric(firstArg)) {
                cursor = firstArg;
                optionStart = 2;
            } else if (!isScanOption(firstArg)) {
                scanArgs.match("*" + firstArg + "*");
                optionStart = 2;
            }
        }
        for (int i = optionStart; i < args.size(); i++) {
            String token = args.get(i);
            if ("MATCH".equalsIgnoreCase(token)) {
                scanArgs.match(required(args, ++i, "pattern"));
            } else if ("COUNT".equalsIgnoreCase(token)) {
                scanArgs.limit(parseLong(args, ++i, "count"));
            }
        }
        KeyScanCursor<String> scan = commands.scan(ScanCursor.of(cursor), scanArgs);
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("cursor", scan.getCursor()));
        for (String key : scan.getKeys()) {
            rows.add(Arrays.asList("key", key));
        }
        result.setHeaderList(headers("type", "value"));
        result.setDataList(rows);
        result.setSqlType(SqlTypeEnum.SELECT.getCode());
        result.setPageNo(1);
        result.setPageSize(rows.size());
        result.setHasNextPage(!scan.isFinished());
        result.setFuzzyTotal(scan.isFinished() ? Integer.toString(rows.size()) : rows.size() + "+");
    }

    private void scriptCommand(RedisCommands<String, String> commands, CommandType commandType, List<String> args,
                               ExecuteResult result) {
        if (args.size() <= 1) {
            throw new IllegalArgumentException("Missing Redis command arguments");
        }
        CommandArgs<String, String> commandArgs = new CommandArgs<>(StringCodec.UTF8);
        for (int i = 1; i < args.size(); i++) {
            commandArgs.add(args.get(i));
        }
        Object value = commands.dispatch(commandType, new FlexibleRedisOutput(StringCodec.UTF8), commandArgs);
        scriptResult(result, value);
    }

    private void dispatchCommand(RedisCommands<String, String> commands, String command, List<String> args,
                                 ExecuteResult result) {
        CommandArgs<String, String> commandArgs = new CommandArgs<>(StringCodec.UTF8);
        for (int i = 1; i < args.size(); i++) {
            commandArgs.add(args.get(i));
        }
        Object value = commands.dispatch(new RawRedisCommand(command),
                new FlexibleRedisOutput(StringCodec.UTF8), commandArgs);
        scriptResult(result, value);
    }

    private boolean isScanOption(String token) {
        return "MATCH".equalsIgnoreCase(token) || "COUNT".equalsIgnoreCase(token);
    }

    private void status(ExecuteResult result, String value) {
        result.setUpdateCount(1);
        oneValue(result, "result", value);
    }

    private void oneValue(ExecuteResult result, String name, Object value) {
        result.setHeaderList(headers(name));
        result.setDataList(List.of(List.of(String.valueOf(value))));
        result.setSqlType(SqlTypeEnum.SELECT.getCode());
        result.setPageNo(1);
        result.setPageSize(1);
        result.setHasNextPage(Boolean.FALSE);
        result.setFuzzyTotal("1");
    }

    private void listValues(ExecuteResult result, String name, Iterable<?> values) {
        List<List<String>> rows = new ArrayList<>();
        for (Object value : values) {
            rows.add(List.of(String.valueOf(value)));
        }
        result.setHeaderList(headers(name));
        result.setDataList(rows);
        result.setSqlType(SqlTypeEnum.SELECT.getCode());
        result.setPageNo(1);
        result.setPageSize(rows.size());
        result.setHasNextPage(Boolean.FALSE);
        result.setFuzzyTotal(Integer.toString(rows.size()));
    }

    private void mapValues(ExecuteResult result, String keyName, String valueName, Map<String, String> values) {
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rows.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        result.setHeaderList(headers(keyName, valueName));
        result.setDataList(rows);
        result.setSqlType(SqlTypeEnum.SELECT.getCode());
        result.setPageNo(1);
        result.setPageSize(rows.size());
        result.setHasNextPage(Boolean.FALSE);
        result.setFuzzyTotal(Integer.toString(rows.size()));
    }

    private void scriptResult(ExecuteResult result, Object value) {
        if (!(value instanceof List<?> values)) {
            oneValue(result, "value", value);
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        appendScriptRows(rows, "", values);
        result.setHeaderList(headers("path", "value"));
        result.setDataList(rows);
        result.setSqlType(SqlTypeEnum.SELECT.getCode());
        result.setPageNo(1);
        result.setPageSize(rows.size());
        result.setHasNextPage(Boolean.FALSE);
        result.setFuzzyTotal(Integer.toString(rows.size()));
    }

    private void appendScriptRows(List<List<String>> rows, String path, List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object value = values.get(i);
            if (value instanceof List<?> nestedValues) {
                appendScriptRows(rows, itemPath, nestedValues);
            } else {
                rows.add(Arrays.asList(itemPath, String.valueOf(value)));
            }
        }
    }

    private List<Header> headers(String... names) {
        List<Header> headers = new ArrayList<>();
        for (String name : names) {
            headers.add(Header.builder().name(name).dataType(DataTypeEnum.STRING.getCode()).build());
        }
        return headers;
    }

    private String required(List<String> args, int index, String name) {
        if (args.size() <= index || StringUtils.isBlank(args.get(index))) {
            throw new IllegalArgumentException("Missing Redis command argument: " + name);
        }
        return args.get(index);
    }

    private String[] tail(List<String> args, int start) {
        if (args.size() <= start) {
            throw new IllegalArgumentException("Missing Redis command arguments");
        }
        return args.subList(start, args.size()).toArray(new String[0]);
    }

    private Map<String, String> fieldValueMap(List<String> args, int start) {
        if (args.size() <= start) {
            throw new IllegalArgumentException("Missing Redis command arguments");
        }
        int pairCount = args.size() - start;
        if (pairCount % 2 != 0) {
            throw new IllegalArgumentException("Redis HSET requires field/value pairs");
        }
        Map<String, String> values = new LinkedHashMap<>(pairCount / 2);
        for (int i = start; i < args.size(); i += 2) {
            values.put(args.get(i), args.get(i + 1));
        }
        return values;
    }

    private int parseInt(List<String> args, int index, String name) {
        return Integer.parseInt(required(args, index, name));
    }

    private long parseLong(List<String> args, int index, String name) {
        return Long.parseLong(required(args, index, name));
    }

    private double parseDouble(List<String> args, int index, String name) {
        return Double.parseDouble(required(args, index, name));
    }

    private static class RawRedisCommand implements ProtocolKeyword {

        private final String name;
        private final byte[] bytes;

        RawRedisCommand(String name) {
            this.name = name;
            this.bytes = name.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private static class FlexibleRedisOutput extends CommandOutput<String, String, Object> {

        private final Deque<List<Object>> stack = new ArrayDeque<>();
        private int depth;

        FlexibleRedisOutput(StringCodec codec) {
            super(codec, null);
        }

        @Override
        public void set(ByteBuffer bytes) {
            append(bytes == null ? null : codec.decodeValue(bytes));
        }

        @Override
        public void setSingle(ByteBuffer bytes) {
            set(bytes);
        }

        @Override
        public void setBigNumber(ByteBuffer bytes) {
            set(bytes);
        }

        @Override
        public void set(long integer) {
            append(integer);
        }

        @Override
        public void set(double number) {
            append(number);
        }

        @Override
        public void set(boolean value) {
            append(value);
        }

        @Override
        public void multi(int count) {
            List<Object> values = new ArrayList<>(Math.max(count, 0));
            append(values);
            stack.push(values);
            depth++;
        }

        @Override
        public void multiArray(int count) {
            multi(count);
        }

        @Override
        public void multiMap(int count) {
            multi(count);
        }

        @Override
        public void complete(int depth) {
            if (depth > 0 && depth < this.depth && !stack.isEmpty()) {
                stack.pop();
                this.depth--;
            }
        }

        private void append(Object value) {
            if (stack.isEmpty()) {
                output = value;
                return;
            }
            stack.peek().add(value);
        }
    }
}
