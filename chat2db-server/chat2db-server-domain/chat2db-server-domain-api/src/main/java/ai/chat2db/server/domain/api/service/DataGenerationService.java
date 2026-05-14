package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.DataGenerationRequest;
import ai.chat2db.server.domain.api.param.ColumnConfigParam;
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import ai.chat2db.server.domain.api.vo.DataGenerationPreviewVO;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

public interface DataGenerationService {

    ListResult<ColumnConfigParam> getTableColumns(DataGenerationRequest request);

    DataResult<DataGenerationPreviewVO> generatePreview(DataGenerationRequest request);

    DataResult<Long> executeDataGeneration(DataGenerationRequest request);

    ListResult<String> getSupportedGenerationTypes();

    ListResult<GeneratorMetadata> getAllGeneratorMetadata();
}
