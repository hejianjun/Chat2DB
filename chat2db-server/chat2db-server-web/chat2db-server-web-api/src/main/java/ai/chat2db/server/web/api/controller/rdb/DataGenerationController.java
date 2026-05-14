package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.domain.api.param.DataGenerationRequest;
import ai.chat2db.server.domain.api.service.DataGenerationService;
import ai.chat2db.server.domain.api.vo.DataGenerationPreviewVO;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.rdb.request.DataGenerationRequestVO;
import ai.chat2db.server.web.api.controller.rdb.converter.DataGenerationConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据生成控制器
 */
@Slf4j
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/rdb/table/generate-data")
public class DataGenerationController {

    @Autowired
    private DataGenerationService dataGenerationService;

    /**
     * 获取表列信息用于数据生成配置
     */
    @PostMapping("/config")
    public ListResult<ai.chat2db.server.domain.api.param.ColumnConfigParam> getTableColumns(
            @RequestBody DataGenerationRequestVO requestVO) {
        try {
            DataGenerationRequest request = DataGenerationConverter.voToRequest(requestVO);
            return dataGenerationService.getTableColumns(request);
        } catch (Exception e) {
            log.error("Failed to get table columns for data generation", e);
            return ListResult.error("获取表列信息失败: " + e.getMessage(), null);
        }
    }


    /**
     * 生成数据预览（10行）
     */
    @PostMapping("/preview")
    public DataResult<DataGenerationPreviewVO> generatePreview(
            @RequestBody DataGenerationRequestVO requestVO) {
        try {
            DataGenerationRequest request = DataGenerationConverter.voToRequest(requestVO);
            request.setPreviewMode(true);
            request.setRowCount(10);
            return dataGenerationService.generatePreview(request);
        } catch (Exception e) {
            log.error("Failed to generate data preview", e);
            return DataResult.error("GENERATE_PREVIEW_ERROR", "生成预览失败: " + e.getMessage());
        }
    }

    /**
     * 执行数据生成（异步任务）
     */
    @PostMapping("/execute")
    public DataResult<Long> executeDataGeneration(
            @RequestBody DataGenerationRequestVO requestVO) {
        try {
            DataGenerationRequest request = DataGenerationConverter.voToRequest(requestVO);
            return dataGenerationService.executeDataGeneration(request);
        } catch (Exception e) {
            log.error("Failed to execute data generation", e);
            return DataResult.error("EXECUTE_GENERATION_ERROR", "执行数据生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的数据生成类型列表
     */
    @GetMapping("/supported-types")
    public ListResult<String> getSupportedGenerationTypes() {
        try {
            return dataGenerationService.getSupportedGenerationTypes();
        } catch (Exception e) {
            log.error("Failed to get supported generation types", e);
            return ListResult.error("获取支持的生成类型失败: " + e.getMessage(), null);
        }
    }
}
