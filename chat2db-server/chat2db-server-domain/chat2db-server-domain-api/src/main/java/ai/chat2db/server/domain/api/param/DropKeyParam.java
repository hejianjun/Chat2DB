package ai.chat2db.server.domain.api.param;

import ai.chat2db.spi.model.BaseModel;
import ai.chat2db.spi.model.IndexModel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 删除表结构
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DropKeyParam implements BaseModel<IndexModel>{
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
     * 模式名
     */
    private String schemaName;

    /**
     * 删除key名
     */
    private String keyName;

    private Class<? extends IndexModel> classType;
}
