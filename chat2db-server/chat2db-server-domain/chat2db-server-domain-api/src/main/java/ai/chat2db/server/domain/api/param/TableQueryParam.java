package ai.chat2db.server.domain.api.param;

import java.io.Serial;

import com.jayway.jsonpath.internal.function.sequence.Index;

import jakarta.validation.constraints.NotNull;

import ai.chat2db.server.tools.base.wrapper.param.QueryParam;
import ai.chat2db.spi.model.BaseModel;
import ai.chat2db.spi.model.IndexModel;
import ai.chat2db.spi.model.TableColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 查询表信息
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TableQueryParam extends QueryParam implements BaseModel<IndexModel>{
    @Serial
    private static final long serialVersionUID = -8918610899872508804L;
    /**
     * 对应数据库存储的来源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * 对应的连接数据库名称
     */
    @NotNull
    private String databaseName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 空间名
     */
    private String schemaName;

    private boolean refresh;


    private Class<? extends IndexModel> classType;

}
