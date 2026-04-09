package ai.chat2db.spi.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;


/**
 * 表元数据类，用于描述表的列类型、字符集、排序规则和索引类型等信息
 */
@Data
@Builder
public class TableMeta {

    /**
     * 列类型列表，描述表中各列的数据类型信息
     */
    private List<ColumnType> columnTypes;


    /**
     * 字符集列表，定义了表中各列可能使用的字符集信息
     */
    private List<Charset> charsets;


    /**
     * 排序规则列表，描述了表中各列的排序规则信息
     */
    private List<Collation> collations;


    /**
     * 索引类型列表，定义了表中可能的索引类型信息
     */
    private List<IndexType> indexTypes;

    /**
     * 默认值列表，包含表中各列的默认值信息
     */
    private List<DefaultValue> defaultValues;
}
