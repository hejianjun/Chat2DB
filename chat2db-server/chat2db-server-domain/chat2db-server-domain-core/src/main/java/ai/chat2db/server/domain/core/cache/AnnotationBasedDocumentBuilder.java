package ai.chat2db.server.domain.core.cache;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * 使用缓存避免重复反射，提升性能
 */
public class AnnotationBasedDocumentBuilder {

    /**
     * 带注解的字段信息
     */
    static class AnnotatedFieldInfo {
        final Field field;
        final LuceneField annotation;

        AnnotatedFieldInfo(Field field, LuceneField annotation) {
            this.field = field;
            this.annotation = annotation;
        }
    }

    /**
     * 缓存类的字段信息，key 为类名，value 为带注解的字段列表
     */
    private final Map<String, List<AnnotatedFieldInfo>> fieldCache = new ConcurrentHashMap<>();

    /**
     * 缓存类的 STRING 类型字段（用于过滤），key 为类名，value 为 STRING 类型字段列表
     */
    private final Map<String, List<AnnotatedFieldInfo>> stringFieldCache = new ConcurrentHashMap<>();

    /**
     * 获取 STRING 类型的字段列表（用于构建查询过滤）
     * STRING 类型字段自动用于精确匹配过滤
     */
    public List<AnnotatedFieldInfo> getStringFields(Class<?> clazz) {
        return stringFieldCache.computeIfAbsent(clazz.getName(), k -> collectStringFields(clazz));
    }

    /**
     * 收集 STRING 类型的字段
     */
    private List<AnnotatedFieldInfo> collectStringFields(Class<?> clazz) {
        List<AnnotatedFieldInfo> stringFields = new ArrayList<>();
        collectStringFieldsRecursive(clazz, stringFields);
        return stringFields;
    }

    /**
     * 递归收集 STRING 类型的字段
     */
    private void collectStringFieldsRecursive(Class<?> clazz, List<AnnotatedFieldInfo> stringFields) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        collectStringFieldsRecursive(clazz.getSuperclass(), stringFields);

        for (Field field : clazz.getDeclaredFields()) {
            LuceneField annotation = field.getAnnotation(LuceneField.class);
            if (annotation != null && annotation.type() == LuceneFieldType.STRING) {
                field.setAccessible(true);
                stringFields.add(new AnnotatedFieldInfo(field, annotation));
            }
        }
    }

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

        // 从缓存获取或解析字段信息
        List<AnnotatedFieldInfo> annotatedFields = fieldCache.computeIfAbsent(clazz.getName(), k -> collectAnnotatedFields(clazz));

        // 直接使用缓存的字段信息构建文档
        for (AnnotatedFieldInfo info : annotatedFields) {
            Object value = info.field.get(source);
            addFieldToDocument(doc, info.annotation, value);
        }

        return doc;
    }

    /**
     * 收集类及其父类中所有带 @LuceneField 注解的字段
     */
    private List<AnnotatedFieldInfo> collectAnnotatedFields(Class<?> clazz) {
        List<AnnotatedFieldInfo> fields = new ArrayList<>();
        collectAnnotatedFieldsRecursive(clazz, fields);
        return fields;
    }

    /**
     * 递归收集字段（从父类到子类）
     */
    private void collectAnnotatedFieldsRecursive(Class<?> clazz, List<AnnotatedFieldInfo> fields) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        // 先处理父类
        collectAnnotatedFieldsRecursive(clazz.getSuperclass(), fields);

        // 再处理当前类
        for (Field field : clazz.getDeclaredFields()) {
            LuceneField annotation = field.getAnnotation(LuceneField.class);
            if (annotation != null) {
                field.setAccessible(true);
                fields.add(new AnnotatedFieldInfo(field, annotation));
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
