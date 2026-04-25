package ai.chat2db.server.web.api.controller.task;

import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.task.biz.ImportBizService;
import ai.chat2db.server.web.api.controller.task.request.DataImportRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据导入控制器
 * 提供数据导入功能的 REST API 接口
 */
@ConnectionInfoAspect
@RequestMapping("/api/import")
@RestController
@Slf4j
public class ImportController {

    @Autowired
    private ImportBizService importBizService;


    /**
     * 导入数据
     * 将文件数据导入到数据库表中
     *
     * @param file 导入的文件
     * @param request 数据导入请求参数，包含目标表、文件类型等信息
     * @return 导入任务 ID，用于后续跟踪导入任务状态
     */
    @PostMapping("/import_data")
    public DataResult<Long> importData(
            @Valid MultipartFile file,
            @Valid DataImportRequest request) {
        return importBizService.importData(file, request);
    }

}
