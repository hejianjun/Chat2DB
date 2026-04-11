# AI 生成标题功能设计文档

## 概述

本文档描述了 Chat2DB 中 AI 生成标题功能的后端状态机流转和前端交互时序设计。

## 1. 后端状态机流转图

```
                              ┌──────────┐
                              │   IDLE   │ ← 初始状态
                              └─────┬────┘
                                    │ 接收 /api/ai/chat 请求
                                    │ promptType=TITLE_GENERATION
                                    ▼
                          ┌───────────────────┐
                          │ BUILDING_PROMPT   │
                          │  (构建提示词)      │
                          └─────────┬─────────┘
                                    │ action: BuildPromptAction
                                    │ - 加载 title_generation 模板
                                    │ - 填充 SQL 内容
                                    ▼
                          ┌───────────────────┐
                          │    STREAMING      │◀─────────────────┐
                          │  (AI 流式响应)     │                  │
                          └─────────┬─────────┘                  │
                                    │                            │
                    ┌───────────────┼───────────────┐            │
                    │               │               │            │
             [流式返回片段]    [AI 完成响应]    [发生错误]        │
                    │               │               │            │
                    ▼               ▼               ▼            │
          ┌──────────────┐  ┌──────────────┐ ┌──────────────┐      │
          │ SSE message  │  │ SSE [DONE]   │ │ SSE error    │      │
          │ content片段  │  │ + 结束连接   │ │ + 结束连接   │      │
          └──────────────┘  └──────────────┘ └──────────────┘      │
                    │                                      │        │
                    │                                      ▼        │
                    │                            ┌──────────────┐  │
                    └────────────────────────────│   FAILED     │  │
                                                 │ (失败状态)   │  │
                                                 └──────────────┘  │
                                                    │              │
                                                    │ [重试请求]   │
                                                    └──────────────┘
```

### 状态说明

| 状态 | 说明 |
|------|------|
| IDLE | 空闲状态，等待请求 |
| BUILDING_PROMPT | 构建提示词：从 `prompt-templates.yml` 加载模板，填充 SQL 内容 |
| STREAMING | 调用 AI 模型（OpenAI/Anthropic），流式返回内容 |
| FAILED | AI 调用失败或网络错误 |

### 事件说明

| 事件 | 说明 |
|------|------|
| REQUEST_GENERATE_TITLE | 接收标题生成请求 |
| PROMPT_BUILT | 提示词构建完成 |
| STREAM_FINISHED | AI 流式响应完成，发送 [DONE] |
| AI_CALL_FAILED | AI 调用失败 |
| CANCEL | 用户取消操作 |
| RETRY | 重试请求 |

### 状态机动作（Actions）

| Action | 职责 |
|--------|------|
| `BuildPromptAction` | 根据模板构建发送给 AI 的提示词 |
| `StreamAction` | 调用 AI 模型进行流式输出 |

## 2. 前端交互时序图

```
┌──────────┐      ┌──────────┐      ┌───────────┐      ┌──────────┐      ┌──────────────┐
│   用户    │      │  前端组件  │      │ 前端服务   │      │  后端     │      │   AI 模型     │
│          │      │ Workspace  │      │ indexedDB │      │ Spring   │      │ (OpenAI/    │
│          │      │ Tabs       │      │           │      │ Boot     │      │ Anthropic)  │
└────┬─────┘      └────┬─────┘      └─────┬─────┘      └────┬─────┘      └──────┬───────┘
     │                 │                  │                 │                   │
     │ 1. 右键点击 Tab  │                  │                 │                   │
     │    选择"AI 生成标题" │                  │                 │                   │
     ├────────────────>│                  │                 │                   │
     │                 │                  │                 │                   │
     │                 │ 2. 获取 SQL 内容   │                 │                   │
     │                 │ (从 tabData 或     │                 │                   │
     │                 │ indexedDB 获取未保存 │                 │                   │
     │                 │ 的修改)           │                 │                   │
     │                 ├─────────────────>│                 │                   │
     │                 │<─────────────────│                 │                   │
     │                 │ 返回 SQL 内容      │                 │                   │
     │                 │                  │                 │                   │
     │                 │ 3. 设置生成中标记  │                 │                   │
     │                 │ setGeneratingTitleKey │              │                   │
     │                 │                  │                 │                   │
     │                 │ 4. 创建 SSE 连接   │                 │                   │
     │                 │ connectToEventSource │              │                   │
     │                 │ GET /api/ai/chat? │                 │                   │
     │                 │ message={sql}     │                 │                   │
     │                 │ promptType=       │                 │                   │
     │                 │ TITLE_GENERATION  │                 │                   │
     │                 ├───────────────────────────────────>│                   │
     │                 │                  │                 │                   │
     │                 │                  │                 │ 5. 创建 StateMachine │
     │                 │                  │                 │    ChatContext     │
     │                 │                  │                 │                   │
     │                 │                  │                 │ 6. 状态流转:      │
     │                 │                  │                 │ IDLE →            │
     │                 │                  │                 │ BUILDING_PROMPT   │
     │                 │                  │                 │                   │
     │                 │                  │                 │ 7. BuildPromptAction │
     │                 │                  │                 │ - 加载模板         │
     │                 │                  │                 │ - 填充 SQL 内容   │
     │                 │                  │                 │                   │
     │                 │                  │                 │ 8. 状态流转:      │
     │                 │                  │                 │ → STREAMING       │
     │                 │                  │                 │                   │
     │                 │                  │                 ├──────────────────>│
     │                 │                  │                 │ 9. 调用 AI 模型     │
     │                 │                  │                 │ (流式请求)         │
     │                 │                  │                 │<──────────────────┤
     │                 │                  │                 │ 10. 流式响应      │
     │                 │                  │                 │───────────────────>
     │                 │                  │                 │                   │
     │                 │<───────────────────────────────────│                   │
     │                 │ 11. SSE event: state              │                   │
     │                 │     data: {"state":"STREAMING"}   │                   │
     │                 │                  │                 │                   │
     │                 │<───────────────────────────────────│                   │
     │                 │ 12. SSE event: message (多次)     │                   │
     │                 │     data: {"content":"..."}       │                   │
     │                 │                  │                 │                   │
     │                 │ 13. 累积内容     │                 │                   │
     │                 │ handleMessage   │                 │                   │
     │                 │ 更新 UI 显示加载中  │                 │                   │
     │                 │                  │                 │                   │
     │                 │<───────────────────────────────────│                   │
     │                 │ 14. SSE event: [DONE]             │                   │
     │                 │     (AI 响应完成，连接关闭)         │                   │
     │                 │                  │                 │                   │
     │                 │ 15. 解析标题     │                 │                   │
     │                 │ handleComplete  │                 │                   │
     │                 │ - 清理引号和空白  │                 │                   │
     │                 │ - 调用           │                 │                   │
     │                 │   historyService│                 │                   │
     │                 │   .updateSavedConsole │            │                   │
     │                 │                  │                 │                   │
     │                 │ 16. 更新标题到   │                 │                   │
     │                 │ indexedDB       │                 │                   │
     │                 ├─────────────────>│                 │                   │
     │                 │                  │                 │                   │
     │                 │ 17. 清除生成中标记 │                 │                   │
     │                 │ clearGeneratingTitleKey│           │                   │
     │                 │                  │                 │                   │
     │<────────────────│                  │                 │                   │
     │ 18. Tab 标题已更新 │                  │                 │                   │
     │                 │                  │                 │                   │
```

### 错误处理分支

```
     │                 │<───────────────────────────────────│                   │
     │                 │ SSE event: error  │                 │                   │
     │                 │ data: {"error":"..."}              │                   │
     │                 │                  │                 │                   │
     │                 │ handleError      │                 │                   │
     │                 │ message.error(   │                 │                   │
     │                 │   'AI 生成标题失败'  │                 │                   │
     │                 │ )                │                 │                   │
     │                 │                  │                 │                   │
```

## 3. 关键数据结构

### 3.1 前端请求参数

```typescript
interface AIChatParams {
  message: string;        // SQL 内容
  promptType: string;     // 'TITLE_GENERATION'
  dataSourceId?: number;  // 数据源 ID
  databaseName?: string;  // 数据库名
  schemaName?: string;    // Schema 名
}
```

### 3.2 后端 SSE 响应格式

```sse
// 状态事件
event: state
data: {"state":"STREAMING"}

// 内容事件（流式返回，可能多次）
event: message
data: {"content":"查询"}

event: message
data: {"content":"用户"}

event: message
data: {"content":"订单"}

// 完成事件（AI 响应结束，直接发送 [DONE]）
event: [DONE]
data: {"name":"set_title","arguments":{"title_name":"查询用户订单"}}

// 错误事件
event: error
data: {"error":"AI 调用失败：网络超时"}
```

### 3.3 状态机上下文

```java
public class ChatContext {
    private String conversationId;     // 会话 ID
    private PromptType promptType;     // TITLE_GENERATION
    private String message;             // SQL 内容
    private Long dataSourceId;          // 数据源 ID
    private String databaseName;        // 数据库名
    private String schemaName;          // Schema 名
    private List<Table> selectedTables; // 选中的表
    private String schemaDDL;           // 表结构 DDL
    private String generatedTitle;      // 生成的标题
}
```

## 4. 异常场景处理

| 场景 | 前端处理 | 后端状态 |
|------|---------|---------|
| AI 调用超时 | `message.error('AI 生成标题失败，请重试')` | FAILED |
| 用户关闭 Tab | 关闭 SSE 连接，发送取消请求 | FAILED (CANCEL 事件) |
| 网络中断 | 重连机制（可选） | FAILED |
| SQL 内容为空 | 提示用户先编写 SQL | 不发起请求 |
| AI 返回格式错误 | 正则提取标题，失败则使用默认标题 | 发送 [DONE]，前端处理 |

## 5. 代码位置参考

### 前端代码

| 文件路径 | 功能说明 |
|---------|---------|
| `chat2db-client/src/pages/main/workspace/components/WorkspaceTabs/index.tsx` | 核心实现：`handleGenerateTitle` 函数（第 161-256 行） |
| `chat2db-client/src/components/Tabs/index.tsx` | Tab 右键菜单中的"AI 生成标题"选项（第 215-228 行） |
| `chat2db-client/src/utils/eventSource.ts` | SSE 连接工具（`connectToEventSource`） |
| `chat2db-client/src/i18n/zh-cn/common.ts` | 国际化：`'common.text.generateTitle': 'AI 生成标题'` |
| `chat2db-client/src/i18n/zh-cn/workspace.ts` | 国际化：`'workspace.tips.generateTitleFailed': 'AI 生成标题失败，请重试'` |

### 后端代码

| 文件路径 | 功能说明 |
|---------|---------|
| `chat2db-server/.../controller/ai/ChatController.java` | AI 聊天控制器：`/api/ai/chat` 接口 |
| `chat2db-server/.../statemachine/ChatStateMachineConfig.java` | 状态机配置：定义 AI 聊天的状态流转 |
| `chat2db-server/.../statemachine/ChatState.java` | 状态枚举（IDLE, STREAMING, COMPLETED, FAILED 等） |
| `chat2db-server/.../statemachine/ChatEvent.java` | 事件枚举（含 `REQUEST_GENERATE_TITLE`） |
| `chat2db-server/.../statemachine/actions/BuildPromptAction.java` | 构建 AI 提示词动作 |
| `chat2db-server/.../statemachine/actions/StreamAction.java` | AI 流式输出动作 |
| `chat2db-server/.../enums/PromptType.java` | 提示类型枚举（含 `TITLE_GENERATION`） |
| `chat2db-server/.../prompt/PromptBuilderImpl.java` | 提示词构建器实现 |
| `chat2db-server/.../prompt/PromptTemplateRegistry.java` | 提示词模板注册表 |
| `chat2db-server/.../resources/prompt-templates.yml` | 提示词模板配置文件 |
| `chat2db-server/.../config/AiChatConfig.java` | AI 客户端配置（OpenAI/Anthropic） |

## 6. AI 提供商配置

### 支持的 AI 提供商

| 提供商 | 配置项前缀 | 默认模型 |
|--------|-----------|---------|
| OpenAI | `ai.openai.` | `gpt-4o-mini` |
| Anthropic | `ai.anthropic.` | `claude-3-5-sonnet-20241022` |

### 可配置参数

- `apiKey` - API 密钥
- `apiHost` - API 主机（仅 OpenAI）
- `model` - 模型名称
- `temperature` - 温度（默认 0.7）
- `maxTokens` - 最大 token 数（默认 4096）

## 7. 提示词模板配置

标题生成的提示词模板位于 `prompt-templates.yml`：

```yaml
title_generation:
  name: "title_generation"
  description: "生成标题"
  template: |
    请为以下 SQL 查询生成一个简洁的中文标题（不超过 20 字）：
    {message}
    
    输出格式："你的标题"
```

## 8. 扩展新 AI 功能

如需添加新的 AI 功能类型：

1. 在 `PromptType.java` 添加新枚举
2. 在 `ChatEvent.java` 添加对应事件
3. 在 `prompt-templates.yml` 添加模板
4. 在前端调用时传入新的 `promptType`

---

**文档版本**: 1.0  
**最后更新**: 2026-04-11  
**维护者**: Chat2DB Team