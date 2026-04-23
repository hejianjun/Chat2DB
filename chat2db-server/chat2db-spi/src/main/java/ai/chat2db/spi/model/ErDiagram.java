package ai.chat2db.spi.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * er图
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ErDiagram {

    private List<TableNode> tables;
    private List<RelationEdge> relations;
    private DatabaseInfo databaseInfo;
    private LayoutInfo layout;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableNode {
        private String id;
        private String name;
        private String schema;
        private String comment;
        private List<TableColumn> columns;
        private List<TableIndex> indexes;
        private String color;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableColumn {
        private String name;
        private String dataType;
        private Integer dataLength;
        private Integer dataScale;
        private Boolean nullable;
        private String defaultValue;
        private String comment;
        private Boolean isPrimaryKey;
        private Boolean isAutoIncrement;
        private Boolean isForeignKey;
        private String referencedTable;
        private String referencedColumn;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableIndex {
        private String name;
        private IndexType type;
        private List<String> columns;

        public enum IndexType {
            PRIMARY, UNIQUE, INDEX
        }
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationEdge {
        private String id;
        private RelationEnd source;
        private RelationEnd target;
        private RelationType type;
        private String name;
        private String description;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationEnd {
        private String tableId;
        private String columnName;
    }

    public enum RelationType {
        ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseInfo {
        private String name;
        private String type;
        private Integer tableCount;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayoutInfo {
        private LayoutType type;
        private Map<String, Position> positions;
    }

    public enum LayoutType {
        FORCE, HIERARCHICAL, ORTHOGONAL, CIRCULAR
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position {
        private Double x;
        private Double y;
    }

    @Deprecated
    private List<Node> nodes;
    @Deprecated
    private List<Edge> edges;

    @Deprecated
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private String id;
        private String name;
    }

    @Deprecated
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        private String id;
        private String source;
        private String target;
        private String description;
    }
}
