package ai.chat2db.plugin.mongodb;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.enums.IndexTypeEnum;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.model.TableIndexColumn;
import ai.chat2db.spi.sql.Chat2DBContext;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.sql.Connection;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MongodbMetaData extends DefaultMetaService implements MetaData {

    private static final int FIELD_SAMPLE_SIZE = 100;
    private static final int UNAUTHORIZED_ERROR_CODE = 13;
    private static final CommandExecutor COMMAND_EXECUTOR = new MongodbCommandExecutor();

    @Override
    public List<Database> databases(Connection connection) {
        try (MongodbConnectionProvider.MongodbConnectionContext context = open()) {
            List<Database> databases = new ArrayList<>();
            for (String name : context.client().listDatabaseNames()) {
                databases.add(Database.builder().name(name).build());
            }
            return databases;
        }
    }

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        return Collections.emptyList();
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        try (MongodbConnectionProvider.MongodbConnectionContext context = open()) {
            List<Table> tables = new ArrayList<>();
            for (String collectionName : context.client().getDatabase(databaseName).listCollectionNames()) {
                if (collectionName.startsWith("system.")) {
                    continue;
                }
                if (StringUtils.isNotBlank(tableName) && !StringUtils.equals(tableName, collectionName)) {
                    continue;
                }
                Table table = Table.builder()
                        .name(collectionName)
                        .databaseName(databaseName)
                        .dbType("MONGODB")
                        .type("TABLE")
                        .build();
                Long rowCount = estimatedDocumentCount(context.client().getDatabase(databaseName)
                        .getCollection(collectionName));
                if (rowCount != null) {
                    table.setRowCount(rowCount);
                }
                tables.add(table);
            }
            return tables;
        }
    }

    @Override
    public List<Table> views(Connection connection, String databaseName, String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        return "db.createCollection(\"" + tableName.replace("\"", "\\\"") + "\")";
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        return columns(connection, databaseName, schemaName, tableName, null);
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName,
                                     String columnName) {
        try (MongodbConnectionProvider.MongodbConnectionContext context = open()) {
            MongoCollection<Document> collection = context.client().getDatabase(databaseName).getCollection(tableName);
            Map<String, FieldInfo> fields = new LinkedHashMap<>();
            try {
                for (Document document : collection.find().limit(FIELD_SAMPLE_SIZE)) {
                    document.forEach((name, value) -> mergeField(fields, name, value));
                }
            } catch (MongoException e) {
                if (e.getCode() == UNAUTHORIZED_ERROR_CODE) {
                    return Collections.emptyList();
                }
                throw e;
            }
            List<TableColumn> columns = new ArrayList<>();
            int position = 1;
            for (Map.Entry<String, FieldInfo> entry : fields.entrySet()) {
                if (StringUtils.isNotBlank(columnName) && !StringUtils.equals(columnName, entry.getKey())) {
                    continue;
                }
                FieldInfo field = entry.getValue();
                columns.add(TableColumn.builder()
                        .name(entry.getKey())
                        .oldName(entry.getKey())
                        .tableName(tableName)
                        .databaseName(databaseName)
                        .columnType(field.bsonType())
                        .dataType(field.dataType())
                        .primaryKey("_id".equals(entry.getKey()))
                        .nullable(field.nullable() ? 1 : 0)
                        .ordinalPosition(position++)
                        .build());
            }
            return columns;
        }
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        try (MongodbConnectionProvider.MongodbConnectionContext context = open()) {
            MongoDatabase database = context.client().getDatabase(databaseName);
            List<TableIndex> indexes = new ArrayList<>();
            for (Document index : database.getCollection(tableName).listIndexes()) {
                String name = index.getString("name");
                boolean unique = Boolean.TRUE.equals(index.getBoolean("unique"));
                Document keys = index.get("key", Document.class);
                List<TableIndexColumn> columns = new ArrayList<>();
                short position = 1;
                if (keys != null) {
                    for (Map.Entry<String, Object> key : keys.entrySet()) {
                        columns.add(TableIndexColumn.builder()
                                .indexName(name)
                                .tableName(tableName)
                                .databaseName(databaseName)
                                .columnName(key.getKey())
                                .ordinalPosition(position++)
                                .ascOrDesc(Number.class.isInstance(key.getValue())
                                        && ((Number) key.getValue()).intValue() < 0 ? "D" : "A")
                                .nonUnique(!unique)
                                .build());
                    }
                }
                indexes.add(TableIndex.builder()
                        .name(name)
                        .tableName(tableName)
                        .databaseName(databaseName)
                        .unique(unique)
                        .type(unique ? IndexTypeEnum.UNIQUE.getCode() : IndexTypeEnum.NORMAL.getCode())
                        .columnList(columns)
                        .build());
            }
            return indexes;
        }
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return COMMAND_EXECUTOR;
    }

    @Override
    public boolean supportBatchQueryColumns() {
        return false;
    }

    private MongodbConnectionProvider.MongodbConnectionContext open() {
        return MongodbConnectionProvider.open(Chat2DBContext.getConnectInfo());
    }

    /**
     * Uses MongoDB's metadata-based estimate instead of scanning the collection.
     * Views and collections without collStats permission may not support this operation.
     */
    private Long estimatedDocumentCount(MongoCollection<Document> collection) {
        try {
            return collection.estimatedDocumentCount();
        } catch (MongoException e) {
            return null;
        }
    }

    private void mergeField(Map<String, FieldInfo> fields, String name, Object value) {
        FieldInfo incoming = resolveField(value);
        fields.merge(name, incoming, (current, next) -> {
            if ("NULL".equals(current.bsonType())) {
                return new FieldInfo(next.bsonType(), next.dataType(), true);
            }
            if ("NULL".equals(next.bsonType())) {
                return new FieldInfo(current.bsonType(), current.dataType(), true);
            }
            if (current.bsonType().equals(next.bsonType())) {
                return new FieldInfo(current.bsonType(), current.dataType(),
                        current.nullable() || next.nullable());
            }
            return new FieldInfo("MIXED", DataTypeEnum.ANY.getCode(),
                    current.nullable() || next.nullable());
        });
    }

    private FieldInfo resolveField(Object value) {
        if (value == null) {
            return new FieldInfo("NULL", DataTypeEnum.UNKNOWN.getCode(), true);
        }
        if (value instanceof Number) {
            return new FieldInfo(value.getClass().getSimpleName().toUpperCase(), DataTypeEnum.NUMERIC.getCode(), false);
        }
        if (value instanceof Boolean) {
            return new FieldInfo("BOOLEAN", DataTypeEnum.BOOLEAN.getCode(), false);
        }
        if (value instanceof Date || value instanceof Temporal) {
            return new FieldInfo("DATE", DataTypeEnum.DATETIME.getCode(), false);
        }
        if (value instanceof byte[]) {
            return new FieldInfo("BINARY", DataTypeEnum.BINARY.getCode(), false);
        }
        if (value instanceof List<?>) {
            return new FieldInfo("ARRAY", DataTypeEnum.ARRAY.getCode(), false);
        }
        if (value instanceof Document || value instanceof Map<?, ?>) {
            return new FieldInfo("DOCUMENT", DataTypeEnum.DOCUMENT.getCode(), false);
        }
        if (value instanceof ObjectId) {
            return new FieldInfo("OBJECT_ID", DataTypeEnum.STRING.getCode(), false);
        }
        return new FieldInfo("STRING", DataTypeEnum.STRING.getCode(), false);
    }

    private record FieldInfo(String bsonType, String dataType, boolean nullable) {
    }
}
