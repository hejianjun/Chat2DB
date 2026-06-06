package ai.chat2db.plugin.redis;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.sql.Chat2DBContext;
import com.google.common.collect.Lists;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RedisMetaData extends DefaultMetaService implements MetaData {

    private static final long SCAN_COUNT = 1000L;
    private static final CommandExecutor COMMAND_EXECUTOR = new RedisCommandExecutor();

    @Override
    public List<Database> databases(Connection connection) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisCommands<String, String> commands = context.connection().sync();
            Map<String, String> config = commands.configGet("databases");
            String count = config.get("databases");
            List<Database> databases = new ArrayList<>();
            if (StringUtils.isNotBlank(count)) {
                for (int i = 0; i < Integer.parseInt(count); i++) {
                    databases.add(Database.builder().name(String.valueOf(i)).build());
                }
            }
            return databases;
        }
    }

    @Override
    @SneakyThrows
    public List<Schema> schemas(Connection connection, String databaseName) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisCommands<String, String> commands = context.connection().sync();
            selectDatabase(commands, databaseName);
            List<Schema> schemas = new ArrayList<>();
            Set<String> uniqueNames = new HashSet<>();
            for (String fullName : scanKeys(commands, null)) {
                if (StringUtils.contains(fullName, ":")) {
                    String schemaName = getSchemaName(fullName);
                    if (uniqueNames.add(schemaName)) {
                        Schema schema = new Schema();
                        schema.setName(schemaName);
                        schema.setDatabaseName(StringUtils.defaultIfBlank(databaseName, "0"));
                        schema.setTreeNodeType("tables");
                        schema.setKeyType(getType(commands, fullName));
                        schemas.add(schema);
                    }
                }
            }
            return schemas;
        }
    }

    @Override
    @SneakyThrows
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        try (RedisConnectionProvider.RedisConnectionContext context =
                     RedisConnectionProvider.open(Chat2DBContext.getConnectInfo())) {
            RedisCommands<String, String> commands = context.connection().sync();
            selectDatabase(commands, databaseName);
            String dbName = StringUtils.defaultIfBlank(databaseName, "0");
            if (StringUtils.isNotBlank(tableName)) {
                return Lists.newArrayList(Table.builder()
                        .name(tableName)
                        .databaseName(dbName)
                        .schemaName(getSchemaName(tableName))
                        .type(getType(commands, tableName))
                        .build());
            }
            String pattern = StringUtils.isBlank(schemaName) ? null : schemaName + "*";
            List<Table> tables = new ArrayList<>();
            for (String name : scanKeys(commands, pattern)) {
                tables.add(Table.builder()
                        .name(name)
                        .databaseName(dbName)
                        .schemaName(getSchemaName(name))
                        .type(getType(commands, name))
                        .build());
            }
            return tables;
        }
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return COMMAND_EXECUTOR;
    }

    private List<String> scanKeys(RedisCommands<String, String> commands, String pattern) {
        List<String> keys = new ArrayList<>();
        ScanArgs scanArgs = new ScanArgs().limit(SCAN_COUNT);
        if (StringUtils.isNotBlank(pattern)) {
            scanArgs.match(pattern);
        }
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> result = commands.scan(cursor, scanArgs);
            keys.addAll(result.getKeys());
            cursor = ScanCursor.of(result.getCursor());
            cursor.setFinished(result.isFinished());
        } while (!cursor.isFinished());
        return keys;
    }

    private void selectDatabase(RedisCommands<String, String> commands, String databaseName) {
        if (StringUtils.isNotBlank(databaseName)) {
            commands.select(Integer.parseInt(databaseName));
        }
    }

    private String getType(RedisCommands<String, String> commands, String tableName) {
        try {
            String type = commands.type(tableName);
            if (StringUtils.isNotBlank(type)) {
                return type;
            }
        } catch (Exception e) {
            log.error("type获取失败", e);
        }
        return "string";
    }

    private String getSchemaName(String fullName) {
        if (!StringUtils.contains(fullName, ":")) {
            return "";
        }
        return StringUtils.substringBeforeLast(fullName, ":");
    }
}
