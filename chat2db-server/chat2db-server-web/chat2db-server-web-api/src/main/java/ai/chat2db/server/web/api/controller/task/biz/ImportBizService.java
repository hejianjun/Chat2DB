package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.domain.api.enums.TaskStatusEnum;
import ai.chat2db.server.domain.api.enums.TaskTypeEnum;
import ai.chat2db.server.domain.api.param.TaskCreateParam;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.domain.api.param.TaskUpdateParam;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.service.TaskService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.controller.task.request.DataImportRequest;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ImportBizService {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TableService tableService;

    @Autowired
    private ImportStrategyFactory strategyFactory;

    public DataResult<Long> importData(MultipartFile file, DataImportRequest request) {
        Assert.notNull(file, "file can not be null");
        Assert.notBlank(request.getTableName(), "tableName can not be blank");

        DataResult<Long> dataResult = createImportTask(request);

        LoginUser loginUser = ContextUtils.getLoginUser();
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();

        // 同步保存文件到安全位置，避免异步执行时临时文件被清理
        File safeFile;
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "import_file";
            }
            safeFile = FileUtil.createTempFile(originalFilename, "", true);
            file.transferTo(safeFile);
        } catch (Exception e) {
            log.error("save upload file error", e);
            throw new BusinessException("dataSource.importError", new Object[]{e.getMessage()}, e);
        }

        final File finalSafeFile = safeFile;

        CompletableFuture.runAsync(() -> {
            buildContext(loginUser, connectInfo);
            try {
                doImportData(finalSafeFile, request, dataResult.getData());
            } finally {
                // 确保清理临时文件
                FileUtil.del(finalSafeFile);
            }
        }).whenComplete((aVoid, throwable) -> {
            updateImportStatus(dataResult.getData(), throwable);
            removeContext();
        });

        return dataResult;
    }

    private DataResult<Long> createImportTask(DataImportRequest request) {
        TaskCreateParam param = new TaskCreateParam();
        param.setTaskName("import_" + request.getTableName());
        param.setTaskType(TaskTypeEnum.UPLOAD_TABLE_DATA.name());
        param.setDatabaseName(request.getDatabaseName());
        param.setSchemaName(request.getSchemaName());
        param.setTableName(request.getTableName());
        param.setDataSourceId(request.getDataSourceId());
        param.setUserId(ContextUtils.getUserId());
        param.setTaskProgress("0");
        return taskService.create(param);
    }

    private void updateImportStatus(Long id, Throwable throwable) {
        TaskUpdateParam updateParam = new TaskUpdateParam();
        updateParam.setId(id);
        if (throwable != null) {
            log.error("import error", throwable);
            updateParam.setTaskStatus(TaskStatusEnum.ERROR.name());
            if (throwable.getCause() instanceof BusinessException businessException) {
                updateParam.setContent(I18nUtils.getMessage(businessException.getCode(), businessException.getArgs()).getBytes(StandardCharsets.UTF_8));
            } else {
                updateParam.setContent(throwable.getMessage().getBytes(StandardCharsets.UTF_8));
            }
        } else {
            updateParam.setTaskStatus(TaskStatusEnum.FINISH.name());
        }
        taskService.updateStatus(updateParam);
    }

    private void doImportData(File file, DataImportRequest request, Long taskId) {
        String fileType = request.getFileType().toUpperCase();

        List<String> headerList = getColumnList(request);

        ImportStrategy strategy = strategyFactory.getStrategy(fileType);

        Connection connection = Chat2DBContext.getConnection();

        Map<String, Integer> columnOrderMap = new HashMap<>();
        for (int i = 0; i < headerList.size(); i++) {
            columnOrderMap.put(headerList.get(i), i);
        }

        ImportContext importContext = ImportContext.builder()
                .taskId(taskId)
                .tableName(request.getTableName())
                .headerList(headerList)
                .columnOrderMap(columnOrderMap)
                .columnCount(headerList.size())
                .connection(connection)
                .progressUpdater(count -> updateProgressCount(taskId, count))
                .build();
        strategy.importData(file, importContext);
    }

    private List<String> getColumnList(DataImportRequest request) {
        TableQueryParam queryParam = new TableQueryParam();
        queryParam.setDataSourceId(request.getDataSourceId());
        queryParam.setDatabaseName(request.getDatabaseName());
        queryParam.setSchemaName(request.getSchemaName());
        queryParam.setTableName(request.getTableName());
        List<TableColumn> columns = tableService.queryColumns(queryParam);
        List<String> columnNames = new ArrayList<>();
        for (TableColumn column : columns) {
            columnNames.add(column.getName());
        }
        return columnNames;
    }

    private void updateProgressCount(Long taskId, int processedCount) {
        TaskUpdateParam updateParam = new TaskUpdateParam();
        updateParam.setId(taskId);
        updateParam.setTaskProgress(String.valueOf(processedCount));
        taskService.updateStatus(updateParam);
    }

    private void removeContext() {
        Dbutils.removeSession();
        ContextUtils.removeContext();
        Chat2DBContext.removeContext();
    }

    private void buildContext(LoginUser loginUser, ConnectInfo connectInfo) {
        ContextUtils.setContext(Context.builder()
                .loginUser(loginUser)
                .build());
        Dbutils.setSession();
        Chat2DBContext.putContext(connectInfo);
    }

}
