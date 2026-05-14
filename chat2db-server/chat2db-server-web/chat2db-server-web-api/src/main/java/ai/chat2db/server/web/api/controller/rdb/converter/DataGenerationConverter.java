package ai.chat2db.server.web.api.controller.rdb.converter;

import ai.chat2db.server.domain.api.param.ColumnConfigParam;
import ai.chat2db.server.domain.api.param.DataGenerationRequest;
import ai.chat2db.server.web.api.controller.rdb.request.DataGenerationRequestVO;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

public class DataGenerationConverter {

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
        request.setBatchSize(vo.getBatchSize());
        request.setPreviewMode(vo.getPreviewMode());

        if (!CollectionUtils.isEmpty(vo.getColumnConfigs())) {
            List<ColumnConfigParam> columnConfigs = new ArrayList<>();
            for (DataGenerationRequestVO.ColumnConfigVO voConfig : vo.getColumnConfigs()) {
                ColumnConfigParam param = new ColumnConfigParam();
                param.setColumnName(voConfig.getColumnName());
                param.setDataType(voConfig.getDataType());
                param.setGenerationType(voConfig.getGenerationType());
                param.setSubType(voConfig.getSubType());
                param.setComment(voConfig.getComment());
                param.setNullable(voConfig.getNullable());
                param.setMaxLength(voConfig.getMaxLength());
                param.setScale(voConfig.getScale());
                param.setCustomParams(voConfig.getCustomParams());
                columnConfigs.add(param);
            }
            request.setColumnConfigs(columnConfigs);
        }

        return request;
    }
}
