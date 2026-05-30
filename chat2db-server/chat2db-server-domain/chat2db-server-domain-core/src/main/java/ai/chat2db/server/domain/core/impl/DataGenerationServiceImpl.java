package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.param.DataGenerationRequest;
import ai.chat2db.server.domain.api.param.ColumnConfigParam;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.domain.api.param.GeneratorTemplate;
import ai.chat2db.server.domain.api.param.TaskCreateParam;
import ai.chat2db.server.domain.api.param.TaskUpdateParam;
import ai.chat2db.server.domain.api.service.DataGenerationService;
import ai.chat2db.server.domain.api.service.ForeignKeySyncService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.service.TaskService;
import ai.chat2db.server.domain.api.service.DataGenerationRuleService;
import ai.chat2db.server.domain.api.enums.TaskStatusEnum;
import ai.chat2db.server.domain.api.enums.TaskTypeEnum;
import ai.chat2db.server.domain.api.vo.DataGenerationPreviewVO;
import ai.chat2db.server.domain.core.generator.ExpressionDataGenerator;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.VirtualForeignKey;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.util.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataGenerationServiceImpl implements DataGenerationService {

    private static final int MAX_REFERENCED_VALUE_ROWS = 10000;
    private static final String SOURCE_TYPE_REAL = "REAL";
    private static final String SOURCE_TYPE_VIRTUAL = "VIRTUAL";

    @Autowired
    private TableService tableService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ExpressionDataGenerator expressionDataGenerator;

    @Autowired
    private DataGenerationRuleService ruleService;

    @Autowired
    private ForeignKeySyncService foreignKeySyncService;

    @Override
    public List<ColumnConfigParam> getTableColumns(DataGenerationRequest request) {
        try {
            TableQueryParam param = new TableQueryParam();
            param.setDataSourceId(request.getDataSourceId());
            param.setDatabaseName(request.getDatabaseName());
            param.setSchemaName(request.getSchemaName());
            param.setTableName(request.getTableName());

            List<TableColumn> tableColumns = tableService.queryColumns(param);
            if (tableColumns == null) {
                throw new BusinessException("GET_TABLE_COLUMNS_ERROR", new Object[]{"获取表列信息失败"});
            }
            List<ForeignKey> foreignKeys = loadForeignKeys(request, true);
            Map<String, ForeignKey> foreignKeyMap = buildForeignKeyMap(foreignKeys);

            List<ColumnConfigParam> savedConfigs = ruleService.getColumnConfigs(
                    request.getDataSourceId(), request.getDatabaseName(), request.getSchemaName(), request.getTableName());

            Map<String, ColumnConfigParam> savedMap = new HashMap<>();
            if (savedConfigs != null && !savedConfigs.isEmpty()) {
                for (ColumnConfigParam cfg : savedConfigs) {
                    savedMap.put(cfg.getColumnName(), cfg);
                }
            }

            List<ColumnConfigParam> columns = new ArrayList<>();
            for (TableColumn column : tableColumns) {
                ColumnConfigParam config = new ColumnConfigParam();
                config.setColumnName(column.getName());
                String dataType = column.getDataType() != null ? column.getDataType() : "VARCHAR";
                config.setDataType(dataType);
                config.setComment(column.getComment());
                config.setNullable(column.getNullable() != null && column.getNullable() == 1);
                config.setAutoIncrement(column.getAutoIncrement() != null && column.getAutoIncrement());
                config.setMaxLength(column.getColumnSize());
                config.setScale(column.getDecimalDigits());
                applyForeignKeyInfo(config, foreignKeyMap.get(column.getName()));

                ColumnConfigParam saved = savedMap.get(column.getName());
                if (saved != null && saved.getExpression() != null) {
                    config.setExpression(saved.getExpression());
                }

                columns.add(config);
            }

            return columns;
        } catch (Exception e) {
            log.error("Failed to get table columns", e);
            throw new BusinessException("GET_TABLE_COLUMNS_ERROR", new Object[]{"获取表列信息失败: " + e.getMessage()});
        }
    }

    @Override
    public DataGenerationPreviewVO generatePreview(DataGenerationRequest request) {
        try {
            saveConfigs(request);

            List<ColumnConfigParam> columns = resolveColumns(request);
            if (columns == null) {
                throw new BusinessException("GET_TABLE_COLUMNS_ERROR", new Object[]{"获取表列信息失败"});
            }

            ForeignKeyValueProvider foreignKeyValueProvider = buildForeignKeyValueProvider(request);
            List<Map<String, Object>> previewData = generateDataRows(request, columns, foreignKeyValueProvider, 10);

            DataGenerationPreviewVO previewVO = new DataGenerationPreviewVO();
            previewVO.setTableName(request.getTableName());
            previewVO.setPreviewData(previewData);

            List<DataGenerationPreviewVO.ColumnInfo> columnInfos = new ArrayList<>();
            for (ColumnConfigParam column : columns) {
                DataGenerationPreviewVO.ColumnInfo columnInfo = new DataGenerationPreviewVO.ColumnInfo();
                columnInfo.setColumnName(column.getColumnName());
                columnInfo.setDataType(column.getDataType());
                columnInfo.setComment(column.getComment());
                columnInfos.add(columnInfo);
            }
            previewVO.setColumns(columnInfos);

            return previewVO;
        } catch (Exception e) {
            log.error("Failed to generate preview", e);
            throw new BusinessException("GENERATE_PREVIEW_ERROR", new Object[]{"生成预览失败: " + e.getMessage()});
        }
    }

    @Override
    public Long executeDataGeneration(DataGenerationRequest request) {
        try {
            saveConfigs(request);

            List<ColumnConfigParam> columns = resolveColumns(request);
            if (columns == null) {
                throw new BusinessException("GET_TABLE_COLUMNS_ERROR", new Object[]{"获取表列信息失败"});
            }

            TaskCreateParam taskParam = new TaskCreateParam();
            taskParam.setDataSourceId(request.getDataSourceId());
            taskParam.setDatabaseName(request.getDatabaseName());
            taskParam.setSchemaName(request.getSchemaName());
            taskParam.setTableName(request.getTableName());
            taskParam.setTaskType(TaskTypeEnum.GENERATE_TABLE_DATA.name());
            taskParam.setTaskName("数据生成 - " + request.getTableName());
            taskParam.setTaskProgress("0");

            Long taskId = taskService.create(taskParam);
            if (taskId == null) {
                throw new BusinessException("CREATE_TASK_ERROR", new Object[]{"创建任务失败"});
            }

            LoginUser loginUser = ContextUtils.getLoginUser();
            ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();

            CompletableFuture.runAsync(() -> {
                buildContext(loginUser, connectInfo);
                try {
                    executeDataGenerationAsync(taskId, request);
                } finally {
                    removeContext();
                }
            });

            return taskId;
        } catch (Exception e) {
            log.error("Failed to execute data generation", e);
            throw new BusinessException("EXECUTE_GENERATION_ERROR", new Object[]{"执行数据生成失败: " + e.getMessage()});
        }
    }

    @Override
    public List<GeneratorTemplate> getAllGeneratorTemplates() {
        return GeneratorTemplate.getDefaultTemplates();
    }

    private void saveConfigs(DataGenerationRequest request) {
        if (request.getColumnConfigs() == null || request.getColumnConfigs().isEmpty()) {
            return;
        }
        try {
            ruleService.saveColumnConfigs(
                    request.getDataSourceId(), request.getDatabaseName(), request.getSchemaName(),
                    request.getTableName(), 0L, request.getColumnConfigs(), request.getRowCount());
        } catch (Exception e) {
            log.warn("Failed to save generation configs, but continuing operation", e);
        }
    }

    private List<ColumnConfigParam> resolveColumns(DataGenerationRequest request) {
        List<ColumnConfigParam> result = getTableColumns(request);
        if (result == null || result.isEmpty()) {
            return null;
        }
        List<ColumnConfigParam> dbColumns = result;

        if (request.getColumnConfigs() != null && !request.getColumnConfigs().isEmpty()) {
            Map<String, String> expressionMap = request.getColumnConfigs().stream()
                    .filter(c -> c.getExpression() != null)
                    .collect(Collectors.toMap(ColumnConfigParam::getColumnName, ColumnConfigParam::getExpression));

            for (ColumnConfigParam col : dbColumns) {
                String userExpression = expressionMap.get(col.getColumnName());
                if (userExpression != null) {
                    col.setExpression(userExpression);
                }
            }
        }

        return dbColumns;
    }

    private List<ForeignKey> loadForeignKeys(DataGenerationRequest request, boolean syncRealForeignKeys) {
        if (syncRealForeignKeys) {
            foreignKeySyncService.syncForeignKeys(request.getDataSourceId(), request.getDatabaseName(),
                    request.getSchemaName(), request.getTableName());
        }
        List<ForeignKey> foreignKeys = foreignKeySyncService.listAllForeignKeys(request.getDataSourceId(),
                request.getDatabaseName(), request.getSchemaName(), request.getTableName());
        if (foreignKeys == null || foreignKeys.isEmpty()) {
            return Collections.emptyList();
        }
        return foreignKeys.stream()
                .filter(fk -> sameName(fk.getTableName(), request.getTableName()))
                .filter(fk -> fk.getColumn() != null && fk.getReferencedTable() != null
                        && fk.getReferencedColumn() != null)
                .collect(Collectors.toList());
    }

    private Map<String, ForeignKey> buildForeignKeyMap(List<ForeignKey> foreignKeys) {
        Map<String, ForeignKey> result = new HashMap<>();
        for (ForeignKey foreignKey : foreignKeys) {
            ForeignKey existing = result.get(foreignKey.getColumn());
            if (existing == null || SOURCE_TYPE_VIRTUAL.equals(getForeignKeySourceType(existing))) {
                result.put(foreignKey.getColumn(), foreignKey);
            }
        }
        return result;
    }

    private void applyForeignKeyInfo(ColumnConfigParam column, ForeignKey foreignKey) {
        if (foreignKey == null) {
            column.setForeignKey(false);
            return;
        }
        column.setForeignKey(true);
        column.setForeignKeySourceType(getForeignKeySourceType(foreignKey));
        column.setReferencedTable(foreignKey.getReferencedTable());
        column.setReferencedColumnName(foreignKey.getReferencedColumn());
    }

    private ForeignKeyValueProvider buildForeignKeyValueProvider(DataGenerationRequest request) {
        List<ForeignKey> foreignKeys = loadForeignKeys(request, false);
        if (foreignKeys.isEmpty()) {
            return ForeignKeyValueProvider.empty();
        }

        Map<String, List<ForeignKey>> groupedForeignKeys = foreignKeys.stream()
                .collect(Collectors.groupingBy(fk -> buildForeignKeyGroupKey(request, fk), LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, ForeignKeyValueGroup> columnGroupMap = new HashMap<>();
        for (List<ForeignKey> groupForeignKeys : groupedForeignKeys.values()) {
            ForeignKeyValueGroup valueGroup = buildForeignKeyValueGroup(request, groupForeignKeys);
            for (String columnName : valueGroup.childColumns()) {
                columnGroupMap.put(columnName, valueGroup);
            }
        }
        return new ForeignKeyValueProvider(columnGroupMap);
    }

    private ForeignKeyValueGroup buildForeignKeyValueGroup(DataGenerationRequest request, List<ForeignKey> foreignKeys) {
        ForeignKey first = foreignKeys.get(0);
        MetaData metaData = Chat2DBContext.getMetaData();
        String tableName = buildQualifiedTableName(first.getReferencedTable(),
                first.getDatabaseName() != null ? first.getDatabaseName() : request.getDatabaseName(),
                first.getSchemaName() != null ? first.getSchemaName() : request.getSchemaName(), metaData);

        List<String> referencedColumns = foreignKeys.stream()
                .map(ForeignKey::getReferencedColumn)
                .collect(Collectors.toList());
        List<String> childColumns = foreignKeys.stream()
                .map(ForeignKey::getColumn)
                .collect(Collectors.toList());

        StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
        for (int i = 0; i < referencedColumns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(metaData.getMetaDataName(referencedColumns.get(i)));
        }
        sql.append(" FROM ").append(tableName).append(" WHERE ");
        for (int i = 0; i < referencedColumns.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(metaData.getMetaDataName(referencedColumns.get(i))).append(" IS NOT NULL");
        }

        List<List<Object>> referencedRows = queryReferencedRows(sql.toString(), referencedColumns.size());
        if (referencedRows.isEmpty()) {
            throw new RuntimeException("外键引用表无可用数据：" + buildForeignKeyRelationText(first));
        }
        return new ForeignKeyValueGroup(childColumns, referencedRows);
    }

    private List<List<Object>> queryReferencedRows(String sql, int columnCount) {
        List<List<Object>> rows = new ArrayList<>();
        try (Statement statement = Chat2DBContext.getConnection().createStatement()) {
            statement.setMaxRows(MAX_REFERENCED_VALUE_ROWS);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(resultSet.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询外键引用数据失败：" + e.getMessage(), e);
        }
        return rows;
    }

    private String buildForeignKeyGroupKey(DataGenerationRequest request, ForeignKey foreignKey) {
        String foreignKeyName = foreignKey.getName();
        if (foreignKeyName == null || foreignKeyName.isBlank()) {
            foreignKeyName = foreignKey.getColumn() + "->" + foreignKey.getReferencedColumn();
        }
        return String.join("|",
                getForeignKeySourceType(foreignKey),
                nullToEmpty(foreignKeyName),
                nullToEmpty(foreignKey.getReferencedTable()),
                nullToEmpty(foreignKey.getDatabaseName() != null ? foreignKey.getDatabaseName() : request.getDatabaseName()),
                nullToEmpty(foreignKey.getSchemaName() != null ? foreignKey.getSchemaName() : request.getSchemaName())
        );
    }

    private String buildForeignKeyRelationText(ForeignKey foreignKey) {
        return foreignKey.getTableName() + "." + foreignKey.getColumn()
                + " -> " + foreignKey.getReferencedTable() + "." + foreignKey.getReferencedColumn();
    }

    private String getForeignKeySourceType(ForeignKey foreignKey) {
        return foreignKey instanceof VirtualForeignKey ? SOURCE_TYPE_VIRTUAL : SOURCE_TYPE_REAL;
    }

    private String buildQualifiedTableName(String tableName, String databaseName, String schemaName, MetaData metaData) {
        String qualifiedName = metaData.getMetaDataName(tableName);
        if (schemaName != null && !schemaName.isBlank()) {
            qualifiedName = metaData.getMetaDataName(schemaName) + "." + qualifiedName;
        }
        if (databaseName != null && !databaseName.isBlank()) {
            qualifiedName = metaData.getMetaDataName(databaseName) + "." + qualifiedName;
        }
        return qualifiedName;
    }

    private boolean sameName(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<Map<String, Object>> generateDataRows(DataGenerationRequest request,
                                                       List<ColumnConfigParam> columns,
                                                       ForeignKeyValueProvider foreignKeyValueProvider,
                                                       int rowCount) {
        List<Map<String, Object>> dataRows = new ArrayList<>();
        Faker faker = new Faker(LocaleContextHolder.getLocale());

        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<ForeignKeyValueGroup, List<Object>> foreignKeyRowCache = new HashMap<>();
            for (ColumnConfigParam column : columns) {
                if (Boolean.TRUE.equals(column.getAutoIncrement())) {
                    continue;
                }
                if (foreignKeyValueProvider.hasForeignKeyValue(column.getColumnName())) {
                    row.put(column.getColumnName(), foreignKeyValueProvider.nextValue(column.getColumnName(),
                            foreignKeyRowCache));
                    continue;
                }
                String expression = column.getExpression();

                ExpressionDataGenerator.ColumnGenerationConfig config =
                        new ExpressionDataGenerator.ColumnGenerationConfig(
                                column.getColumnName(),
                                column.getDataType(),
                                column.getNullable(),
                                column.getMaxLength(),
                                column.getScale()
                        );

                Object value = expressionDataGenerator.generate(faker, expression, config);
                row.put(column.getColumnName(), value);
            }
            dataRows.add(row);
        }

        return dataRows;
    }

    private void executeDataGenerationAsync(Long taskId, DataGenerationRequest request) {
        Exception error = null;
        try {
            updateTaskProgress(taskId, TaskStatusEnum.PROCESSING, 0);

            List<ColumnConfigParam> columns = resolveColumns(request);
            if (columns == null) {
                throw new RuntimeException("获取表列信息失败");
            }
            ForeignKeyValueProvider foreignKeyValueProvider = buildForeignKeyValueProvider(request);

            int totalRows = request.getRowCount() != null ? request.getRowCount() : 100;
            int batchSize = request.getBatchSize() != null ? request.getBatchSize() : 1000;
            int processedRows = 0;

            while (processedRows < totalRows) {
                int currentBatchSize = Math.min(batchSize, totalRows - processedRows);
                List<Map<String, Object>> batchData = generateDataRows(request, columns, foreignKeyValueProvider,
                        currentBatchSize);
                insertBatchData(request, batchData);
                processedRows += currentBatchSize;

                int progress = (processedRows * 100) / totalRows;
                updateTaskProgress(taskId, TaskStatusEnum.PROCESSING, progress);
            }

            log.info("Data generation completed successfully for table: {}", request.getTableName());
        } catch (Exception e) {
            log.error("Data generation failed for table: " + request.getTableName(), e);
            error = e;
        } finally {
            updateGenerationStatus(taskId, error);
        }
    }

    private void updateGenerationStatus(Long taskId, Exception throwable) {
        try {
            TaskUpdateParam updateParam = new TaskUpdateParam();
            updateParam.setId(taskId);
            if (throwable != null) {
                updateParam.setTaskStatus(TaskStatusEnum.ERROR.name());
                if (throwable instanceof BusinessException businessException) {
                    updateParam.setContent(I18nUtils.getMessage(businessException.getCode(), businessException.getArgs()));
                } else {
                    updateParam.setContent(throwable.getMessage());
                }
            } else {
                updateParam.setTaskStatus(TaskStatusEnum.FINISH.name());
                updateParam.setTaskProgress("100");
            }
            taskService.updateStatus(updateParam);
        } catch (Exception e) {
            log.error("Failed to update generation status", e);
        }
    }

    private void insertBatchData(DataGenerationRequest request, List<Map<String, Object>> batchData) {
        if (batchData == null || batchData.isEmpty()) {
            return;
        }

        List<String> columnNames = new ArrayList<>(batchData.get(0).keySet());
        if (columnNames.isEmpty()) {
            return;
        }

        MetaData metaData = Chat2DBContext.getMetaData();
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(buildTableName(request, metaData)).append(" (");
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(metaData.getMetaDataName(columnNames.get(i)));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");

        Connection connection = Chat2DBContext.getConnection();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (Map<String, Object> row : batchData) {
                    for (int i = 0; i < columnNames.size(); i++) {
                        ps.setObject(i + 1, row.get(columnNames.get(i)));
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                log.error("Batch insert error, SQL: {}", sql, e);
                throw new BusinessException("dataGeneration.batchInsertFailed", new Object[]{e.getMessage()}, e);
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            log.error("Batch insert error, SQL: {}", sql, e);
            throw new BusinessException("dataGeneration.batchInsertFailed", new Object[]{e.getMessage()}, e);
        }
    }

    private static class ForeignKeyValueProvider {

        private final Map<String, ForeignKeyValueGroup> columnGroupMap;

        private final Random random = new Random();

        private ForeignKeyValueProvider(Map<String, ForeignKeyValueGroup> columnGroupMap) {
            this.columnGroupMap = columnGroupMap;
        }

        private static ForeignKeyValueProvider empty() {
            return new ForeignKeyValueProvider(Collections.emptyMap());
        }

        private boolean hasForeignKeyValue(String columnName) {
            return columnGroupMap.containsKey(columnName);
        }

        private Object nextValue(String columnName, Map<ForeignKeyValueGroup, List<Object>> rowCache) {
            ForeignKeyValueGroup group = columnGroupMap.get(columnName);
            List<Object> values = rowCache.computeIfAbsent(group, key -> key.nextValues(random));
            int index = group.childColumns().indexOf(columnName);
            return values.get(index);
        }
    }

    private record ForeignKeyValueGroup(List<String> childColumns, List<List<Object>> referencedRows) {

        private List<Object> nextValues(Random random) {
            return referencedRows.get(random.nextInt(referencedRows.size()));
        }
    }

    private String buildTableName(DataGenerationRequest request, MetaData metaData) {
        return buildQualifiedTableName(request.getTableName(), request.getDatabaseName(), request.getSchemaName(),
                metaData);
    }

    private void updateTaskProgress(Long taskId, TaskStatusEnum status, int progress) {
        try {
            TaskUpdateParam updateParam = new TaskUpdateParam();
            updateParam.setId(taskId);
            updateParam.setTaskStatus(status.name());
            updateParam.setTaskProgress(String.valueOf(progress));
            taskService.updateStatus(updateParam);
        } catch (Exception e) {
            log.error("Failed to update task progress", e);
        }
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

