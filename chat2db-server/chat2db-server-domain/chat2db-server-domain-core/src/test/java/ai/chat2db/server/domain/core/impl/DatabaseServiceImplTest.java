package ai.chat2db.server.domain.core.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
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
                .build();

        String schema = DatabaseServiceImpl.formatMongoSchema(List.of(table));
        JSONArray schemas = JSON.parseArray(schema);
        JSONObject result = schemas.getJSONObject(0);
        JSONObject properties = result.getJSONObject("schema")
                .getJSONObject("$jsonSchema")
                .getJSONObject("properties");

        assertThat(result.getString("collection")).isEqualTo("orders.archive");
        assertThat(result.getString("source")).isEqualTo("sampledDocuments");
        assertThat(properties.getJSONObject("_id").getString("bsonType")).isEqualTo("objectId");
        assertThat(properties.getJSONObject("amount").getString("bsonType")).isEqualTo("double");
        assertThat(properties.getJSONObject("metadata").getString("bsonType")).isEqualTo("object");
        assertThat(properties.getJSONObject("variable")).doesNotContainKey("bsonType");
        assertThat(properties.getJSONObject("variable").getString("description"))
                .contains("Multiple BSON types observed in sampled documents");
    }

    @Test
    void formatsEmptyCollectionListAsJsonArray() {
        assertThat(DatabaseServiceImpl.formatMongoSchema(List.of())).isEqualTo("[]");
    }

    private TableColumn column(String name, String columnType) {
        return TableColumn.builder().name(name).columnType(columnType).build();
    }
}
