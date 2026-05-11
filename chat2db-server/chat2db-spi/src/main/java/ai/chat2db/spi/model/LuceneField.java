package ai.chat2db.spi.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lucene 字段注解，用于声明字段在 Lucene 索引中的行为
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LuceneField {
    /**
     * Lucene 索引中的字段名
     */
    String name();

    /**
     * 字段类型
     */
    LuceneFieldType type() default LuceneFieldType.TEXT;

    /**
     * 是否支持排序（需要添加 DocValues）
     */
    boolean sort() default false;

    /**
     * 是否存储原始值（Field.Store.YES）
     */
    boolean store() default false;
}
