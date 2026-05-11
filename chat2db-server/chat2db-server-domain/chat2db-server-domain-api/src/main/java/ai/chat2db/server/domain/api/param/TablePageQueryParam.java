package ai.chat2db.server.domain.api.param;

import ai.chat2db.server.tools.base.wrapper.param.PageQueryParam;
import ai.chat2db.spi.model.BaseModel;
import ai.chat2db.spi.model.LuceneField;
import ai.chat2db.spi.model.LuceneFieldType;
import ai.chat2db.spi.model.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分页查询表信息
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TablePageQueryParam extends PageQueryParam implements BaseModel<Table> {
    private static final long serialVersionUID = 8054519332890887747L;
    /**
     * 对应数据库存储的来源id
     */
    @NotNull
    @LuceneField(name = "dataSourceId", type = LuceneFieldType.STRING)
    private Long dataSourceId;

    /**
     * 对应的连接数据库名称
     */
    @NotNull
    @LuceneField(name = "databaseName", type = LuceneFieldType.STRING)
    private String databaseName;

    /**
     * 表名
     */
    @LuceneField(name = "tableName", type = LuceneFieldType.STRING)
    private String tableName;


    /**
     * 模式名
     */
    @LuceneField(name = "schemaName", type = LuceneFieldType.STRING)
    private String schemaName;



    /**
     * if true, refresh the cache
     */
    private boolean refresh;


    private String searchKey;

    /**
     * 排序字段: name, rowCount
     */
    private String sortField;

    /**
     * 排序方向: ascend, descend
     */
    private String sortOrder;

    @Override
    public Class<Table> getClassType() {
        return Table.class;
    }

}
