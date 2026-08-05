package ai.chat2db.server.domain.api.model;

import java.util.List;

import ai.chat2db.spi.model.IndexModel;
import ai.chat2db.spi.model.LuceneField;
import ai.chat2db.spi.model.LuceneFieldType;
import ai.chat2db.spi.redis.RedisKeyInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 持久化的 Redis key 前缀样例。
 *
 * <p>样例列表只保存在 Lucene 文档的 source 中，不建立字段索引，避免较大的 Redis value
 * 触发 Lucene 单个 term 长度限制。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisKeyPrefixSample implements IndexModel {

    /** 使用前缀 SHA-256 作为文档名，避免超长 Redis key 前缀进入精确索引。 */
    private String name;

    private String comment;

    private String aiComment;

    private Long dataSourceId;

    @LuceneField(name = "databaseName", type = LuceneFieldType.STRING)
    private String databaseName;

    private String schemaName;

    @LuceneField(name = "prefixHash", type = LuceneFieldType.STRING)
    private String prefixHash;

    private String prefix;

    private List<RedisKeyInfo> samples;

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
        return RedisKeyPrefixSample.class;
    }
}
