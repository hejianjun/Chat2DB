package ai.chat2db.server.domain.core.cache;

import java.lang.reflect.Field;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;

import ai.chat2db.spi.model.LuceneField;
import ai.chat2db.spi.model.LuceneFieldType;
import lombok.SneakyThrows;

/**
 * 基于注解的 Lucene 文档构建器
 * 通过读取字段上的 @LuceneField 注解自动构建 Lucene Document
 */
public class AnnotationBasedDocumentBuilder {

    /**
     * 基于注解构建 Lucene 文档
     *
     * @param source 源对象
     * @return Lucene 文档
     */
    @SneakyThrows
    public org.apache.lucene.document.Document buildDocument(Object source) {
        if (source == null) {
            return new org.apache.lucene.document.Document();
        }

        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
        Class<?> clazz = source.getClass();

        // 递归处理父类字段（从子类到父类）
        buildClassFields(doc, clazz, source);

        return doc;
    }

    /**
     * 递归处理类及其父类的字段
     */
    @SneakyThrows
    private void buildClassFields(org.apache.lucene.document.Document doc, Class<?> clazz, Object source) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        // 先处理父类字段
        buildClassFields(doc, clazz.getSuperclass(), source);

        // 处理当前类字段
        for (Field field : clazz.getDeclaredFields()) {
            LuceneField annotation = field.getAnnotation(LuceneField.class);
            if (annotation != null) {
                field.setAccessible(true);
                Object value = field.get(source);
                addFieldToDocument(doc, annotation, value);
            }
        }
    }

    /**
     * 根据注解配置添加字段到文档
     */
    private void addFieldToDocument(org.apache.lucene.document.Document doc, LuceneField annotation, Object value) {
        if (value == null) {
            return;
        }

        String fieldName = annotation.name();
        LuceneFieldType type = annotation.type();
        boolean sort = annotation.sort();
        boolean store = annotation.store();

        switch (type) {
            case TEXT:
                addTextField(doc, fieldName, value.toString(), sort, store);
                break;
            case STRING:
                addStringField(doc, fieldName, value.toString(), sort, store);
                break;
            case LONG:
                addLongField(doc, fieldName, (Long) value, sort, store);
                break;
            case INTEGER:
                addIntegerField(doc, fieldName, (Integer) value, sort, store);
                break;
            case DOUBLE:
                addDoubleField(doc, fieldName, (Double) value, sort, store);
                break;
            default:
                throw new IllegalArgumentException("Unknown field type: " + type);
        }
    }

    /**
     * 添加文本字段
     */
    private void addTextField(org.apache.lucene.document.Document doc, String fieldName, String value, boolean sort, boolean store) {
        // 文本字段用于全文搜索
        doc.add(new TextField(fieldName, value, store ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));

        // 如果需要排序，添加 SortedDocValuesField
        if (sort) {
            doc.add(new SortedDocValuesField(fieldName, new BytesRef(value)));
        }
    }

    /**
     * 添加字符串字段
     */
    private void addStringField(org.apache.lucene.document.Document doc, String fieldName, String value, boolean sort, boolean store) {
        // 字符串字段用于精确匹配
        doc.add(new StringField(fieldName, value, store ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));

        // 如果需要排序，添加 SortedDocValuesField
        if (sort) {
            doc.add(new SortedDocValuesField(fieldName, new BytesRef(value)));
        }
    }

    /**
     * 添加长整型字段
     */
    private void addLongField(org.apache.lucene.document.Document doc, String fieldName, Long value, boolean sort, boolean store) {
        // LongPoint 用于范围查询
        doc.add(new LongPoint(fieldName, value));

        // 如果需要排序，添加 NumericDocValuesField
        if (sort) {
            doc.add(new NumericDocValuesField(fieldName, value));
        }

        // 如果需要存储，添加 StoredField
        if (store) {
            doc.add(new StoredField(fieldName, value));
        }
    }

    /**
     * 添加整型字段
     */
    private void addIntegerField(org.apache.lucene.document.Document doc, String fieldName, Integer value, boolean sort, boolean store) {
        // 转换为 Long 处理
        Long longValue = value.longValue();
        doc.add(new LongPoint(fieldName, longValue));

        if (sort) {
            doc.add(new NumericDocValuesField(fieldName, longValue));
        }

        if (store) {
            doc.add(new StoredField(fieldName, longValue));
        }
    }

    /**
     * 添加双精度浮点字段
     */
    private void addDoubleField(org.apache.lucene.document.Document doc, String fieldName, Double value, boolean sort, boolean store) {
        // DoublePoint 用于范围查询
        doc.add(new org.apache.lucene.document.DoublePoint(fieldName, value));

        // 如果需要排序，添加 SortedNumericDocValuesField
        if (sort) {
            long encoded = Double.doubleToLongBits(value);
            doc.add(new org.apache.lucene.document.SortedNumericDocValuesField(fieldName, encoded));
        }

        if (store) {
            doc.add(new StoredField(fieldName, value));
        }
    }
}
