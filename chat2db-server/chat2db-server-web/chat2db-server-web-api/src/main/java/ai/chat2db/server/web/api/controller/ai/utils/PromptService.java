package ai.chat2db.server.web.api.controller.ai.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.unfbx.chatgpt.entity.chat.Parameters;
import com.unfbx.chatgpt.entity.chat.tool.ToolChoiceObj;
import com.unfbx.chatgpt.entity.chat.tool.ToolChoiceObjFunction;
import com.unfbx.chatgpt.entity.chat.tool.Tools;
import com.unfbx.chatgpt.entity.chat.tool.ToolsFunction;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.SchemaQueryParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.domain.api.param.TableSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.enums.DataSourceTypeEnum;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.util.EasyEnumUtils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.ai.converter.ChatConverter;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.rdb.converter.RdbWebConverter;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConnectionInfoAspect
@Service
public class PromptService {

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private TableService tableService;

    @Autowired
    private ChatConverter chatConverter;

    @Autowired
    private RdbWebConverter rdbWebConverter;

    /**
     * 构建schema参数
     *
     * @param tableQueryParam
     * @param tableNames
     * @return
     */
    public String buildTableColumn(TableQueryParam tableQueryParam,
            List<String> tableNames) {
        if (CollectionUtils.isEmpty(tableNames)) {
            log.error("tableNames is empty");
            return "";
        }
        try {
            return tableNames.stream().map(tableName -> {
                tableQueryParam.setTableName(tableName);
                return queryTableDdl(tableName, tableQueryParam);
            }).collect(Collectors.joining(";\n"));
        } catch (Exception exception) {
            log.error("query tables:{} error, do nothing", tableNames);
        }
        return "";
    }

    /**
     * 从缓存中获取ddl
     *
     * @param tableName
     * @param request
     * @return
     */
    public String queryTableDdl(String tableName, TableQueryParam request) {
        TablePageQueryParam param = new TablePageQueryParam();
        param.setTableName(tableName);
        param.setDataSourceId(request.getDataSourceId());
        param.setDatabaseName(request.getDatabaseName());
        param.setSchemaName(request.getSchemaName());
        TableSelector tableSelector = new TableSelector();
        tableSelector.setColumnList(true);
        tableSelector.setIndexList(false);
        PageResult<Table> tables = tableService.pageQuery(param, tableSelector);
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        for (Table table : tables.getData()) {
            return sqlBuilder.buildCreateTableSql(table);
        }
        log.error("query table:{} error, do nothing", tableName);
        return "";
    }

    /**
     * 构建 prompt
     *
     * @param queryRequest
     * @return
     */
    public String buildAutoPrompt(ChatQueryRequest queryRequest) {
        if (PromptType.TEXT_GENERATION.getCode().equals(queryRequest.getPromptType())) {
            return queryRequest.getMessage();
        }

        String dataSourceType = queryDatabaseType(queryRequest);
        PromptType pType = determinePromptType(queryRequest);

        if (dataSourceType.equals(DataSourceTypeEnum.REDIS.getCode())) {
            return buildRedisPrompt(queryRequest, dataSourceType, pType);
        }

        String properties = buildSchemaProperties(queryRequest, pType);
        String schemaProperty = buildSchemaPrompt(properties, queryRequest, dataSourceType, pType);

        if (PromptType.SQL_2_SQL.equals(pType)) {
            schemaProperty = appendTargetSqlType(schemaProperty, queryRequest.getDestSqlType(), dataSourceType);
        }

        return cleanPrompt(schemaProperty);
    }

    /**
     * 确定 prompt 类型
     */
    private PromptType determinePromptType(ChatQueryRequest queryRequest) {
        String promptType = StringUtils.defaultIfBlank(queryRequest.getPromptType(), PromptType.NL_2_SQL.getCode());
        PromptType pType = EasyEnumUtils.getEnum(PromptType.class, promptType);

        if (CollectionUtils.isEmpty(queryRequest.getTableNames())
            && !PromptType.NL_2_COMMENT.equals(pType)
            && !PromptType.TITLE_GENERATION.equals(pType)) {
            return PromptType.SELECT_TABLES;
        }
        return pType;
    }

    /**
     * 构建 Redis prompt
     */
    private String buildRedisPrompt(ChatQueryRequest queryRequest, String dataSourceType, PromptType pType) {
        queryRequest.setDestSqlType(DataSourceTypeEnum.REDIS.getCode());
        String properties = queryRedisSchema(queryRequest);
        String ext = StringUtils.defaultIfEmpty(queryRequest.getExt(), "");
        String prompt = StringUtils.defaultString(queryRequest.getMessage(), "");

        return String.format(
                "### 请根据以下 keys 和 input%s. %s\n#\n### %s keys list:\n#\n# "
                        + "%s\n#\n#\n### SQL input: %s",
                "将自然语言转换成 Redis 命令", ext, dataSourceType,
                properties, prompt);
    }

    /**
     * 构建 schema properties
     */
    private String buildSchemaProperties(ChatQueryRequest queryRequest, PromptType pType) {
        if (CollectionUtils.isNotEmpty(queryRequest.getTableNames())) {
            TableQueryParam queryParam = chatConverter.chat2tableQuery(queryRequest);
            return buildTableColumn(queryParam, queryRequest.getTableNames());
        } else {
            return queryDatabaseTables(queryRequest);
        }
    }

    /**
     * 构建 schema prompt
     */
    private String buildSchemaPrompt(String properties, ChatQueryRequest queryRequest,
                                     String dataSourceType, PromptType pType) {
        String ext = StringUtils.defaultIfEmpty(queryRequest.getExt(), "");
        String prompt = StringUtils.defaultString(queryRequest.getMessage(), "");

        if (StringUtils.isNotEmpty(properties)) {
            return String.format(
                    "### 请根据以下 table properties 和 SQL input%s. %s\n#\n### %s SQL tables:\n#\n# "
                            + "%s\n#\n#\n### SQL input: %s",
                    pType.getDescription(), ext, dataSourceType,
                    properties, prompt);
        } else {
            return String.format("### 请根据以下 SQL input%s. %s\n#\n### SQL input: %s",
                    pType.getDescription(), ext, prompt);
        }
    }

    /**
     * 添加目标 SQL 类型
     */
    private String appendTargetSqlType(String schemaProperty, String destSqlType, String dataSourceType) {
        String targetDbType = StringUtils.isNotBlank(destSqlType) ? destSqlType : dataSourceType;
        return String.format(
                "%s\n#\n### 目标 SQL 类型：%s", schemaProperty, targetDbType);
    }

    /**
     * 清理 prompt
     */
    private String cleanPrompt(String prompt) {
        return prompt.replaceAll("[\r\t]", "");
    }

    private String queryRedisSchema(ChatQueryRequest queryRequest) {
        if (CollectionUtils.isEmpty(queryRequest.getTableNames())) {
            SchemaQueryParam schemaQueryParam = rdbWebConverter.chatQueryRequest2schemaParam(queryRequest);
            ListResult<Schema> schemaListResult = databaseService.querySchema(schemaQueryParam);
            List<String> tableNames = new ArrayList<>();
            String properties = schemaListResult.getData()
                    .stream()
                    .peek(schema -> tableNames.add(schema.getName()))
                    .map(schema -> schema.getName() + ":*(" + schema.getKeyType() + ")")
                    .collect(Collectors.joining(","));
            queryRequest.setTableNames(tableNames);
            return properties;
        }
        return queryRequest.getTableNames().stream().map(name -> name + ":*").collect(Collectors.joining(","));
    }

    /**
     * query database type
     *
     * @param queryRequest
     * @return
     */
    public String queryDatabaseType(ChatQueryRequest queryRequest) {
        // 查询schema信息
        DataResult<DataSource> dataResult = dataSourceService.queryById(queryRequest.getDataSourceId());
        String dataSourceType = dataResult.getData().getType();
        if (StringUtils.isBlank(dataSourceType)) {
            dataSourceType = "MYSQL";
        }
        return dataSourceType;
    }

    /**
     * query database schema
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    public String queryDatabaseTables(ChatQueryRequest queryRequest) {
        try {
            TablePageQueryParam queryParam = rdbWebConverter.chatQueryRequest2page(queryRequest);
            queryParam.queryAll();
            TableSelector tableSelector = TableSelector.builder()
                    .indexList(false)
                    .columnList(true)
                    .foreignKey(true)
                    .build();
            PageResult<Table> tables = tableService.pageQuery(queryParam, tableSelector);
            List<String> tableNames = new ArrayList<>();
            String properties = tables.getData().stream().map(table -> {
                tableNames.add(table.getName());
                StringBuilder sb = new StringBuilder(table.getName()); // 直接在初始化时加入表名
                String comment = StringUtils.defaultString(table.getComment(), table.getAiComment());
                List<ForeignKey> foreignKeys = table.getForeignKeyList();
                // 只有当有注释或外键时才添加额外信息
                if (StringUtils.isNotEmpty(comment) || !foreignKeys.isEmpty()) {
                    sb.append("(").append(comment);

                    // 如果存在外键，添加外键信息
                    if (!foreignKeys.isEmpty()) {
                        // 如果注释和外键都存在，先添加一个分隔符
                        if (StringUtils.isNotEmpty(comment)) {
                            sb.append(";");
                        }
                        String foreignKeysString = foreignKeys.stream()
                                .map(foreignKey -> foreignKey.getColumn() + "->" + foreignKey.getReferencedTable() + ":"
                                        + foreignKey.getReferencedColumn())
                                .collect(Collectors.joining(","));
                        // 优化外键的展示
                        sb.append("foreignKeys:").append(foreignKeysString);
                    }
                    sb.append(")");
                }
                return sb.toString(); // 在映射阶段直接转换为字符串
            }).collect(Collectors.joining(","));
            queryRequest.setTableNames(tableNames);
            return properties;
        } catch (Exception e) {
            log.error("query table error:{}, do nothing", e.getMessage());
            return "";
        }
    }




}
