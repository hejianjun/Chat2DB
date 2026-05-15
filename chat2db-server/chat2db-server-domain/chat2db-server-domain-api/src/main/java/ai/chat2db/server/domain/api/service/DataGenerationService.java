package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.DataGenerationRequest;
import ai.chat2db.server.domain.api.param.ColumnConfigParam;
import ai.chat2db.server.domain.api.param.GeneratorTemplate;
import ai.chat2db.server.domain.api.vo.DataGenerationPreviewVO;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

import java.util.List;

public interface DataGenerationService {

    ListResult<ColumnConfigParam> getTableColumns(DataGenerationRequest request);

    DataResult<DataGenerationPreviewVO> generatePreview(DataGenerationRequest request);

    DataResult<Long> executeDataGeneration(DataGenerationRequest request);

    ListResult<GeneratorTemplate> getAllGeneratorTemplates();
}
