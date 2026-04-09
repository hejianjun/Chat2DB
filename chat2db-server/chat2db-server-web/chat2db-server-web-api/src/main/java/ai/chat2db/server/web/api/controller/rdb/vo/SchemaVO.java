
package ai.chat2db.server.web.api.controller.rdb.vo;

import lombok.Data;

/**
 * @author jipengfei
 * @version : SchemaVO.java
 */
@Data
public class SchemaVO {
    /**
     * 数据名字
     */
    private String name;
    /**
     * 子节点类型
     */
    private String treeNodeType;
}