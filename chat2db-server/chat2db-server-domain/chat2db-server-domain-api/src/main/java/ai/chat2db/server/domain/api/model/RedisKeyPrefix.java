package ai.chat2db.server.domain.api.model;

import ai.chat2db.spi.model.IndexModel;
import ai.chat2db.spi.model.LuceneField;
import ai.chat2db.spi.model.LuceneFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis key 前缀模型。
 *
 * <p>该模型同时承担两类职责：一是作为 Lucene 索引文档，用于保存 Redis key 前缀、备注、
 * 数据库名和刷新版本；二是作为接口返回对象，向前端展示前缀名称、key 数量以及当前是否
 * 正在刷新。</p>
 *
 * <p>带有 {@link LuceneField} 注解的字段会写入 Lucene 索引；未带注解的字段通常只用于
 * 业务上下文、接口返回或运行时状态。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisKeyPrefix implements IndexModel {

    /**
     * Redis key 前缀名称。
     *
     * <p>由完整 Redis key 去掉最后一段后得到，例如 {@code user:1001:profile} 的前缀为
     * {@code user:1001}。该字段进入 Lucene 文本索引，并支持排序。</p>
     */
    @LuceneField(name = "name", type = LuceneFieldType.TEXT, sort = true)
    private String name;

    /**
     * 用户手动维护的前缀备注。
     */
    @LuceneField(name = "comment", type = LuceneFieldType.TEXT, inherit = true)
    private String comment;

    /**
     * AI 生成的前缀说明。
     */
    @LuceneField(name = "aiComment", type = LuceneFieldType.TEXT, inherit = true)
    private String aiComment;

    /**
     * 数据源 ID。
     *
     * <p>LuceneIndexManager 按数据源获取索引管理器，因此该字段主要用于构造文档和接口返回，
     * 不作为当前文档内的 Lucene 查询字段。</p>
     */
    private Long dataSourceId;

    /**
     * Redis 数据库名称或编号。
     *
     * <p>用于隔离同一数据源下不同 Redis database 的前缀索引。</p>
     */
    @LuceneField(name = "databaseName", type = LuceneFieldType.STRING)
    private String databaseName;

    /**
     * Schema 名称。
     *
     * <p>该字段用于复用通用索引模型的 schema 维度；Redis 场景通常为空。</p>
     */
    @LuceneField(name = "schemaName", type = LuceneFieldType.STRING)
    private String schemaName;

    /**
     * 当前前缀下聚合到的 Redis key 数量。
     */
    @LuceneField(name = "keyCount", type = LuceneFieldType.LONG, sort = true, inherit = true)
    private Long keyCount;

    /**
     * 当前数据源/库是否正在刷新 Redis key 前缀缓存。
     *
     * <p>这是运行时返回字段，不写入 Lucene。搜索结果返回前由服务层根据刷新状态填充。</p>
     */
    private Boolean building;

    /**
     * 客户端侧使用的数据版本。
     */
    private Long version;

    /**
     * 适配通用 {@link IndexModel} 的表名概念。
     *
     * <p>Redis 没有表名，这里用前缀名称作为通用索引模型中的 tableName。</p>
     */
    @Override
    public String getTableName() {
        return name;
    }

    /**
     * 适配通用 {@link IndexModel} 的表名写入方法。
     */
    @Override
    public void setTableName(String tableName) {
        this.name = tableName;
    }

    /**
     * 返回当前索引文档对应的模型类型，供 Lucene 索引管理器识别文档结构。
     */
    @Override
    public Class<? extends IndexModel> getClassType() {
        return RedisKeyPrefix.class;
    }
}
