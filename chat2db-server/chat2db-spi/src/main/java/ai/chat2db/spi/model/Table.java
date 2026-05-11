package ai.chat2db.spi.model;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 表信息
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Table implements IndexModel {

    /**
     * 表名
     */
    @JsonAlias({ "TABLE_NAME" })
    private String name;

    /**
     * 描述
     */
    @JsonAlias({ "REMARKS" })

    private String comment;

    /**
     * DB 名
     */
    @JsonAlias({ "TABLE_SCHEM" })

    private String schemaName;

    /**
     * 列列表
     */
    @lombok.Builder.Default
    private List<TableColumn> columnList = Collections.emptyList();

    /**
     * 索引列表
     */
    @lombok.Builder.Default
    private List<TableIndex> indexList = Collections.emptyList();

    /**
     * 外键列表
     */
    private List<ForeignKey> foreignKeyList;

    /**
     * 虚拟外键
     */
    private List<VirtualForeignKey> virtualForeignKeyList;

    /**
     * DB类型
     */
    private String dbType;

    /**
     * 数据库名
     */
    @JsonAlias("TABLE_CAT")
    private String databaseName;

    /**
     * 表类型
     */
    @JsonAlias("TABLE_TYPE")
    private String type;

    /**
     * 是否置顶
     */
    private boolean pinned;

    /**
     * 是否已废弃
     */
    private boolean deprecated;

    /**
     * ddl
     */
    private String ddl;

    // 数据表的存储引擎信息
    private String engine;

    // 数据表的字符集信息
    private String charset;

    // 数据表的排序规则信息
    private String collate;

    // 数据表中自增字段的增量值
    private Long incrementValue;

    // 数据表的分区信息
    private String partition;

    // 数据表所属的表空间信息
    private String tablespace;

    /**
     * AI生成的注释
     */
    private String aiComment;
    /**
     * 版本
     */
    private Long version;

    /**
     * 预估行数
     */
    private Long rowCount;

    @Override
    public String getTableName() {
        return this.name;
    }

    @Override
    public void setTableName(String tableName) {
        this.name = tableName;
    }
}
