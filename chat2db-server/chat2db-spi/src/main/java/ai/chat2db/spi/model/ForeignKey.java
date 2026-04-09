package ai.chat2db.spi.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ForeignKey implements IndexModel {


    // 外键名称
    @JsonAlias({"FK_NAME"})
    private String name;
    // 当前表
    @JsonAlias({"FKTABLE_NAME"})
    private String tableName;

    /**
     * 索引所属schema
     */
    @JsonAlias({"PKTABLE_SCHEM"})
    private String schemaName;

    /**
     * 数据库名
     */
    @JsonAlias({"PKTABLE_CAT"})
    private String databaseName;
    // 当前表的列
    @JsonAlias({"FKCOLUMN_NAME"})
    private String column;
    // 引用的表
    @JsonAlias({"PKTABLE_NAME"})
    private String referencedTable;
    // 引用的列
    @JsonAlias({"PKCOLUMN_NAME"})
    private String referencedColumn;

    /**
     * 更新规则
     * @see java.sql.DatabaseMetaData#importedKeyCascade
     * @see java.sql.DatabaseMetaData#importedKeyRestrict
     * @see java.sql.DatabaseMetaData#importedKeySetNull
     * @see java.sql.DatabaseMetaData#importedKeyNoAction
     */
    @JsonAlias({"UPDATE_RULE"})
    private int updateRule;
    /**
     * 删除规则
     * @see java.sql.DatabaseMetaData#importedKeyCascade
     * @see java.sql.DatabaseMetaData#importedKeyRestrict
     * @see java.sql.DatabaseMetaData#importedKeySetNull
     * @see java.sql.DatabaseMetaData#importedKeyNoAction
     */
    @JsonAlias({"DELETE_RULE"})
    private int deleteRule;

    // 备注（可选）
    @JsonAlias({"COMMENT"})
    private String comment;


    private String editStatus;

    /**
     * AI生成的注释
     */
    private String aiComment;

    /**
     * 版本
     */
    private Long version;

}
