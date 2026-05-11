package ai.chat2db.spi.model;

/**
 * Lucene 字段类型枚举
 */
public enum LuceneFieldType {
    /**
     * 文本类型，支持全文搜索
     */
    TEXT,

    /**
     * 字符串类型，精确匹配，支持排序
     */
    STRING,

    /**
     * 长整型，支持范围查询和排序
     */
    LONG,

    /**
     * 整型，支持范围查询和排序
     */
    INTEGER,

    /**
     * 双精度浮点型，支持范围查询和排序
     */
    DOUBLE
}
