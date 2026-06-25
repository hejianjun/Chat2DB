package ai.chat2db.server.domain.api.param;

import ai.chat2db.server.domain.api.model.RedisKeyPrefix;
import ai.chat2db.server.tools.base.wrapper.param.PageQueryParam;
import ai.chat2db.spi.model.BaseModel;
import ai.chat2db.spi.model.LuceneField;
import ai.chat2db.spi.model.LuceneFieldType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RedisKeyPrefixQueryParam extends PageQueryParam implements BaseModel<RedisKeyPrefix> {

    private static final long serialVersionUID = 1L;

    @NotNull
    private Long dataSourceId;

    @LuceneField(name = "databaseName", type = LuceneFieldType.STRING)
    private String databaseName;

    @LuceneField(name = "schemaName", type = LuceneFieldType.STRING)
    private String schemaName;

    private String searchKey;

    private boolean refresh;

    private String sortField;

    private String sortOrder;

    @Override
    public String getTableName() {
        return null;
    }

    @Override
    public void setTableName(String tableName) {
        // Redis key prefixes are not table scoped.
    }

    @Override
    public Class<RedisKeyPrefix> getClassType() {
        return RedisKeyPrefix.class;
    }
}
