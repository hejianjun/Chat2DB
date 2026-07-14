package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.rdb.request.TableTagUpdateRequest;
import ai.chat2db.server.web.api.controller.rdb.request.TableTagRefreshRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@ConnectionInfoAspect
@RequestMapping("/api/rdb/table/tag")
@RestController
public class TableTagController {

    @Autowired
    private TableService tableService;

    @PostMapping("/update")
    public ActionResult update(@Valid @RequestBody TableTagUpdateRequest request) {
        TablePageQueryParam param = TablePageQueryParam.builder().dataSourceId(request.getDataSourceId())
                .databaseName(request.getDatabaseName()).schemaName(request.getSchemaName()).build();
        tableService.updateTag(param, request.getTableName(), request.getTagName());
        return ActionResult.isSuccess();
    }

    @PostMapping("/refresh")
    public ActionResult refresh(@Valid @RequestBody TableTagRefreshRequest request) {
        TablePageQueryParam param = TablePageQueryParam.builder().dataSourceId(request.getDataSourceId())
                .databaseName(request.getDatabaseName()).schemaName(request.getSchemaName()).build();
        tableService.refreshTags(param);
        return ActionResult.isSuccess();
    }
}
