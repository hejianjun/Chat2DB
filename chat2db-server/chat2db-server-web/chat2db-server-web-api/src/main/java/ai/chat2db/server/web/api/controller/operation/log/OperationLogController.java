package ai.chat2db.server.web.api.controller.operation.log;

import java.util.List;
import java.util.Objects;

import ai.chat2db.server.domain.api.model.OperationLog;
import ai.chat2db.server.domain.api.param.operation.OperationLogCreateParam;
import ai.chat2db.server.domain.api.param.operation.OperationLogPageQueryParam;
import ai.chat2db.server.domain.api.service.OperationLogService;
import ai.chat2db.server.domain.core.cache.QueryResultCache;
import ai.chat2db.server.tools.base.wrapper.ServicePage;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.controller.operation.log.converter.OperationLogWebConverter;
import ai.chat2db.server.web.api.controller.operation.log.request.OperationLogCreateRequest;
import ai.chat2db.server.web.api.controller.operation.log.request.OperationLogQueryRequest;
import ai.chat2db.server.web.api.controller.operation.log.vo.OperationLogVO;
import ai.chat2db.server.web.api.controller.rdb.converter.RdbWebConverter;
import ai.chat2db.server.web.api.controller.rdb.vo.ExecuteResultVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 历史记录服务类
 *
 * @author moji
 * @version HistoryManageController.java, v 0.1 2022年09月18日 10:55 moji Exp $
 * @date 2022/09/18
 */
@RequestMapping("/api/operation/log")
@RestController
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private OperationLogWebConverter operationLogWebConverter;

    @Autowired
    private QueryResultCache queryResultCache;

    @Autowired
    private RdbWebConverter rdbWebConverter;

    /**
     * 查询执行记录
     *
     * @param request
     * @return
     */
    @GetMapping("/list")
    public WebPageResult<OperationLogVO> list(OperationLogQueryRequest request) {
        OperationLogPageQueryParam param = operationLogWebConverter.req2param(request);
        param.setUserId(ContextUtils.getUserId());
        ServicePage<OperationLog> result = operationLogService.queryPage(param);
        List<OperationLogVO> operationLogVOList = operationLogWebConverter.dto2vo(result.getData());
        return WebPageResult.of(operationLogVOList, result.getTotal(), result.getPageNo(), result.getPageSize());
    }

    /**
     * 新增历史记录
     *
     * @param request
     * @return
     */
    @PostMapping("/create")
    public DataResult<Long> create(@RequestBody OperationLogCreateRequest request) {
        OperationLogCreateParam param = operationLogWebConverter.createReq2param(request);
        return DataResult.of(operationLogService.create(param));
    }

    /**
     * Restore a recent cached query result without executing the SQL again.
     */
    @GetMapping("/{id}/result")
    public DataResult<ExecuteResultVO> result(@PathVariable Long id) {
        QueryResultCache.CachedQueryResult cachedResult = queryResultCache.get(id);
        if (cachedResult == null || !Objects.equals(ContextUtils.getUserId(), cachedResult.getUserId())) {
            return DataResult.of(null);
        }
        return DataResult.of(rdbWebConverter.dto2vo(cachedResult.getResult()));
    }

}
