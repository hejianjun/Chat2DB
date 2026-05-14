package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.DataGenerationRequest;
import ai.chat2db.server.domain.api.param.ColumnConfigParam;
import ai.chat2db.server.domain.api.vo.DataGenerationPreviewVO;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

import java.util.Map;

/**
 * 数据生成服务接口
 */
public interface DataGenerationService {

    /**
     * 获取表列信息用于数据生成配置
     *
     * @param request 请求数据
     * @return 列配置列表
     */
    ListResult<ColumnConfigParam> getTableColumns(DataGenerationRequest request);

    /**
     * AI推断列的数据生成类型
     *
     * @param request 请求数据
     * @return 列名到生成类型的映射
     */
    DataResult<Map<String, String>> aiInferGenerationTypes(DataGenerationRequest request);

    /**
     * 生成数据预览（10行）
     *
     * @param request 请求数据
     * @return 预览数据
     */
    DataResult<DataGenerationPreviewVO> generatePreview(DataGenerationRequest request);

    /**
     * 执行数据生成（异步任务）
     *
     * @param request 请求数据
     * @return 任务ID
     */
    DataResult<Long> executeDataGeneration(DataGenerationRequest request);

    /**
     * 获取支持的数据生成类型列表
     *
     * @return 生成类型列表
     */
    ListResult<String> getSupportedGenerationTypes();
}
