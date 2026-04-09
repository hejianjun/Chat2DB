package ai.chat2db.server.domain.core.cache;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.wltea.analyzer.lucene.IKAnalyzer;

import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Sets;

import ai.chat2db.spi.model.BaseModel;
import ai.chat2db.spi.model.IndexModel;
import ai.chat2db.spi.model.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

/**
 * 类LuceneIndexManager用于管理Lucene全文索引的创建、更新和查询
 * 它实现了AutoCloseable接口，支持使用try-with-resources语句自动关闭资源
 */
public class LuceneIndexManager<T extends IndexModel> implements AutoCloseable {
    /**
     * 索引、分析器、写入器、读者和搜索者实例变量
     */
    private Directory index;
    private Analyzer analyzer;
    private IndexWriter writer;
    private IndexReader reader;
    private IndexSearcher searcher;

    @Getter
    private Integer lastDocId;

    @Getter
    private long total = 0;

    /**
     * 读写锁
     */
    @Getter
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private static final String[] TEXT_FIELDS = { "name", "comment", "aiComment" };

    /**
     * 构造函数，根据给定的ID初始化Lucene索引管理器
     *
     * @param id 用于确定索引文件路径的ID
     */
    @SneakyThrows
    public LuceneIndexManager(@NotNull Long id) {
        String indexPath = getIndexPath(id);
        this.index = FSDirectory.open(Paths.get(indexPath));
        this.analyzer = new IKAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        this.writer = new IndexWriter(index, config);
        this.reader = DirectoryReader.open(writer);
        this.searcher = new IndexSearcher(reader);
    }

    @SneakyThrows
    public <E extends BaseModel> Long getMaxVersion(E queryModel) {
        // 创建查询条件
        BooleanQuery query = buildBooleanQuery(queryModel).build();

        // 创建按版本号降序排序的排序规则
        // true表示降序
        Sort sort = new Sort(new SortField("version", SortField.Type.LONG, true));

        // 执行查询，按版本号降序排序，只取第一个文档
        TopDocs topDocs = searcher.search(query, 1, sort);

        if (topDocs.totalHits.value == 0) {
            return null;
        }

        // 获取匹配的最高版本号文档
        Document document = searcher.doc(topDocs.scoreDocs[0].doc, Collections.singleton("version"));
        IndexableField versionField = document.getField("version");

        return versionField != null ? (Long) versionField.numericValue() : null;
    }

    /**
     * 根据ID和环境获取索引的文件路径
     *
     * @param id 用于确定索引文件路径的ID
     * @return 索引的文件路径
     */
    private String getIndexPath(Long id) {
        String environment = StringUtils.defaultString(System.getProperty("spring.profiles.active"), "dev");
        String basePath = System.getProperty("user.home") + "/.chat2db/index/";
        switch (environment.toLowerCase()) {
            case "test":
                return basePath + id + "_test";
            case "dev":
            default:
                return basePath + id + "_dev";
        }
    }

    /**
     * 释放资源，关闭索引读者、写入器和目录
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (index != null) {
            index.close();
        }
    }

    /**
     * 重建Reader和Searcher
     */
    @SneakyThrows
    private void reload() {
        writer.commit();
        if (this.reader != null) {
            this.reader.close();
        }
        this.reader = DirectoryReader.open(writer);
        this.searcher = new IndexSearcher(reader);
    }

    /**
     * 构建旧数据映射表
     *
     * @param model     实体类型（用于构建查询条件）
     * @param maxHits 最大命中数
     * @return 以"name"为Key的旧数据映射表
     */
    @SneakyThrows
    private <E extends IndexModel> Map<String, E> buildSourceMap(E model, int maxHits) {
        BooleanQuery query = buildBooleanQuery(model).build();
        TopDocs topDocs = searcher.search(query, maxHits);
        total = topDocs.totalHits.value;
        if (total == 0) {
            return Collections.emptyMap();
        }
        return Arrays.stream(topDocs.scoreDocs)
                .map(scoreDoc -> (E) getDocument(model.getClassType(), scoreDoc.doc))
                .collect(Collectors.toMap(
                        IndexModel::getName,
                        obj -> obj,
                        // 重复时保留最新值
                        (oldVal, newVal) -> newVal
                ));
    }

    /**
     * 批量更新文档到Lucene索引
     * 逻辑说明：
     * 1. 参数校验
     * 2. 类型一致性检查
     * 3. 准备搜索环境
     * 4. 构建旧数据映射表
     * 5. 批量创建文档并更新
     */
    @SneakyThrows
    public void updateDocuments(List<? extends T> sources, Long version) {
        if (CollectionUtils.isEmpty(sources)) {
            return;
        }
        T model = sources.get(0);
        // 兼容Table类型
        if (model instanceof Table) {
            Table table = new Table();
            table.setDatabaseName(model.getDatabaseName());
            table.setSchemaName(model.getSchemaName());
            model = (T) table;
        }
        lock.writeLock().lock();
        try {
            // 获取全部相关旧数据
            Map<String, T> sourceMap = buildSourceMap(model, 1000);
            BooleanQuery query = buildBooleanQuery(model).build();
            List<Document> docs = sources.stream()
                    .peek(source -> source.setVersion(version))
                    .map(source -> createDocument(source, sourceMap))
                    .collect(Collectors.toList());

            writer.updateDocuments(query, docs);
            reload();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 更新单个文档到Lucene索引
     * 逻辑说明：
     * 1. 参数校验
     * 2. 准备搜索环境
     * 3. 构建旧数据映射表
     * 4. 创建新文档并更新
     */
    @SneakyThrows
    public void updateDocument(T source) {
        if (source == null) {
            throw new IllegalArgumentException("Source must not be null");
        }
        lock.writeLock().lock();
        try {
            Map<String, T> sourceMap = buildSourceMap(source, 1);
            Document document = createDocument(source, sourceMap);
            BooleanQuery query = buildBooleanQuery(source).build();
            writer.updateDocuments(query, Collections.singletonList(document));
            reload();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 构建更新文档时使用的布尔查询
     *
     * @return 构建的布尔查询对象
     */
    private <E extends BaseModel<?>> BooleanQuery.Builder buildBooleanQuery(E model) {
        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();

        addTermQuery(booleanQuery, "type", model.getClassType().getSimpleName(), BooleanClause.Occur.FILTER);
        // 添加数据库名称条件
        addTermQuery(booleanQuery, "databaseName", model.getDatabaseName(), BooleanClause.Occur.FILTER);

        // 添加架构名称条件
        addTermQuery(booleanQuery, "schemaName", model.getSchemaName(), BooleanClause.Occur.FILTER);

        // 添加表名称条件
        addTermQuery(booleanQuery, "tableName", model.getTableName(), BooleanClause.Occur.FILTER);

        return booleanQuery;
    }

    /**
     * 向布尔查询构建器中添加术语查询
     *
     * @param booleanQuery 布尔查询构建器
     * @param field        字段名
     * @param value        字段值
     * @param occur        查询条款的发生关系
     */
    private void addTermQuery(BooleanQuery.Builder booleanQuery, String field, String value,
            BooleanClause.Occur occur) {
        if (StringUtils.isNotBlank(value)) {
            Query query = new TermQuery(new Term(field, value));
            booleanQuery.add(query, occur);
        }
    }

    /**
     * 根据实体对象创建Lucene文档
     * 逻辑说明：
     * 1. 添加类型标识字段
     * 2. 处理AI注释字段继承逻辑
     * 3. 动态添加预定义字段
     * 4. 保留原始数据快照
     *
     * @param source    源实体对象
     * @param sourceMap 旧数据映射表（用于字段继承）
     * @return 构建完成的Lucene文档
     */
    private Document createDocument(T source, Map<String, T> sourceMap) {
        Document doc = new Document();

        // 1. 添加类型标识字段
        String typeName = source.getClassType().getSimpleName();
        addStringField(doc, "type", typeName);

        // 新增版本冲突检测逻辑
        Long incomingVersion = source.getVersion();

        // 继承旧版本号或初始化
        Long storedVersion = getStoredVersion(source, sourceMap);
        if (storedVersion != null) {
            if (incomingVersion != null && incomingVersion < storedVersion) {
                throw new ConcurrentModificationException(
                        String.format("Data version conflict detected incomingVersion:%s storedVersion:%s",
                                incomingVersion, storedVersion));
            }
            long newVersion = storedVersion + 1;
            doc.add(new StoredField("version", newVersion));
            doc.add(new NumericDocValuesField("version", newVersion));
            source.setVersion(newVersion);
        } else {
            doc.add(new StoredField("version", 1L));
            doc.add(new NumericDocValuesField("version", 1L));
            source.setVersion(1L);
        }

        // 3. AI注释继承逻辑（新数据为空时从旧数据获取）
        handleAiCommentInheritance(source, sourceMap);

        // 4. 添加预定义字段（文本型+字符串型）
        addStringField(doc, "databaseName", source.getDatabaseName());
        addStringField(doc, "schemaName", source.getSchemaName());
        addStringField(doc, "tableName", source.getTableName());
        addTextField(doc, "name", source.getName());
        addTextField(doc, "comment", source.getComment());
        addTextField(doc, "aiComment", source.getAiComment());

        // 5. 添加名称字段别名（typeName + "Name"）
        Optional.ofNullable(source.getName())
                .ifPresent(name -> addStringField(doc, typeName + "Name", name));

        // 6. 存储原始数据快照
        doc.add(new StoredField("source", JSONObject.toJSONString(source)));
        return doc;
    }

    /**
     * 新增版本获取方法
     */
    private Long getStoredVersion(T source, Map<String, T> sourceMap) {
        String nameValue = source.getName();
        if (nameValue == null || sourceMap == null) {
            return null;
        }

        T oldData = sourceMap.get(nameValue);
        return oldData != null ? oldData.getVersion() : null;
    }

    // 辅助方法：处理AI注释继承
    private void handleAiCommentInheritance(T source, Map<String, T> sourceMap) {
        String nameValue = source.getName();
        if (nameValue == null) {
            return;
        }

        String aiComment = source.getAiComment();
        if (aiComment == null && sourceMap != null) {
            T oldData = sourceMap.get(nameValue);
            if (oldData != null) {
                source.setAiComment(oldData.getAiComment());
            }
        }
    }

    /**
     * 向文档中添加文本字段
     *
     * @param doc       文档对象
     * @param fieldName 字段名
     * @param value     字段值
     */
    private void addTextField(Document doc, String fieldName, String value) {
        if (value != null) {
            doc.add(new TextField(fieldName, value, Field.Store.NO));
        }
    }

    /**
     * 向文档中添加字符串字段
     *
     * @param doc       文档对象
     * @param fieldName 字段名
     * @param value     字段值
     */
    private void addStringField(Document doc, String fieldName, String value) {
        if (value != null) {
            doc.add(new StringField(fieldName, value, Field.Store.NO));
        }
    }

    /**
     * 搜索文档
     *
     * @param lastDocId 上一次搜索结果中的最后一个文档ID，用于分页搜索
     * @param queryStr  搜索查询字符串
     * @return 搜索结果的TopDocs对象
     */
    @SneakyThrows
    public <E extends BaseModel> List<T> search(E queryModel, Integer lastDocId, String queryStr) {
        lock.readLock().lock();
        try {
            BooleanQuery booleanQuery = buildSearchQuery(queryModel, queryStr);
            ScoreDoc lastScoreDoc = null;
            if (lastDocId != null) {
                lastScoreDoc = new ScoreDoc(lastDocId, 1);
            }
            TopDocs topDocs = searcher.searchAfter(lastScoreDoc, booleanQuery, 1000);
            return Arrays.stream(topDocs.scoreDocs)
                    .map(scoreDoc -> {
                        T doc = (T) getDocument(queryModel.getClassType(), scoreDoc.doc);
                        this.lastDocId = scoreDoc.doc;
                        return doc;
                    })
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 构建搜索查询
     *
     * @param queryStr 搜索查询字符串
     * @return 构建的布尔查询对象
     */
    @SneakyThrows
    private <E extends BaseModel<T>> BooleanQuery buildSearchQuery(E queryModel, String queryStr) {
        BooleanQuery.Builder booleanQuery = buildBooleanQuery(queryModel);
        if (StringUtils.isBlank(queryStr)) {
            booleanQuery.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        } else {
            MultiFieldQueryParser multiParser = new MultiFieldQueryParser(TEXT_FIELDS, analyzer);
            multiParser.setDefaultOperator(QueryParser.Operator.AND);
            Query query = multiParser.parse(queryStr);
            booleanQuery.add(query, BooleanClause.Occur.MUST);
        }
        return booleanQuery.build();
    }

    /**
     * 获取指定ID的文档，可指定需要加载的字段
     *
     * @param docId 文档ID
     * @return 加载的文档对象
     */
    @SneakyThrows
    private <E extends IndexModel> E getDocument(Class<E> clz, int docId) {
        StoredFields storedFields = searcher.storedFields();
        Document document = storedFields.document(docId, Sets.newHashSet("source"));
        String source = document.get("source");
        return JSONObject.parseObject(source, clz);
    }

}
