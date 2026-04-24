package ai.chat2db.spi.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * ER图模型，用于表示数据库表之间的关系图
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ErDiagram {

    /**
     * 节点列表，每个节点代表一张表
     */
    private List<Node> nodes;

    /**
     * 边列表，每条边代表表之间的外键关系
     */
    private List<Edge> edges;

    /**
     * ER图节点，代表一张数据库表
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        /**
         * 节点唯一标识，使用表名
         */
        private String id;
        /**
         * 表名
         */
        private String name;
        /**
         * 表注释
         */
        private String comment;
        /**
         * 表的列数量
         */
        private Integer columnCount;
    }

    /**
     * ER图边，代表表之间的外键关系
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        /**
         * 边唯一标识，使用外键名或生成的ID
         */
        private String id;
        /**
         * 源表名（拥有外键的表）
         */
        private String source;
        /**
         * 目标表名（被引用的表）
         */
        private String target;
        /**
         * 源表的外键列名
         */
        private String sourceColumn;
        /**
         * 目标表被引用的列名
         */
        private String targetColumn;
        /**
         * 关系描述，格式：sourceColumn -> targetColumn
         */
        private String label;
        /**
         * 是否为虚拟外键（根据命名规范推断的外键）
         */
        private Boolean virtual;
    }

}