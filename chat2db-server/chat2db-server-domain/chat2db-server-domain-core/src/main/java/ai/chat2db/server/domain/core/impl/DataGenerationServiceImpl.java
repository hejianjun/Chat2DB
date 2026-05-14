package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.DataGenerationRequest;
import ai.chat2db.server.domain.api.param.ColumnConfigParam;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.domain.api.service.DataGenerationService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.service.TaskService;
import ai.chat2db.server.domain.api.param.TaskCreateParam;
import ai.chat2db.server.domain.api.enums.TaskStatusEnum;
import ai.chat2db.server.domain.api.enums.TaskTypeEnum;
import ai.chat2db.server.domain.api.vo.DataGenerationPreviewVO;
import ai.chat2db.server.domain.core.generator.DataGenerator;
import ai.chat2db.server.domain.core.generator.DataGeneratorFactory;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.model.TableColumn;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 数据生成服务实现
 */
@Slf4j
@Service
public class DataGenerationServiceImpl implements DataGenerationService {

    @Autowired
    private TableService tableService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private DataGeneratorFactory dataGeneratorFactory;

    @Override
    public ListResult<ColumnConfigParam> getTableColumns(DataGenerationRequest request) {
        try {
            // 获取表列信息
            TableQueryParam param = new TableQueryParam();
            param.setDataSourceId(request.getDataSourceId());
            param.setDatabaseName(request.getDatabaseName());
            param.setSchemaName(request.getSchemaName());
            param.setTableName(request.getTableName());
            
            List<TableColumn> tableColumns = tableService.queryColumns(param);
            if (tableColumns == null) {
                return ListResult.error("GET_TABLE_COLUMNS_ERROR", "获取表列信息失败");
            }

            List<ColumnConfigParam> columns = new ArrayList<>();

            for (TableColumn column : tableColumns) {
                ColumnConfigParam config = new ColumnConfigParam();
                config.setColumnName(column.getName());
                config.setDataType(column.getDataType().toString());
                config.setComment(column.getComment());
                config.setNullable(column.getNullable() != null && column.getNullable() == 1);
                config.setMaxLength(column.getColumnSize());
                config.setScale(column.getDecimalDigits());
                
                // 设置默认生成类型
                DataGenerator defaultGenerator = dataGeneratorFactory.getDefaultGenerator(column.getDataType().toString());
                if (defaultGenerator != null) {
                    config.setGenerationType(defaultGenerator.getGeneratorType());
                }
                
                columns.add(config);
            }

            return ListResult.of(columns);
        } catch (Exception e) {
            log.error("Failed to get table columns", e);
            return ListResult.error("GET_TABLE_COLUMNS_ERROR", "获取表列信息失败: " + e.getMessage());
        }
    }

    @Override
    public DataResult<Map<String, String>> aiInferGenerationTypes(DataGenerationRequest request) {
        try {
            // TODO: 实现AI类型推断逻辑
            // 这里可以调用AI服务来推断每列的数据生成类型
            Map<String, String> inferredTypes = new HashMap<>();
            
            // 基于列名的简单推断逻辑
            ListResult<ColumnConfigParam> columnsResult = getTableColumns(request);
            if (columnsResult.success() && columnsResult.getData() != null) {
                for (ColumnConfigParam column : columnsResult.getData()) {
                    String columnName = column.getColumnName().toLowerCase();
                    String inferredType = inferTypeFromColumnName(columnName);
                    inferredTypes.put(column.getColumnName(), inferredType);
                }
            }

            return DataResult.of(inferredTypes);
        } catch (Exception e) {
            log.error("Failed to AI infer generation types", e);
            return DataResult.error("AI_INFER_ERROR", "AI推断失败: " + e.getMessage());
        }
    }

    @Override
    public DataResult<DataGenerationPreviewVO> generatePreview(DataGenerationRequest request) {
        try {
            ListResult<ColumnConfigParam> columnsResult = getTableColumns(request);
            if (!columnsResult.success()) {
                return DataResult.error("GET_TABLE_COLUMNS_ERROR", "获取表列信息失败");
            }

            List<ColumnConfigParam> columns = columnsResult.getData();
            List<Map<String, Object>> previewData = generateDataRows(request, columns, 10);

            DataGenerationPreviewVO previewVO = new DataGenerationPreviewVO();
            previewVO.setTableName(request.getTableName());
            previewVO.setPreviewData(previewData);

            List<DataGenerationPreviewVO.ColumnInfo> columnInfos = new ArrayList<>();
            for (ColumnConfigParam column : columns) {
                DataGenerationPreviewVO.ColumnInfo columnInfo = new DataGenerationPreviewVO.ColumnInfo();
                columnInfo.setColumnName(column.getColumnName());
                columnInfo.setDataType(column.getDataType());
                columnInfo.setGenerationType(column.getGenerationType());
                columnInfo.setComment(column.getComment());
                columnInfos.add(columnInfo);
            }
            previewVO.setColumns(columnInfos);

            return DataResult.of(previewVO);
        } catch (Exception e) {
            log.error("Failed to generate preview", e);
            return DataResult.error("GENERATE_PREVIEW_ERROR", "生成预览失败: " + e.getMessage());
        }
    }

    @Override
    public DataResult<Long> executeDataGeneration(DataGenerationRequest request) {
        try {
            // 创建任务
            TaskCreateParam taskParam = new TaskCreateParam();
            taskParam.setDataSourceId(request.getDataSourceId());
            taskParam.setDatabaseName(request.getDatabaseName());
            taskParam.setSchemaName(request.getSchemaName());
            taskParam.setTableName(request.getTableName());
            taskParam.setTaskType(TaskTypeEnum.GENERATE_TABLE_DATA.name());
            // TaskCreateParam没有taskStatus字段，状态由TaskService内部管理
            taskParam.setTaskName("数据生成 - " + request.getTableName());
            taskParam.setTaskProgress("0");

            DataResult<Long> taskResult = taskService.create(taskParam);
            if (!taskResult.success()) {
                return DataResult.error("CREATE_TASK_ERROR", "创建任务失败");
            }

            Long taskId = taskResult.getData();

            // 异步执行数据生成
            CompletableFuture.runAsync(() -> {
                executeDataGenerationAsync(taskId, request);
            });

            return DataResult.of(taskId);
        } catch (Exception e) {
            log.error("Failed to execute data generation", e);
            return DataResult.error("EXECUTE_GENERATION_ERROR", "执行数据生成失败: " + e.getMessage());
        }
    }

    @Override
    public ListResult<String> getSupportedGenerationTypes() {
        try {
            String[] supportedTypes = dataGeneratorFactory.getSupportedTypes();
            return ListResult.of(Arrays.asList(supportedTypes));
        } catch (Exception e) {
            log.error("Failed to get supported generation types", e);
            return ListResult.error("GET_SUPPORTED_TYPES_ERROR", "获取支持的生成类型失败");
        }
    }

    /**
     * 基于列名推断生成类型
     */
    private String inferTypeFromColumnName(String columnName) {
        if (columnName.contains("name") || columnName.contains("名")) {
            return "name";
        } else if (columnName.contains("email") || columnName.contains("邮箱") || columnName.contains("邮件")) {
            return "email";
        } else if (columnName.contains("phone") || columnName.contains("电话") || columnName.contains("手机")) {
            return "phone";
        } else if (columnName.contains("address") || columnName.contains("地址")) {
            return "address";
        } else if (columnName.contains("company") || columnName.contains("公司") || columnName.contains("企业")) {
            return "company";
        } else if (columnName.contains("date") || columnName.contains("时间") || columnName.contains("日期")) {
            return "datetime";
        } else if (columnName.contains("age") || columnName.contains("年龄")) {
            return "numeric";
        } else {
            return "text";
        }
    }

    /**
     * 生成数据行
     */
    private List<Map<String, Object>> generateDataRows(DataGenerationRequest request, 
                                                       List<ColumnConfigParam> columns, 
                                                       int rowCount) {
        List<Map<String, Object>> dataRows = new ArrayList<>();
        Faker faker = dataGeneratorFactory.createFaker();

        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new HashMap<>();
            for (ColumnConfigParam column : columns) {
                String generationType = request.getColumnConfigs() != null 
                    ? request.getColumnConfigs().get(column.getColumnName())
                    : column.getGenerationType();

                if (generationType == null) {
                    generationType = column.getGenerationType();
                }

                DataGenerator generator = dataGeneratorFactory.getGenerator(generationType);
                if (generator == null) {
                    generator = dataGeneratorFactory.getDefaultGenerator(column.getDataType());
                }

                if (generator != null) {
                    DataGenerator.ColumnConfig config = new DataGenerator.ColumnConfig();
                    config.setColumnName(column.getColumnName());
                    config.setDataType(column.getDataType());
                    config.setGenerationType(generationType);
                    config.setComment(column.getComment());
                    config.setNullable(column.getNullable());
                    config.setMaxLength(column.getMaxLength());
                    config.setScale(column.getScale());

                    Object value = generator.generate(faker, config);
                    row.put(column.getColumnName(), value);
                }
            }
            dataRows.add(row);
        }

        return dataRows;
    }

    /**
     * 异步执行数据生成
     */
    private void executeDataGenerationAsync(Long taskId, DataGenerationRequest request) {
        try {
            // 更新任务状态为处理中
            updateTaskProgress(taskId, TaskStatusEnum.PROCESSING, 0);

            ListResult<ColumnConfigParam> columnsResult = getTableColumns(request);
            if (!columnsResult.success()) {
                updateTaskProgress(taskId, TaskStatusEnum.ERROR, 0);
                return;
            }

            List<ColumnConfigParam> columns = columnsResult.getData();
            int totalRows = request.getRowCount();
            int batchSize = request.getBatchSize();
            int processedRows = 0;

            // 分批生成和插入数据
            while (processedRows < totalRows) {
                int currentBatchSize = Math.min(batchSize, totalRows - processedRows);
                
                // 生成当前批次的数据
                List<Map<String, Object>> batchData = generateDataRows(request, columns, currentBatchSize);
                
                // 插入数据到数据库
                insertBatchData(request, batchData);
                
                processedRows += currentBatchSize;
                
                // 更新进度
                int progress = (processedRows * 100) / totalRows;
                updateTaskProgress(taskId, TaskStatusEnum.PROCESSING, progress);
            }

            // 任务完成
            updateTaskProgress(taskId, TaskStatusEnum.FINISH, 100);
            log.info("Data generation completed successfully for table: {}", request.getTableName());

        } catch (Exception e) {
            log.error("Data generation failed for table: " + request.getTableName(), e);
            updateTaskProgress(taskId, TaskStatusEnum.ERROR, 0);
        }
    }

    /**
     * 批量插入数据
     */
    private void insertBatchData(DataGenerationRequest request, List<Map<String, Object>> batchData) {
        // TODO: 实现批量插入逻辑
        // 这里需要构建INSERT SQL并执行
        // 可以使用现有的SQL执行机制
        log.debug("Inserting batch of {} rows into table {}", batchData.size(), request.getTableName());
    }

    /**
     * 更新任务进度
     */
    private void updateTaskProgress(Long taskId, TaskStatusEnum status, int progress) {
        try {
            // TODO: 实现任务进度更新
            // 这里需要调用TaskService的更新方法
            log.debug("Updating task {} status: {}, progress: {}%", taskId, status, progress);
        } catch (Exception e) {
            log.error("Failed to update task progress", e);
        }
    }
}
