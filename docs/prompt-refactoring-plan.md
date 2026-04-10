# Chat2DB 提示词生成重构计划

## 执行摘要

当前的提示词生成代码**分散在多个文件中，存在逻辑重复、职责混乱、组织性差等问题**。主要问题包括：

1. **提示词构建逻辑重复** - `BuildPromptAction` 和 `PromptService` 之间存在重复逻辑
2. **PromptService 是上帝类** - 311 行的类混合了 Schema 获取、提示词构建、数据库查询等多种职责
3. **硬编码的提示词模板** - 分散在各个 Action 类中
4. **关注点未分离** - 业务逻辑与格式化逻辑混合
5. **缺乏集中化的模板管理** - 没有统一的提示词模板管理机制

---

## 当前架构问题

### 1. **提示词构建逻辑重复**

**问题：** `BuildPromptAction.buildPromptWithSchema()` (66-86 行) 与 `PromptService.buildSchemaPrompt()` (191-207 行) 逻辑重复。

```java
// BuildPromptAction.java:70-77
String.format("### 请根据以下 table properties 和 SQL input%s. %s\n#\n### %s SQL tables...", ...)

// PromptService.java:198-200  
String.format("### 请根据以下 table properties 和 SQL input%s. %s\n#\n### %s SQL tables...", ...)
```

**影响：** 两条不同的代码路径产生不同的提示词格式，导致不一致性。

---

### 2. **PromptService 是上帝类**

**问题：** `PromptService` 承担过多职责：
- Schema 获取 (`queryDatabaseTables`, `queryRedisSchema`)
- DDL 构建 (`buildTableColumn`, `queryTableDdl`)
- 提示词构造 (`buildAutoPrompt`, `buildSchemaPrompt`)
- 数据库类型检测 (`queryDatabaseType`)
- 字符串清理 (`cleanPrompt`)

**代码行数：** 311 行，包含 15+ 个 public/private 方法

---

### 3. **Action 类中硬编码提示词模板**

**问题：** `AutoSelectTablesAction` 直接构建提示词：

```java
// AutoSelectTablesAction.java:72
return promptService.buildAutoPrompt(ctx.getRequest())
    + "\n\n请只输出 JSON，格式：{\"table_names\":[\"表名 1\",\"表名 2\"]}，不要输出其他文字。";
```

**影响：** 提示词模板分散，难以进行本地化和 A/B 测试。

---

### 4. **BuildPromptAction 关注点混乱**

**问题：** `BuildPromptAction` 处理：
- 状态机事件处理
- 提示词类型检测
- 数据库类型猜测
- 提示词模板格式化
- 字符串清理

```java
// BuildPromptAction.java:45-53
String dataSourceType = guessDataSourceType(schemaDdl);
String builtPrompt = buildPromptWithSchema(...);
builtPrompt = builtPrompt.replaceAll("[\r\t]", "").replaceAll("#", "");
```

---

### 5. **缺乏提示词模板抽象**

**问题：** 没有集中化的模板管理。模板：
- 硬编码在 format 调用中
- 未外部化
- 未版本化
- 无法独立测试

---

## 重构架构

### 核心设计原则

1. **职责分离** - 提示词构建与数据获取分离
2. **单一事实来源** - 模板集中管理，无重复
3. **建造者模式** - 流式 API 构建提示词
4. **复用现有服务** - Schema 获取合并到现有 `DatabaseService`
5. **删除门面** - 直接使用建造者，减少中间层

---

### 新架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Action 类                                     │
│  (BuildPromptAction, AutoSelectTablesAction, FetchSchemaAction) │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
┌─────────────────────────────────┐  ┌─────────────────────────┐
│        PromptBuilder            │  │    DatabaseService      │
│                                 │  │  (现有服务，扩展)        │
│  - build(PromptContext)         │  │                         │
│  - validate(String prompt)      │  │  + querySchema()        │
│                                 │  │  + queryTableDdl()      │
│  内部使用:                       │  │  + queryDatabaseType()  │
│  - PromptTemplateRegistry       │  │  + guessDbType()        │
│  - PromptValidator              │  │                         │
└─────────────────────────────────┘  └─────────────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│     PromptTemplateRegistry      │
│                                 │
│  - 集中管理所有提示词模板        │
│  - 按 PromptType 获取模板         │
│  - 支持模板版本化                │
└─────────────────────────────────┘
```

---

## 重构计划

### 第一阶段：扩展 DatabaseService 📦

**目标服务：** `ai.chat2db.server.domain.api.service.DatabaseService`

#### 1.1 在 DatabaseService 中添加 Schema 获取方法

```java
public interface DatabaseService {
    // 现有方法...
    
    // 新增：查询表 DDL
    String queryTableDdl(DataSourceConnectionInfo connectionInfo, 
                         String schemaName, String tableName);
    
    // 新增：批量构建表列信息
    String buildTableColumn(DataSourceConnectionInfo connectionInfo, 
                           String schemaName, List<String> tableNames);
    
    // 新增：查询数据库所有表信息（用于自动选表场景）
    String queryDatabaseTables(DataSourceConnectionInfo connectionInfo, 
                               String schemaName);
    
    // 新增：查询 Redis Schema
    String queryRedisSchema(DataSourceConnectionInfo connectionInfo, 
                            String schemaName, List<String> tableNames);
    
    // 新增：获取数据库类型
    String queryDatabaseType(Long dataSourceId);
}
```

#### 1.2 在 DatabaseServiceImpl 中实现

```java
@Service
@Slf4j
public class DatabaseServiceImpl implements DatabaseService {
    
    @Autowired
    private TableService tableService;
    
    @Autowired
    private DataSourceService dataSourceService;
    
    @Override
    public String queryTableDdl(DataSourceConnectionInfo connectionInfo, 
                                String schemaName, String tableName) {
        // 实现：调用 TableService 查询表结构
        // 使用 SqlBuilder 构建 CREATE TABLE 语句
    }
    
    @Override
    public String buildTableColumn(DataSourceConnectionInfo connectionInfo, 
                                   String schemaName, List<String> tableNames) {
        // 实现：批量构建表 DDL
        return tableNames.stream()
            .map(tableName -> queryTableDdl(connectionInfo, schemaName, tableName))
            .collect(Collectors.joining(";\n"));
    }
    
    @Override
    public String queryDatabaseTables(DataSourceConnectionInfo connectionInfo, 
                                      String schemaName) {
        // 实现：查询所有表及元数据（注释、外键等）
    }
    
    @Override
    public String queryRedisSchema(DataSourceConnectionInfo connectionInfo, 
                                   String schemaName, List<String> tableNames) {
        // 实现：查询 Redis key 列表
    }
    
    @Override
    public String queryDatabaseType(Long dataSourceId) {
        // 实现：从 DataSource 获取类型
    }
}
```

---

### 第二阶段：创建提示词建造者 🔨

**创建目录：** `chat2db-server-web-api/src/main/java/ai/chat2db/server/web/api/controller/ai/prompt/`

#### 2.1 创建 `PromptTemplate.java` - 模板值对象

```java
package ai.chat2db.server.web.api.controller.ai.prompt;

import lombok.Builder;
import lombok.Data;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;

@Data
@Builder
public class PromptTemplate {
    private String name;
    private String template;
    private PromptType promptType;
    private String description;
}
```

#### 2.2 创建 `PromptTemplateRegistry.java` - 模板注册表

```java
package ai.chat2db.server.web.api.controller.ai.prompt;

import org.springframework.stereotype.Component;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Map;

@Component
public class PromptTemplateRegistry {
    
    private final Map<PromptType, PromptTemplate> templates = new EnumMap<>(PromptType.class);
    
    @PostConstruct
    public void init() {
        // 注册所有提示词模板
        register(PromptType.NL_2_SQL, buildNl2SqlTemplate());
        register(PromptType.SQL_EXPLAIN, buildSqlExplainTemplate());
        register(PromptType.SQL_OPTIMIZER, buildSqlOptimizerTemplate());
        register(PromptType.SQL_2_SQL, buildSql2SqlTemplate());
        register(PromptType.SELECT_TABLES, buildSelectTablesTemplate());
        register(PromptType.TEXT_GENERATION, buildTextGenerationTemplate());
        register(PromptType.TITLE_GENERATION, buildTitleGenerationTemplate());
        register(PromptType.NL_2_COMMENT, buildNl2CommentTemplate());
    }
    
    private void register(PromptType type, PromptTemplate template) {
        templates.put(type, template);
    }
    
    public PromptTemplate getTemplate(PromptType type) {
        return templates.getOrDefault(type, getDefaultTemplate());
    }
    
    public PromptTemplate getTemplate(String code) {
        PromptType type = PromptType.valueOf(code);
        return getTemplate(type);
    }
    
    private PromptTemplate getDefaultTemplate() {
        return templates.get(PromptType.NL_2_SQL);
    }
    
    // 构建各个模板...
    private PromptTemplate buildNl2SqlTemplate() {
        return PromptTemplate.builder()
            .name("nl_2_sql")
            .promptType(PromptType.NL_2_SQL)
            .template("### 请根据以下 table properties 和 SQL input{description}. {ext}\n" +
                     "#\n" +
                     "### {db_type} SQL tables, with their properties:\n" +
                     "#\n" +
                     "# {schema}\n" +
                     "#\n" +
                     "#\n" +
                     "### SQL input: {message}")
            .build();
    }
    
    // ... 其他模板构建方法
}
```

#### 2.3 创建 `PromptValidator.java` - 验证器

```java
package ai.chat2db.server.web.api.controller.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class PromptValidator {
    
    private static final int MAX_PROMPT_LENGTH = 15400;
    private static final int TOKEN_CONVERT_CHAR_LENGTH = 4;
    
    public boolean isValidLength(String prompt) {
        return prompt != null && 
               prompt.length() / TOKEN_CONVERT_CHAR_LENGTH <= MAX_PROMPT_LENGTH;
    }
    
    public int getTokenCount(String prompt) {
        if (prompt == null) return 0;
        return prompt.length() / TOKEN_CONVERT_CHAR_LENGTH;
    }
    
    public String cleanPrompt(String prompt) {
        if (prompt == null) return "";
        return prompt.replaceAll("[\r\t]", "").replaceAll("#", "");
    }
}
```

#### 2.4 创建 `PromptContext.java` - 构建上下文

```java
package ai.chat2db.server.web.api.controller.ai.prompt;

import lombok.Builder;
import lombok.Data;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;

@Data
@Builder
public class PromptContext {
    
    private PromptType promptType;
    private String message;
    private String ext;
    private String schemaDdl;
    private String dataSourceType;
    private String targetSqlType;
    private boolean forTableSelection;
    
    public static PromptContext from(ChatQueryRequest request) {
        return PromptContext.builder()
            .message(request.getMessage())
            .ext(request.getExt())
            .dataSourceType(request.getDataSourceType())
            .targetSqlType(request.getDestSqlType())
            .build();
    }
}
```

#### 2.5 创建 `PromptBuilder.java` - 建造者接口

```java
package ai.chat2db.server.web.api.controller.ai.prompt;

public interface PromptBuilder {
    PromptBuilder context(PromptContext context);
    PromptBuilder message(String message);
    PromptBuilder ext(String ext);
    PromptBuilder schema(String schemaDdl);
    PromptBuilder dataSourceType(String dataSourceType);
    PromptBuilder targetSqlType(String targetSqlType);
    PromptBuilder forTableSelection(boolean forTableSelection);
    String build();
    boolean validate();
}
```

#### 2.6 创建 `PromptBuilderImpl.java` - 建造者实现

```java
package ai.chat2db.server.web.api.controller.ai.prompt;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;

@Component
public class PromptBuilderImpl implements PromptBuilder {
    
    private final PromptTemplateRegistry templateRegistry;
    private final PromptValidator validator;
    
    private PromptContext context;
    
    public PromptBuilderImpl(PromptTemplateRegistry templateRegistry, 
                            PromptValidator validator) {
        this.templateRegistry = templateRegistry;
        this.validator = validator;
    }
    
    @Override
    public PromptBuilder context(PromptContext context) {
        this.context = context;
        return this;
    }
    
    @Override
    public PromptBuilder message(String message) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setMessage(message);
        return this;
    }
    
    @Override
    public PromptBuilder ext(String ext) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setExt(ext);
        return this;
    }
    
    @Override
    public PromptBuilder schema(String schemaDdl) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setSchemaDdl(schemaDdl);
        return this;
    }
    
    @Override
    public PromptBuilder dataSourceType(String dataSourceType) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setDataSourceType(dataSourceType);
        return this;
    }
    
    @Override
    public PromptBuilder targetSqlType(String targetSqlType) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setTargetSqlType(targetSqlType);
        return this;
    }
    
    @Override
    public PromptBuilder forTableSelection(boolean forTableSelection) {
        if (this.context == null) {
            this.context = new PromptContext();
        }
        this.context.setForTableSelection(forTableSelection);
        return this;
    }
    
    @Override
    public String build() {
        validateContext();
        
        PromptType type = context.getPromptType();
        PromptTemplate template = templateRegistry.getTemplate(type);
        
        String builtPrompt = fillTemplate(template, context);
        
        if (context.isForTableSelection()) {
            builtPrompt = appendTableSelectionInstruction(builtPrompt);
        }
        
        return validator.cleanPrompt(builtPrompt);
    }
    
    @Override
    public boolean validate() {
        String builtPrompt = build();
        return validator.isValidLength(builtPrompt);
    }
    
    private void validateContext() {
        if (context == null) {
            throw new IllegalStateException("PromptContext is null");
        }
        if (StringUtils.isBlank(context.getMessage())) {
            throw new IllegalArgumentException("Message is required");
        }
    }
    
    private String fillTemplate(PromptTemplate template, PromptContext context) {
        String templateStr = template.getTemplate();
        
        return templateStr
            .replace("{description}", context.getPromptType().getDescription())
            .replace("{ext}", StringUtils.defaultString(context.getExt(), ""))
            .replace("{db_type}", StringUtils.defaultString(context.getDataSourceType(), "MYSQL"))
            .replace("{schema}", StringUtils.defaultString(context.getSchemaDdl(), ""))
            .replace("{message}", context.getMessage());
    }
    
    private String appendTableSelectionInstruction(String prompt) {
        return prompt + "\n\n请只输出 JSON，格式：{\"table_names\":[\"表名 1\",\"表名 2\"]}，不要输出其他文字。";
    }
}
```

---

### 第三阶段：删除 PromptService 门面 🗑️

#### 3.1 删除文件

```
删除：chat2db-server-web-api/src/main/java/ai/chat2db/server/web/api/controller/ai/utils/PromptService.java
```

#### 3.2 迁移引用

**原代码：**
```java
@Autowired
private PromptService promptService;

String schema = promptService.buildTableColumn(param, tableNames);
String prompt = promptService.buildAutoPrompt(request);
```

**新代码：**
```java
@Autowired
private DatabaseService databaseService;

@Autowired
private PromptBuilderImpl promptBuilder;

String schema = databaseService.buildTableColumn(connectionInfo, schemaName, tableNames);
String prompt = promptBuilder.context(context).build();
```

---

### 第四阶段：重构 Action 类 🧹

#### 4.1 重构 `BuildPromptAction`

```java
package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptBuilder;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptContext;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BuildPromptAction extends BaseChatAction {
    
    @Autowired
    private PromptBuilder promptBuilder;
    
    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext chatContext = getChatContext(context);
        if (chatContext.isCancelled()) {
            return;
        }
        
        try {
            sendStateEvent(chatContext.getSseEmitter(), 
                          ChatState.BUILDING_PROMPT, "正在构建提示...");
            
            ChatQueryRequest request = chatContext.getRequest();
            String schemaDdl = chatContext.getSchemaDdl();
            
            PromptType promptType = determinePromptType(request);
            
            PromptContext promptContext = PromptContext.builder()
                .promptType(promptType)
                .message(request.getMessage())
                .ext(request.getExt())
                .schemaDdl(schemaDdl)
                .dataSourceType(guessDataSourceType(schemaDdl))
                .targetSqlType(request.getDestSqlType())
                .forTableSelection(false)
                .build();
            
            String builtPrompt = promptBuilder.context(promptContext).build();
            chatContext.setBuiltPrompt(builtPrompt);
            
            context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.PROMPT_BUILT).build()
            ).subscribe();
            
        } catch (Exception e) {
            log.error("Build prompt failed", e);
            sendError(getChatContext(context).getSseEmitter(), 
                     "构建提示失败：" + e.getMessage());
            context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.PROMPT_BUILD_FAILED).build()
            ).subscribe();
        }
    }
    
    private PromptType determinePromptType(ChatQueryRequest request) {
        String promptType = StringUtils.isBlank(request.getPromptType())
            ? PromptType.NL_2_SQL.getCode()
            : request.getPromptType();
        return PromptType.valueOf(promptType);
    }
    
    private String guessDataSourceType(String schemaDdl) {
        if (StringUtils.isEmpty(schemaDdl)) return "MYSQL";
        String upper = schemaDdl.toUpperCase();
        if (upper.contains("MYSQL") || upper.contains("AUTO_INCREMENT")) {
            return "MYSQL";
        } else if (upper.contains("POSTGRES") || upper.contains("SERIAL")) {
            return "POSTGRESQL";
        } else if (upper.contains("ORACLE") || upper.contains("NUMBER(")) {
            return "ORACLE";
        }
        return "MYSQL";
    }
}
```

#### 4.2 重构 `AutoSelectTablesAction`

```java
package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import ai.chat2db.server.web.api.controller.ai.prompt.PromptBuilder;
import ai.chat2db.server.web.api.controller.ai.prompt.PromptContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AutoSelectTablesAction extends BaseChatAction {
    
    @Autowired
    private PromptBuilder promptBuilder;
    
    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        if (ctx.isCancelled()) {
            return;
        }
        
        try {
            sendStateEvent(ctx.getSseEmitter(), 
                          ChatState.AUTO_SELECTING_TABLES, "正在选择相关表...");
            
            List<String> tableNames = selectTables(ctx);
            
            if (CollectionUtils.isNotEmpty(tableNames)) {
                ctx.getRequest().setTableNames(tableNames);
                ctx.setSelectedTables(tableNames);
                sendTablesSelected(ctx.getSseEmitter(), tableNames);
            }
            
            context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.AUTO_SELECT_DONE).build()
            ).subscribe();
            
        } catch (Exception e) {
            log.error("Auto select tables failed", e);
            sendError(ctx.getSseEmitter(), "选表失败：" + e.getMessage());
            context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.AUTO_SELECT_FAILED).build()
            ).subscribe();
        }
    }
    
    private List<String> selectTables(ChatContext ctx) {
        String selectPrompt = buildSelectPrompt(ctx);
        
        ChatResponse chatResponse = ctx.getChatClient().prompt()
            .user(selectPrompt)
            .call()
            .chatResponse();
        
        String content = extractContent(chatResponse);
        return parseTableNames(content, ctx.getRequest().getTableNames());
    }
    
    private String buildSelectPrompt(ChatContext ctx) {
        PromptContext promptContext = PromptContext.builder()
            .promptType(PromptType.SELECT_TABLES)
            .message(ctx.getRequest().getMessage())
            .dataSourceType(ctx.getRequest().getDataSourceType())
            .forTableSelection(true)
            .build();
        
        return promptBuilder.context(promptContext).build();
    }
    
    // ... 其他方法保持不变
}
```

#### 4.3 重构 `FetchSchemaAction`

```java
package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FetchSchemaAction extends BaseChatAction {
    
    @Autowired
    private DatabaseService databaseService;
    
    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        if (ctx.isCancelled()) {
            return;
        }
        
        try {
            sendStateEvent(ctx.getSseEmitter(), 
                          ChatState.FETCHING_TABLE_SCHEMA, "正在获取表结构...");
            
            String schemaDdl = fetchSchemaDdl(ctx);
            ctx.setSchemaDdl(schemaDdl);
            
            if (CollectionUtils.isNotEmpty(ctx.getRequest().getTableNames())) {
                sendSchemaFetched(ctx.getSseEmitter(), schemaDdl);
            }
            
            context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.SCHEMA_FETCHED).build()
            ).subscribe();
            
        } catch (Exception e) {
            log.error("Fetch schema failed", e);
            sendError(ctx.getSseEmitter(), "获取表结构失败：" + e.getMessage());
            context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.FETCH_SCHEMA_FAILED).build()
            ).subscribe();
        }
    }
    
    private String fetchSchemaDdl(ChatContext ctx) {
        ChatQueryRequest request = ctx.getRequest();
        
        if (isTextGeneration(request)) {
            return "";
        } else if (hasTableNames(request)) {
            return queryTablesWithNames(request);
        } else {
            return queryAllTables(request);
        }
    }
    
    private boolean isTextGeneration(ChatQueryRequest request) {
        return PromptType.TEXT_GENERATION.getCode().equals(request.getPromptType());
    }
    
    private boolean hasTableNames(ChatQueryRequest request) {
        return CollectionUtils.isNotEmpty(request.getTableNames());
    }
    
    private String queryTablesWithNames(ChatQueryRequest request) {
        // 使用 DatabaseService 的新方法
        return databaseService.buildTableColumn(
            buildConnectionInfo(request),
            request.getSchemaName(),
            request.getTableNames()
        );
    }
    
    private String queryAllTables(ChatQueryRequest request) {
        // 使用 DatabaseService 的新方法
        return databaseService.queryDatabaseTables(
            buildConnectionInfo(request),
            request.getSchemaName()
        );
    }
}
```

---

### 第五阶段：重构 StreamAction 🔒

```java
package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import java.io.IOException;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.alibaba.fastjson2.JSONObject;
import com.unfbx.chatgpt.entity.chat.Message;

import ai.chat2db.server.web.api.controller.ai.prompt.PromptValidator;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatContext;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
public class StreamAction extends BaseChatAction {
    
    @Autowired
    private PromptValidator promptValidator;
    
    @Override
    public void execute(StateContext<ChatState, ChatEvent> context) {
        ChatContext ctx = getChatContext(context);
        if (ctx.isCancelled()) {
            return;
        }
        
        String prompt = ctx.getBuiltPrompt();
        
        // 使用 PromptValidator 验证
        if (!promptValidator.isValidLength(prompt)) {
            sendError(ctx.getSseEmitter(), "提示语超出最大长度");
            context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
            ).subscribe();
            return;
        }
        
        try {
            sendStateEvent(ctx.getSseEmitter(), ChatState.STREAMING, "AI 正在生成...");
            
            Flux<String> flux = ctx.getChatClient().prompt()
                .user(prompt)
                .stream()
                .content();
            
            // ... 流处理逻辑保持不变
            
        } catch (Exception e) {
            log.error("Start streaming failed", e);
            sendError(ctx.getSseEmitter(), "启动 AI 流式调用失败：" + e.getMessage());
            context.getStateMachine().sendEvent(
                MessageBuilder.withPayload(ChatEvent.AI_CALL_FAILED).build()
            ).subscribe();
        }
    }
}
```

---

## 新架构优势

| 方面 | 重构前 | 重构后 |
|------|--------|--------|
| **服务层** | PromptService (311 行上帝类) | DatabaseService (复用现有服务) |
| **门面模式** | PromptService 作为门面 | 删除门面，直接使用建造者 |
| **提示词构建** | 分散在 Action 和 Service 中 | PromptBuilder 集中管理 |
| **模板管理** | 硬编码在方法中 | PromptTemplateRegistry 集中注册 |
| **代码重复** | BuildPromptAction 和 PromptService 重复 | 单一事实来源 |
| **可测试性** | 难以独立测试模板 | 模板和建造者可单元测试 |
| **类数量** | 1 个大类 (PromptService) | 6 个专注类 |
| **职责分离** | 混乱 | 清晰 |

---

## 重构后文件结构

```
chat2db-server/
└── chat2db-server-web/
    └── chat2db-server-web-api/
        └── src/main/java/ai/chat2db/server/web/api/controller/ai/
            ├── prompt/                          # 新增包
            │   ├── PromptBuilder.java           # 建造者接口
            │   ├── PromptBuilderImpl.java       # 建造者实现
            │   ├── PromptContext.java           # 上下文对象
            │   ├── PromptTemplate.java          # 模板值对象
            │   ├── PromptTemplateRegistry.java  # 模板注册表
            │   └── PromptValidator.java         # 验证器
            │
            ├── statemachine/
            │   └── actions/
            │       ├── BuildPromptAction.java   # 重构：使用 PromptBuilder
            │       ├── AutoSelectTablesAction.java # 重构：使用 PromptBuilder
            │       ├── FetchSchemaAction.java   # 重构：使用 DatabaseService
            │       └── StreamAction.java        # 重构：使用 PromptValidator
            │
            ├── enums/
            │   └── PromptType.java              # 保持不变
            │
            └── utils/
                └── PromptService.java           # 删除 🗑️

chat2db-server-domain/
└── chat2db-server-domain-api/
    └── src/main/java/ai/chat2db/server/domain/api/service/
        └── DatabaseService.java                 # 扩展：添加 Schema 获取方法
```

---

## 迁移步骤

### 第 1 周：扩展 DatabaseService
1. 在 `DatabaseService` 接口中添加新方法
2. 在 `DatabaseServiceImpl` 中实现
3. 编写单元测试

### 第 2 周：创建 Prompt 包
1. 创建 `PromptTemplate` 和 `PromptTemplateRegistry`
2. 创建 `PromptValidator`
3. 创建 `PromptContext`
4. 实现 `PromptBuilder` 接口和实现

### 第 3 周：重构 Action 类
1. 重构 `BuildPromptAction`
2. 重构 `AutoSelectTablesAction`
3. 重构 `FetchSchemaAction`
4. 重构 `StreamAction`

### 第 4 周：清理与测试
1. 删除 `PromptService`
2. 更新所有引用
3. 集成测试
4. 回归测试

---

## 测试策略

### 单元测试

```java
// PromptTemplateRegistryTest.java
@Test
void testGetTemplateByType() {
    PromptTemplate template = registry.getTemplate(PromptType.NL_2_SQL);
    assertNotNull(template);
    assertTrue(template.getTemplate().contains("{schema}"));
}

// PromptBuilderImplTest.java
@Test
void testBuildPromptWithSchema() {
    PromptContext context = PromptContext.builder()
        .promptType(PromptType.NL_2_SQL)
        .message("查询用户")
        .schemaDdl("CREATE TABLE user...")
        .dataSourceType("MYSQL")
        .build();
    
    String prompt = promptBuilder.context(context).build();
    assertTrue(prompt.contains("MYSQL SQL tables"));
    assertTrue(prompt.contains("查询用户"));
}

// PromptValidatorTest.java
@Test
void testPromptLengthValidation() {
    String longPrompt = "x".repeat(70000); // 超过 15400 tokens
    assertFalse(validator.isValidLength(longPrompt));
}
```

### 集成测试

```java
// BuildPromptActionIntegrationTest.java
@Test
void testBuildPromptActionFlow() {
    // 模拟状态机执行
    stateMachine.sendEvent(MessageBuilder.withPayload(ChatEvent.TABLES_PROVIDED).build());
    
    // 验证 prompt 被正确构建
    verify(chatContext).setBuiltPrompt(anyString());
    verify(stateMachine).sendEvent(
        MessageBuilder.withPayload(ChatEvent.PROMPT_BUILT).build()
    );
}
```

---

## 风险提示

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| DatabaseService 方法实现复杂 | 中 | 充分测试，复用现有 TableService |
| Action 类重构遗漏 | 高 | 全局搜索 PromptService 引用 |
| 模板格式变化 | 中 | 保持向后兼容，渐进迁移 |
| 性能回退 | 低 | 基准测试，优化模板加载 |

---

## 成功指标

1. **代码质量**
   - 删除 PromptService (311 行)
   - BuildPromptAction 减少到 ~60 行
   - 测试覆盖率 > 80%

2. **架构清晰度**
   - 提示词模板集中管理
   - DatabaseService 负责数据获取
   - PromptBuilder 负责提示词构建

3. **开发效率**
   - 新增提示词类型 < 30 分钟
   - 模板修改无需重新编译（如外部化）

---

## 总结

本次重构核心变更：

1. ✅ **删除 PromptService 门面** - 减少中间层
2. ✅ **扩展 DatabaseService** - 复用现有服务，添加 Schema 获取方法
3. ✅ **创建 PromptBuilder** - 专注提示词构建
4. ✅ **创建 PromptTemplateRegistry** - 集中管理模板
5. ✅ **重构 Action 类** - 使用新架构

重构后架构更清晰、职责更明确、易于维护和扩展。
