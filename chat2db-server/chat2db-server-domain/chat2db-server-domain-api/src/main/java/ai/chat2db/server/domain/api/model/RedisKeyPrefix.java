package ai.chat2db.server.domain.api.model;

import ai.chat2db.spi.model.IndexModel;
import ai.chat2db.spi.model.LuceneField;
import ai.chat2db.spi.model.LuceneFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisKeyPrefix implements IndexModel {

    @LuceneField(name = "name", type = LuceneFieldType.TEXT, sort = true)
    private String name;

    @LuceneField(name = "comment", type = LuceneFieldType.TEXT)
    private String comment;

    @LuceneField(name = "aiComment", type = LuceneFieldType.TEXT)
    private String aiComment;

    private Long dataSourceId;

    @LuceneField(name = "databaseName", type = LuceneFieldType.STRING)
    private String databaseName;

    @LuceneField(name = "schemaName", type = LuceneFieldType.STRING)
    private String schemaName;

    private Long keyCount;

    private Boolean building;

    private Long version;

    @Override
    public String getTableName() {
        return name;
    }

    @Override
    public void setTableName(String tableName) {
        this.name = tableName;
    }

    @Override
    public Class<? extends IndexModel> getClassType() {
        return RedisKeyPrefix.class;
    }
}
