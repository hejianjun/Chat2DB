package ai.chat2db.server.domain.core.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.model.TableIndexColumn;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

class DatabaseServiceImplTest {

    @Test
    void formatsSampledMongoFieldsAsJsonSchema() {
        Table table = Table.builder()
                .name("orders.archive")
                .columnList(List.of(
                        column("_id", "OBJECT_ID"),
                        column("amount", "DOUBLE"),
                        column("metadata", "DOCUMENT"),
                        column("variable", "MIXED")))
                .indexList(List.of(
                        index("_id_", false, indexColumn("_id", "A", (short) 1)),
                        index("idx_status_created", true,
                                indexColumn("status", "A", (short) 1),
                                indexColumn("createdAt", "D", (short) 2)),
                        index("idx_location", false, indexColumn("location", "2dsphere", (short) 1))))
                .build();

        String schema = DatabaseServiceImpl.formatMongoSchema(List.of(table));
        JSONArray schemas = JSON.parseArray(schema);
        JSONObject result = schemas.getJSONObject(0);
        JSONObject properties = result.getJSONObject("schema")
                .getJSONObject("$jsonSchema")
                .getJSONObject("properties");
        JSONArray indexes = result.getJSONArray("indexes");

        assertThat(result.getString("collection")).isEqualTo("orders.archive");
        assertThat(result.getString("source")).isEqualTo("sampledDocuments");
        assertThat(properties.getJSONObject("_id").getString("bsonType")).isEqualTo("objectId");
        assertThat(properties.getJSONObject("amount").getString("bsonType")).isEqualTo("double");
        assertThat(properties.getJSONObject("metadata").getString("bsonType")).isEqualTo("object");
        assertThat(properties.getJSONObject("variable")).doesNotContainKey("bsonType");
        assertThat(properties.getJSONObject("variable").getString("description"))
                .contains("Multiple BSON types observed in sampled documents");
        assertThat(indexes).hasSize(3);
        assertThat(indexes.getJSONObject(0).getJSONObject("keys").getIntValue("_id")).isEqualTo(1);
        assertThat(indexes.getJSONObject(1).getString("name")).isEqualTo("idx_status_created");
        assertThat(indexes.getJSONObject(1).getJSONObject("keys").getIntValue("status")).isEqualTo(1);
        assertThat(indexes.getJSONObject(1).getJSONObject("keys").getIntValue("createdAt")).isEqualTo(-1);
        assertThat(indexes.getJSONObject(1).getBooleanValue("unique")).isTrue();
        assertThat(indexes.getJSONObject(2).getJSONObject("keys").getString("location")).isEqualTo("2dsphere");
    }

    @Test
    void formatsEmptyCollectionListAsJsonArray() {
        assertThat(DatabaseServiceImpl.formatMongoSchema(List.of())).isEqualTo("[]");
    }

    private TableColumn column(String name, String columnType) {
        return TableColumn.builder().name(name).columnType(columnType).build();
    }

    private TableIndex index(String name, boolean unique, TableIndexColumn... columns) {
        return TableIndex.builder().name(name).unique(unique).columnList(List.of(columns)).build();
    }

    private TableIndexColumn indexColumn(String name, String keyType, short position) {
        return TableIndexColumn.builder()
                .columnName(name)
                .ascOrDesc(keyType)
                .ordinalPosition(position)
                .build();
    }
}
