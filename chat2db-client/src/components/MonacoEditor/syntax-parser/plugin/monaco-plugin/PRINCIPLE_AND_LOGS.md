# SQL 智能补全原理与调试日志

## 一、整体架构

```
用户输入 SQL
     │
     ▼
┌─────────────────────────────────────┐
│ Monaco Editor 触发补全              │
│ triggerCharacters: . : = 等         │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 1. 词法分析 (Lexer)                  │
│    SQL 字符串 → Token 流              │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 2. 语法分析 (Parser)                 │
│    Token 流 → AST (抽象语法树)        │
│    记录光标位置 (cursorKeyPath)      │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 3. 语义分析 (Reader)                 │
│    从 AST 提取语义信息                │
│    识别：表名/字段/别名/函数         │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 4. 补全生成                          │
│    根据语义类型调用对应 API           │
│    - onSuggestTableNames            │
│    - onSuggestTableFields           │
└─────────────────────────────────────┘
     │
     ▼
用户看到补全列表
```

## 二、核心流程详解

### 2.1 词法分析（Lexer）

**文件**: `sql-parser/mysql/lexer.ts`

**作用**: 将 SQL 字符串转换为 Token 流

```javascript
输入： "SELECT * FROM users WHERE id = 1"

输出: [
  { type: 'word', value: 'SELECT', position: [0, 6] },
  { type: 'whitespace', value: ' ', ignore: true },
  { type: 'special', value: '*', position: [7, 8] },
  { type: 'whitespace', value: ' ', ignore: true },
  { type: 'word', value: 'FROM', position: [9, 13] },
  { type: 'whitespace', value: ' ', ignore: true },
  { type: 'word', value: 'users', position: [14, 19] },
  // ... 更多 Token
]
```

**关键日志**:
```javascript
// 在 parser/chain.ts 中添加
console.log('[Lexer] 输入 SQL:', text);
console.log('[Lexer] Token 数量:', tokens.length);
console.log('[Lexer] Token 流:', tokens);
```

### 2.2 语法分析（Parser）

**文件**: `parser/chain.ts`

**作用**: 基于递归下降算法构建 AST

**核心算法**:
1. 从根节点开始深度优先遍历
2. 尝试匹配每个子节点
3. 失败时回溯到上一个成功点
4. 记录光标前的所有节点路径

**AST 示例**:
```javascript
// SQL: SELECT * FROM users AS t WHERE t.id = 1

AST: [
  {
    type: 'statement',
    variant: 'select',
    result: [{ name: { value: '*' } }],
    from: {
      sources: [
        {
          type: 'identifier',
          variant: 'table',
          name: {
            type: 'identifier',
            variant: 'tableName',
            tableName: { value: 'users' }
          },
          alias: { value: 't' }  // ← 别名
        }
      ],
      where: { /* WHERE 条件 */ }
    }
  }
]
```

**关键日志** (已添加):
```javascript
console.log('[Parser] 开始解析 SQL');
console.log('[Parser] Token 数量:', tokens.length);
console.log('[Parser] 光标位置:', cursorIndex);
console.log('[Parser] 解析成功:', success);
console.log('[Parser] AST:', ast);
console.log('[Parser] 光标路径:', cursorKeyPath);
```

### 2.3 语义分析（Reader）

**文件**: `sql-parser/base/reader.ts`

**作用**: 从 AST 提取语义信息，识别补全类型

#### getCursorInfo - 识别光标处的语义类型

```javascript
输入: 
  - rootStatement: AST
  - cursorKeyPath: ["0", "from", "sources", "0", "name", "alias"]

处理流程:
1. 从 AST 中提取光标处的节点
2. 判断父节点类型
3. 返回语义类型

输出:
{
  type: 'tableFieldAfterGroup',  // 表名限定字段
  token: { value: 'id' },
  groupName: 't'                  // 表别名
}
```

**识别的类型**:
- `tableName`: 表名位置
- `tableField`: 普通字段
- `tableFieldAfterGroup`: `table.` 后面的字段
- `functionName`: 函数名

**关键日志** (已添加):
```javascript
console.log('[Reader] getCursorInfo - keyPath:', keyPath);
console.log('[Reader] getCursorInfo - cursorValue:', cursorValue);
console.log('[Reader] getCursorInfo - cursorKey:', cursorKey);
console.log('[Reader] getCursorInfo - parentStatement:', parentStatement);
console.log('[Reader] getCursorInfo - typePlusVariant:', typePlusVariant);
console.log('[Reader] getCursorInfo - 识别为:', result?.type);
```

#### getFieldsFromStatement - 收集可用字段

```javascript
输入:
  - rootStatement: AST
  - cursorKeyPath: 光标路径
  - getFieldsByTableName: 获取字段的回调函数

处理流程:
1. 找到光标所在的 statement
2. 根据 variant 选择处理策略:
   - 'select': 处理 FROM 子句
   - 'join': 找到父级 SELECT，处理所有 sources
3. 遍历所有表源 (sources + joins)
4. 为每个表收集字段
5. 设置 groupPickerName (表别名)

输出:
[
  {
    label: 'id',
    groupPickerName: 't',        // ← 别名，用于分组
    tableInfo: { tableName: { value: 'users' } },
    originFieldName: 'id'
  },
  { label: 'username', groupPickerName: 't', ... },
  // ... 更多字段
]
```

**关键日志** (已添加):
```javascript
console.log('[Reader] getFieldsFromStatement - cursorRootStatement:', cursorRootStatement?.variant);
console.log('[Reader] getFieldsFromStatement - SELECT 语句模式');
console.log('[Reader] getFieldsFromStatement - JOIN 语句模式');
console.log('[Reader] getFieldsByFromClause - typePlusVariant:', typePlusVariant);
console.log('[Reader] getFieldsByFromClause - 处理表标识符');
console.log('[Reader] getFieldsByFromClause - 别名:', itFromStatement.alias);
console.log('[Reader] getFieldsByFromClause - 使用别名作为分组名:', tableNameAlias);
console.log('[Reader] getFieldsByFromClause - 原始字段数:', originFields.length);
console.log('[Reader] getFieldsByFromClause - 处理后的字段示例:', originFields[0]);
```

### 2.4 补全生成（Monaco Plugin）

**文件**: `monaco-plugin/index.ts`

**作用**: 根据语义类型生成补全项

#### 补全策略分发

```javascript
switch (cursorInfo.type) {
  case 'tableField':
    // 普通字段补全（显示所有表的字段）
    fields = getFieldsFromStatement(...)
    groups = groupBy(fields, 'groupPickerName')
    return fields + keywords + groupNames  // 字段 + 关键字 + 表名分组
    
  case 'tableFieldAfterGroup':
    // 表名限定字段 (t.|)
    fields = getFieldsFromStatement(...)
    filteredFields = filter(fields, f.groupPickerName === 't')
    return filteredFields + keywords  // 只返回 t 表的字段
    
  case 'tableName':
    // 表名补全 (FROM |)
    tables = getAllTableNames(...)
    return tables + keywords
    
  default:
    // 只返回 SQL 关键字
    return keywords
}
```

**关键日志** (已添加):
```javascript
console.log('[SQL 补全] === 开始补全流程 ===');
console.log('[SQL 补全] 解析结果:', { success, hasError, cursorKeyPath });
console.log('[SQL 补全] 光标信息:', cursorInfo);
console.log('[SQL 补全] 光标类型:', cursorInfo.type);
console.log('[SQL 补全] 表字段补全模式');
console.log('[SQL 补全] 获取到字段数量:', cursorRootStatementFields.length);
console.log('[SQL 补全] 分组信息:', Object.keys(groups));
console.log('[SQL 补全] 最终补全项总数:', result.length);
console.log('[SQL 补全] 表名限定字段模式，分组:', groupName);
console.log('[SQL 补全] 过滤前字段数量:', cursorRootStatementFieldsAfter.length);
console.log('[SQL 补全] 过滤后字段数量:', filteredFields.length);
```

#### 后端 API 调用

**文件**: `sql-autocomplete.ts`

```javascript
// 获取表名列表
onSuggestTableNames: async (cursorInfo) => {
  data = await sqlService.getAllTableList({
    dataSourceId,
    databaseName,
    schemaName
  });
  return data.map(table => ({ label: table.name, ... }));
}

// 获取表字段列表
onSuggestTableFields: async (tableInfo) => {
  data = await sqlService.getColumnList({
    dataSourceId,
    databaseName,
    tableName: tableInfo.tableName.value
  });
  return data.map(column => ({ label: column.name, ... }));
}
```

**关键日志** (已添加):
```javascript
console.log('[SQL 补全 - API] 获取表名列表，boundInfo:', { dataSourceId, databaseName, schemaName });
console.log('[SQL 补全 - API] 获取到表名数量:', data.length);
console.log('[SQL 补全 - API] 获取表字段，表信息:', { tableName, namespace });
console.log('[SQL 补全 - API] 获取到字段数量:', data.length);
console.log('[SQL 补全 - API] 字段示例:', data.slice(0, 3));
```

## 三、表别名识别机制

### 3.1 别名在 AST 中的表示

```javascript
// SQL: FROM users AS t

AST 节点:
{
  type: 'identifier',
  variant: 'table',
  name: {
    type: 'identifier',
    variant: 'tableName',
    tableName: { value: 'users' }  // 原始表名
  },
  alias: { value: 't' }  // ← 别名
}
```

### 3.2 别名传递流程

```
1. Lexer 阶段
   "users AS t" → Token(users), Token(AS), Token(t)

2. Parser 阶段
   根据语法规则 tableSource: tableName [AS] alias
   构建 AST，设置 alias.value = 't'

3. Reader 阶段
   getFieldsByFromClause 读取 alias.value
   设置 groupPickerName = 't'

4. Monaco 阶段
   根据 groupPickerName 生成分组
   显示 "t" 作为补全项分组
   过滤 t.后面的字段时匹配 groupPickerName
```

### 3.3 关键代码

```typescript
// reader.ts:186-220
case 'identifier.table':
  const itFromStatement = fromStatement as ISource;
  
  // 1. 获取真实字段
  let originFields = await getFieldsByTableName(
    itFromStatement.name,
    cursorInfo.token.value,
    rootStatement
  );
  
  // 2. 提取别名
  const tableNameAlias: string = _.get(itFromStatement, 'alias.value');
  
  let groupPickerName: string = null;
  if (tableNameAlias) {
    // 3. 有别名，使用别名作为分组名
    groupPickerName = tableNameAlias;
  } else {
    // 4. 无别名，使用表名作为分组名
    // ... (省略逻辑)
  }
  
  // 5. 为字段添加分组信息
  originFields = originFields.map(originField => ({
    ...originField,
    tableInfo: itFromStatement.name,
    groupPickerName,  // ← 关键字段
    originFieldName: originField.label,
  }));
```

## 四、调试日志完整示例

### 4.1 正常补全流程日志

```
[SQL 补全] === 开始补全流程 ===
[SQL 补全] 解析结果：{ success: true, hasError: false, cursorKeyPath: [...], nextMatchingsCount: 5 }
[SQL 补全] 光标信息：{ type: 'tableFieldAfterGroup', token: {...}, groupName: 't' }
[SQL 补全] 光标类型：tableFieldAfterGroup
[SQL 补全] 表名限定字段模式，分组：t
[Reader] getCursorInfo - keyPath: ["0", "from", "sources", "0", "alias", "value"]
[Reader] getCursorInfo - cursorValue: { value: 't' }
[Reader] getCursorInfo - cursorKey: value
[Reader] getCursorInfo - parentStatement: { type: 'identifier', variant: 'columnAfterGroup', ... }
[Reader] getCursorInfo - typePlusVariant: identifier.columnAfterGroup
[Reader] getCursorInfo - 识别为表名限定字段，groupName: t
[Reader] getCursorInfo - 最终结果：{ type: 'tableFieldAfterGroup', ... }
[Reader] getFieldsFromStatement - 开始执行
[Reader] getFieldsFromStatement - cursorRootStatement: select
[Reader] getFieldsFromStatement - SELECT 语句模式
[Reader] getFieldsByFromClause - typePlusVariant: identifier.table
[Reader] getFieldsByFromClause - 处理表标识符，表信息：{ tableName: { value: 'users' } }
[Reader] getFieldsByFromClause - 别名：{ value: 't' }
[Reader] getFieldsByFromClause - 使用别名作为分组名：t
[Reader] getFieldsByFromClause - 原始字段数：10
[Reader] getFieldsByFromClause - 处理后的字段示例：{ label: 'id', groupPickerName: 't', ... }
[SQL 补全 - API] 获取表字段，表信息：{ tableName: 'users', namespace: undefined, cursorValue: undefined }
[SQL 补全 - API] 获取到字段数量：10
[SQL 补全 - API] 字段示例：[
  { name: 'id', columnType: 'bigint', comment: '用户 ID' },
  { name: 'username', columnType: 'varchar(50)', comment: '用户名' },
  { name: 'email', columnType: 'varchar(100)', comment: '邮箱' }
]
[SQL 补全] 过滤前字段数量：10
[SQL 补全] 过滤后字段数量：10
[SQL 补全] 最终补全项总数：15
```

### 4.2 错误场景日志

**场景 1：API 调用失败**
```
[SQL 补全 - API] 获取表字段，表信息：{ tableName: 'users' }
[SQL 补全 - API] 获取表字段失败：Error: Connection timeout
    at ...
[SQL 补全] 获取到字段数量：0
```

**场景 2：AST 解析失败**
```
[SQL 补全] 解析结果：{ success: false, hasError: true, ... }
[SQL 补全] 光标信息：null
[SQL 补全] 无光标信息，返回关键字补全
[SQL 补全] 关键字补全数量：5
```

**场景 3：表别名未识别**
```
[Reader] getFieldsByFromClause - 别名：null
[Reader] getFieldsByFromClause - 多个表名有值，不提供分组名
[SQL 补全] 分组信息：[]
[SQL 补全] 最终补全项总数：10  // 只有字段，没有分组
```

## 五、常见问题排查

### Q1: 为什么输入 `t.` 后不出现字段补全？

**排查步骤**:
1. 检查控制台是否有 `[SQL 补全] 光标类型：tableFieldAfterGroup`
   - 如果没有 → AST 解析失败
   - 如果有 → 继续下一步

2. 检查 `[Reader] getFieldsByFromClause - 别名：` 是否有值
   - 如果是 `null` → 别名识别失败
   - 如果有值 → 继续下一步

3. 检查 `[SQL 补全 - API] 获取到字段数量:`
   - 如果是 0 → 后端 API 问题或表不存在
   - 如果有值 → 继续下一步

4. 检查 `[SQL 补全] 过滤后字段数量:`
   - 如果是 0 → `groupPickerName` 不匹配
   - 如果有值 → 补全应该正常显示

### Q2: 为什么只显示字段，没有表名分组？

**原因**: `groupPickerName` 为 null

**排查**:
```javascript
console.log('[Reader] getFieldsByFromClause - 别名:', itFromStatement.alias);
console.log('[Reader] getFieldsByFromClause - 使用别名作为分组名:', tableNameAlias);
```

**解决方案**:
- 检查是否使用了 `AS` 关键字：`FROM users AS t` (推荐)
- 检查是否直接写别名：`FROM users t` (部分数据库支持)

### Q3: 为什么补全项中有重复字段？

**原因**: 多个表源有相同的 `groupPickerName`

**排查**:
```javascript
console.log('[SQL 补全] 分组信息:', Object.keys(groups));
```

**解决方案**:
- 使用不同的表别名
- 检查是否有重复的 JOIN

## 六、性能优化建议

### 6.1 缓存策略

```typescript
const fieldCache = new Map<string, Column[]>();

onSuggestTableFields: async (tableInfo) => {
  const cacheKey = `${boundInfo.dataSourceId}_${boundInfo.databaseName}_${tableInfo.tableName.value}`;
  
  if (fieldCache.has(cacheKey)) {
    console.log('[SQL 补全 - API] 使用缓存字段，key:', cacheKey);
    return fieldCache.get(cacheKey);
  }
  
  const data = await sqlService.getColumnList(...);
  fieldCache.set(cacheKey, data);
  return data;
}
```

### 6.2 防抖处理

```typescript
import { debounce } from 'lodash';

const debouncedSuggest = debounce(async (tableInfo) => {
  console.log('[SQL 补全 - API] 实际调用 API');
  const data = await sqlService.getColumnList(...);
  return data;
}, 300);
```

### 6.3 懒加载

```typescript
// 只在实际需要时才获取字段
onSuggestFieldGroup: (tableNameOrAlias) => {
  // 先显示分组名
  // 用户点击分组后再加载字段
  return {
    label: tableNameOrAlias,
    kind: monaco.languages.CompletionItemKind.Folder,
  };
}
```

## 七、测试用例

### 测试 1: 基本表别名补全

```sql
SELECT * FROM users AS t WHERE t.
```

**预期日志**:
```
[Reader] getFieldsByFromClause - 别名：{ value: 't' }
[Reader] getFieldsByFromClause - 使用别名作为分组名：t
[SQL 补全] 光标类型：tableFieldAfterGroup
[SQL 补全] 分组：t
[SQL 补全] 过滤后字段数量：N (users 表的字段数)
```

### 测试 2: 多表 JOIN

```sql
SELECT t1.id, t2.name 
FROM users t1 
JOIN orders t2 ON t1.id = t2.user_id
WHERE t2.
```

**预期日志**:
```
[Reader] getFieldsFromStatement - JOIN 语句模式
[Reader] getFieldsByFromClause - 处理表标识符，表信息：users
[Reader] getFieldsByFromClause - 别名：{ value: 't1' }
[Reader] getFieldsByFromClause - 处理表标识符，表信息：orders
[Reader] getFieldsByFromClause - 别名：{ value: 't2' }
[SQL 补全] 过滤后字段数量：N (orders 表的字段数)
```

### 测试 3: 子查询

```sql
SELECT * FROM (SELECT id, username FROM users) AS t WHERE t.
```

**预期日志**:
```
[Reader] getFieldsByFromClause - 处理子查询
[SQL 补全 - API] 获取表字段，表信息：users
[Reader] getFieldsByFromClause - 子查询有别名：t
[SQL 补全] 过滤后字段数量：2 (id, username)
```

## 八、总结

SQL 智能补全的核心是：
1. **AST 解析**: 准确识别 SQL 语法结构
2. **别名识别**: 通过 `alias.value → groupPickerName` 传递别名信息
3. **字段收集**: 遍历所有表源，收集字段并设置分组
4. **精准过滤**: 根据 `groupName` 过滤对应表的字段

通过日志可以清晰地看到数据流转过程，快速定位问题。
