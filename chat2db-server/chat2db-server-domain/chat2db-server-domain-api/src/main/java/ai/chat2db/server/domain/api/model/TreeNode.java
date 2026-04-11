package ai.chat2db.server.domain.api.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreeNode {

    private String uuid;

    private String key;

    private String name;

    private String treeNodeType;

    private String pretendNodeType;

    private String comment;

    private Boolean isLeaf;

    private Boolean pinned;

    private List<String> parentPath;

    private Map<String, Object> extraParams;
}