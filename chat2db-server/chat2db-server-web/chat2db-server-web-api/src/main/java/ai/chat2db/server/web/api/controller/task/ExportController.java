package ai.chat2db.server.web.api.controller.task;

import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.rdb.request.DataExportRequest;
import ai.chat2db.server.web.api.controller.task.biz.TaskBizService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据导出控制器
 * 提供数据导出和文档导出功能的REST API接口
 */
@ConnectionInfoAspect
@RequestMapping("/api/export")
@RestController
@Slf4j
public class ExportController {

    @Autowired
    private TaskBizService taskBizService;


    /**
     * 导出数据
     * 将查询结果数据导出为文件
     *
     * @param request 数据导出请求参数，包含导出配置、查询条件等信息
     * @return 导出任务ID，用于后续跟踪导出任务状态
     */
    @PostMapping("/export_data")
    public DataResult<Long> export(@Valid @RequestBody DataExportRequest request) {
        return taskBizService.exportResultData(request);
    }

    /**
     * 导出数据库结构文档
     * 将数据库表结构、字段信息等导出为文档格式
     *
     * @param request 数据导出请求参数，包含导出配置、数据库连接等信息
     * @return 导出任务ID，用于后续跟踪导出任务状态
     */
    @PostMapping("/export_doc")
    public DataResult<Long> exportDoc(@Valid @RequestBody DataExportRequest request) {
        return taskBizService.exportSchemaDoc(request);
    }


}
