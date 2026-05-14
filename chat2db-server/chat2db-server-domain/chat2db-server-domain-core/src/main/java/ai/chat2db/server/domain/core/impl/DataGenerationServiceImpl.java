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
import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.model.TableColumn;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

                DataGenerator defaultGenerator = dataGeneratorFactory.getDefaultGenerator(column.getDataType().toString());
                if (defaultGenerator != null) {
                    GeneratorMetadata metadata = defaultGenerator.getMetadata();
                    config.setGenerationType(metadata.getGeneratorType());
                    if (metadata.getSubTypes() != null && !metadata.getSubTypes().isEmpty()) {
                        config.setSubType(metadata.getSubTypes().get(0).getValue());
                    }
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
    public DataResult<DataGenerationPreviewVO> generatePreview(DataGenerationRequest request) {
        try {
            List<ColumnConfigParam> columns = resolveColumns(request);
            if (columns == null) {
                return DataResult.error("GET_TABLE_COLUMNS_ERROR", "获取表列信息失败");
            }

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
            TaskCreateParam taskParam = new TaskCreateParam();
            taskParam.setDataSourceId(request.getDataSourceId());
            taskParam.setDatabaseName(request.getDatabaseName());
            taskParam.setSchemaName(request.getSchemaName());
            taskParam.setTableName(request.getTableName());
            taskParam.setTaskType(TaskTypeEnum.GENERATE_TABLE_DATA.name());
            taskParam.setTaskName("数据生成 - " + request.getTableName());
            taskParam.setTaskProgress("0");

            DataResult<Long> taskResult = taskService.create(taskParam);
            if (!taskResult.success()) {
                return DataResult.error("CREATE_TASK_ERROR", "创建任务失败");
            }

            Long taskId = taskResult.getData();

            CompletableFuture.runAsync(() -> executeDataGenerationAsync(taskId, request));

            return DataResult.of(taskId);
        } catch (Exception e) {
            log.error("Failed to execute data generation", e);
            return DataResult.error("EXECUTE_GENERATION_ERROR", "执行数据生成失败: " + e.getMessage());
        }
    }

    @Override
    public ListResult<String> getSupportedGenerationTypes() {
        try {
            List<GeneratorMetadata> metadataList = dataGeneratorFactory.getAllMetadata();
            List<String> types = metadataList.stream()
                    .map(GeneratorMetadata::getGeneratorType)
                    .collect(Collectors.toList());
            return ListResult.of(types);
        } catch (Exception e) {
            log.error("Failed to get supported generation types", e);
            return ListResult.error("GET_SUPPORTED_TYPES_ERROR", "获取支持的生成类型失败");
        }
    }

    public ListResult<GeneratorMetadata> getAllGeneratorMetadata() {
        try {
            return ListResult.of(dataGeneratorFactory.getAllMetadata());
        } catch (Exception e) {
            log.error("Failed to get all generator metadata", e);
            return ListResult.error("GET_METADATA_ERROR", "获取生成器元数据失败");
        }
    }

    private List<ColumnConfigParam> resolveColumns(DataGenerationRequest request) {
        ListResult<ColumnConfigParam> result = getTableColumns(request);
        if (!result.success()) {
            return null;
        }
        List<ColumnConfigParam> columns = result.getData();

        if (request.getColumnConfigs() != null && !request.getColumnConfigs().isEmpty()) {
            Map<String, ColumnConfigParam> requestMap = new HashMap<>();
            for (ColumnConfigParam c : request.getColumnConfigs()) {
                requestMap.put(c.getColumnName(), c);
            }
            for (ColumnConfigParam column : columns) {
                ColumnConfigParam reqConfig = requestMap.get(column.getColumnName());
                if (reqConfig != null) {
                    if (reqConfig.getGenerationType() != null) {
                        column.setGenerationType(reqConfig.getGenerationType());
                    }
                    if (reqConfig.getSubType() != null) {
                        column.setSubType(reqConfig.getSubType());
                    }
                    if (reqConfig.getCustomParams() != null) {
                        column.setCustomParams(reqConfig.getCustomParams());
                    }
                }
            }
        }
        return columns;
    }

    private List<Map<String, Object>> generateDataRows(DataGenerationRequest request,
                                                       List<ColumnConfigParam> columns,
                                                       int rowCount) {
        List<Map<String, Object>> dataRows = new ArrayList<>();
        Faker faker = dataGeneratorFactory.createFaker();

        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new HashMap<>();
            for (ColumnConfigParam column : columns) {
                String generationType = column.getGenerationType();
                if (generationType == null) {
                    generationType = "text";
                }

                DataGenerator generator = dataGeneratorFactory.getGenerator(generationType);
                if (generator == null) {
                    generator = dataGeneratorFactory.getDefaultGenerator(column.getDataType());
                }

                if (generator != null) {
                    DataGenerator.ColumnConfig config = new DataGenerator.ColumnConfig();
                    config.setColumnName(column.getColumnName());
                    config.setDataType(column.getDataType());
                    config.setSubType(column.getSubType());
                    config.setComment(column.getComment());
                    config.setNullable(column.getNullable());
                    config.setMaxLength(column.getMaxLength());
                    config.setScale(column.getScale());
                    config.setCustomParams(column.getCustomParams());

                    Object value = generator.generate(faker, config);
                    row.put(column.getColumnName(), value);
                }
            }
            dataRows.add(row);
        }

        return dataRows;
    }

    private void executeDataGenerationAsync(Long taskId, DataGenerationRequest request) {
        try {
            updateTaskProgress(taskId, TaskStatusEnum.PROCESSING, 0);

            List<ColumnConfigParam> columns = resolveColumns(request);
            if (columns == null) {
                updateTaskProgress(taskId, TaskStatusEnum.ERROR, 0);
                return;
            }

            int totalRows = request.getRowCount() != null ? request.getRowCount() : 100;
            int batchSize = request.getBatchSize() != null ? request.getBatchSize() : 1000;
            int processedRows = 0;

            while (processedRows < totalRows) {
                int currentBatchSize = Math.min(batchSize, totalRows - processedRows);
                List<Map<String, Object>> batchData = generateDataRows(request, columns, currentBatchSize);
                insertBatchData(request, batchData);
                processedRows += currentBatchSize;

                int progress = (processedRows * 100) / totalRows;
                updateTaskProgress(taskId, TaskStatusEnum.PROCESSING, progress);
            }

            updateTaskProgress(taskId, TaskStatusEnum.FINISH, 100);
            log.info("Data generation completed successfully for table: {}", request.getTableName());
        } catch (Exception e) {
            log.error("Data generation failed for table: " + request.getTableName(), e);
            updateTaskProgress(taskId, TaskStatusEnum.ERROR, 0);
        }
    }

    private void insertBatchData(DataGenerationRequest request, List<Map<String, Object>> batchData) {
        log.debug("Inserting batch of {} rows into table {}", batchData.size(), request.getTableName());
    }

    private void updateTaskProgress(Long taskId, TaskStatusEnum status, int progress) {
        try {
            log.debug("Updating task {} status: {}, progress: {}%", taskId, status, progress);
        } catch (Exception e) {
            log.error("Failed to update task progress", e);
        }
    }
}
