package ai.chat2db.spi.model;

import java.util.List;

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

    private List<Node> nodes;
    private List<Edge> edges;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private String id;
        private String name;
    }

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
