package ai.chat2db.server.web.api.controller.rdb.converter;

import ai.chat2db.server.domain.api.param.DataGenerationRequest;
import ai.chat2db.server.web.api.controller.rdb.request.DataGenerationRequestVO;
import org.springframework.stereotype.Component;

/**
 * 数据生成转换器
 */
@Component
public class DataGenerationConverter {

    /**
     * VO转换为请求参数
     */
    public static DataGenerationRequest voToRequest(DataGenerationRequestVO vo) {
        if (vo == null) {
            return null;
        }

        DataGenerationRequest request = new DataGenerationRequest();
        request.setDataSourceId(vo.getDataSourceId());
        request.setDatabaseName(vo.getDatabaseName());
        request.setSchemaName(vo.getSchemaName());
        request.setTableName(vo.getTableName());
        request.setRowCount(vo.getRowCount());
        request.setColumnConfigs(vo.getColumnConfigs());
        request.setBatchSize(vo.getBatchSize());
        request.setPreviewMode(vo.getPreviewMode());

        return request;
    }
}
