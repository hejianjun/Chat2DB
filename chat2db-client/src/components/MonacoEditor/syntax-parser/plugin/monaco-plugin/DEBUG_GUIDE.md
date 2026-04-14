# SQL 补全调试日志使用指南

## 快速开始

### 1. 打开浏览器开发者工具

- Chrome/Edge: `F12` 或 `Ctrl+Shift+I`
- Firefox: `F12`

### 2. 打开 Console 标签

### 3. 在 SQL 编辑器中输入 SQL

例如：
```sql
select * from AKMG_APP t where t.
```

### 4. 查看日志输出

日志按以下格式组织：

```
[SQL 补全] - Monaco 插件层（补全生成）
[Reader] - Reader 层（语义分析）
[Parser] - Parser 层（语法分析）
[Lexer] - Lexer 层（词法分析）
[SQL 补全 - API] - 后端 API 调用
```

## 日志级别说明

### 关键日志（必看）

```javascript
[SQL 补全] === 开始补全流程 ===
[SQL 补全] 光标类型：tableFieldAfterGroup  // ← 关键！识别补全类型
[SQL 补全] 过滤后字段数量：10  // ← 关键！实际显示的字段数
[SQL 补全] 最终补全项总数：15  // ← 关键！总补全项数
```

### 详细日志（调试用）

```javascript
[Reader] getCursorInfo - keyPath: [...]
[Reader] getFieldsByFromClause - 别名：{ value: 't' }
[SQL 补全 - API] 获取表字段，表信息：{ tableName: 'users' }
```

## 常见场景日志分析

### 场景 1: 正常补全

```
[SQL 补全] === 开始补全流程 ===
[SQL 补全] 光标类型：tableFieldAfterGroup
[SQL 补全] 表名限定字段模式，分组：t
[Reader] getFieldsByFromClause - 别名：{ value: 't' }
[Reader] getFieldsByFromClause - 使用别名作为分组名：t
[SQL 补全 - API] 获取到字段数量：10
[SQL 补全] 过滤后字段数量：10
[SQL 补全] 最终补全项总数：15
```

✅ **结论**: 一切正常，应该看到字段补全

### 场景 2: 无补全（API 失败）

```
[SQL 补全] 光标类型：tableFieldAfterGroup
[SQL 补全] 表名限定字段模式，分组：t
[SQL 补全 - API] 获取表字段，表信息：{ tableName: 'users' }
[SQL 补全 - API] 获取表字段失败：Error: Connection timeout
[SQL 补全] 获取到字段数量：0
[SQL 补全] 过滤后字段数量：0
[SQL 补全] 最终补全项总数：5  // 只有关键字
```

❌ **结论**: 后端 API 调用失败，检查网络连接和后端服务

### 场景 3: 无补全（别名未识别）

```
[SQL 补全] 光标类型：tableFieldAfterGroup
[SQL 补全] 表名限定字段模式，分组：t
[Reader] getFieldsByFromClause - 别名：null
[Reader] getFieldsByFromClause - 多个表名有值，不提供分组名
[SQL 补全] 分组信息：[]
[SQL 补全] 过滤后字段数量：0
[SQL 补全] 最终补全项总数：5  // 只有关键字
```

❌ **结论**: 别名识别失败，检查 SQL 语法：
- 使用 `FROM users AS t` (推荐)
- 或 `FROM users t` (部分数据库支持)

### 场景 4: 只有关键字补全

```
[SQL 补全] === 开始补全流程 ===
[SQL 补全] 解析结果：{ success: false, hasError: true }
[SQL 补全] 光标信息：null
[SQL 补全] 无光标信息，返回关键字补全
[SQL 补全] 关键字补全数量：5
```

⚠️ **结论**: SQL 解析失败，检查 SQL 语法是否正确

## 日志过滤技巧

### 只看错误

在 Console 中输入：
```javascript
console.error
```
或点击 Console 面板的 "Errors" 级别

### 只看 SQL 补全日志

在 Console 的过滤器中输入：
```
[SQL 补全]
```

### 只看 Reader 层日志

在 Console 的过滤器中输入：
```
[Reader]
```

### 只看 API 调用

在 Console 的过滤器中输入：
```
[SQL 补全 - API]
```

## 性能分析

### 查看 API 响应时间

```javascript
[SQL 补全 - API] 获取表字段，表信息：{ tableName: 'users' }
// 记录时间戳
[SQL 补全 - API] 获取到字段数量：10
// 计算时间差
```

### 查看字段数量

```javascript
[SQL 补全] 过滤前字段数量：50  // 所有表字段总数
[SQL 补全] 过滤后字段数量：10  // 实际显示的字段数
```

## 报告问题时的日志收集

### 1. 清空 Console

点击 Console 面板的 "清除控制台" 按钮

### 2. 复现问题

在 SQL 编辑器中输入 SQL

### 3. 导出日志

右键点击 Console → "Save as..." → 保存为 `.log` 文件

### 4. 提供以下信息

```markdown
- SQL 语句：SELECT * FROM users t WHERE t.
- 期望行为：显示 users 表的字段
- 实际行为：无补全
- 数据库类型：MySQL
- 日志：[附上导出的日志文件]
```

## 禁用日志（可选）

如果日志太多，可以临时禁用：

### 方法 1: 注释日志代码

```typescript
// console.log('[SQL 补全] ...');
```

### 方法 2: 使用日志级别

```typescript
const DEBUG = false;
const log = DEBUG ? console.log : () => {};

log('[SQL 补全] 消息');
```

## 相关文档

- 完整原理说明：`PRINCIPLE_AND_LOGS.md`
- 测试用例：`TEST.md`
- 实现文档：`README.md`

## 快速参考卡片

```
┌─────────────────────────────────────────────────┐
│ 关键日志检查点                                   │
├─────────────────────────────────────────────────┤
│ 1. [SQL 补全] 光标类型                         │
│    - tableFieldAfterGroup → 表名限定字段        │
│    - tableField → 普通字段                      │
│    - tableName → 表名                           │
│                                                 │
│ 2. [Reader] 别名                               │
│    - 有值 → 别名识别成功                        │
│    - null → 别名识别失败                        │
│                                                 │
│ 3. [SQL 补全 - API] 字段数量                   │
│    - >0 → API 正常                              │
│    - 0 → API 失败或表无字段                     │
│                                                 │
│ 4. [SQL 补全] 过滤后字段数量                   │
│    - >0 → 补全正常                              │
│    - 0 → 分组不匹配                             │
└─────────────────────────────────────────────────┘
```

## 示例：完整的调试流程

### 问题：输入 `t.` 后没有字段补全

**步骤 1**: 打开 Console，清空日志

**步骤 2**: 输入 SQL
```sql
select * from users t where t.
```

**步骤 3**: 查看日志
```
[SQL 补全] 光标类型：tableFieldAfterGroup  ✅
[SQL 补全] 表名限定字段模式，分组：t  ✅
[Reader] getFieldsByFromClause - 别名：null  ❌
```

**步骤 4**: 识别问题
- 光标类型正确
- 分组名正确
- 别名为 null → 别名识别失败

**步骤 5**: 修复 SQL
```sql
select * from users AS t where t.
```

**步骤 6**: 重新测试
```
[Reader] getFieldsByFromClause - 别名：{ value: 't' }  ✅
[SQL 补全] 过滤后字段数量：10  ✅
[SQL 补全] 最终补全项总数：15  ✅
```

**结论**: 使用 `AS` 关键字后问题解决
