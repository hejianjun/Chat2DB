package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.ErDiagramQueryParam;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.spi.model.ErDiagram;

/**
 * ER图服务接口
 */
public interface ErDiagramService {

    /**
     * 查询ER图数据
     * 根据数据库中的表和外键关系构建ER图数据
     *
     * @param param 查询参数，包含数据源、数据库、schema、过滤条件等
     * @return ER图数据，包含节点（表）和边（外键关系）
     */
    DataResult<ErDiagram> queryErDiagram(ErDiagramQueryParam param);
}