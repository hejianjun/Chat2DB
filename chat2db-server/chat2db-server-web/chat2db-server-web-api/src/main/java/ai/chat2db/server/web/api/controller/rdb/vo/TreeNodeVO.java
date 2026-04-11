package ai.chat2db.server.web.api.controller.rdb.vo;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class TreeNodeVO {

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