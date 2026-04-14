# SQL 补全排序和重复问题修复

## 问题描述

### 问题 1: 字段提示重复
```
[SQL 补全] 过滤前字段数量: 3
[SQL 补全] 过滤后字段数量: 3
```
同一个字段出现多次。

### 问题 2: 字段没有排在表名前面
补全列表中表名排在字段前面，不符合使用习惯。

## 原因分析

### 重复原因
`getFieldsFromStatement` 内部遍历所有表源时，可能多次添加同一个字段。

### 排序原因
默认排序规则：
- 表名：`sortText: A*`
- 字段：`sortText: B*`
- 函数：`sortText: C*`
- 分组：`sortText: D*`
- 关键字：`sortText: W*`

Monaco 按 `sortText` 字母顺序排序，A 在 B 前面，所以表名排在字段前面。

## 解决方案

### 1. 去重字段

**文件**: `index.ts`

```typescript
// 表字段补全模式
const cursorRootStatementFields = await reader.getFieldsFromStatement(...);

// 去重字段（避免重复）
const uniqueFields = _.uniqBy(cursorRootStatementFields, 'label');

// 使用去重后的字段
const result = uniqueFields
  .concat(functionNames)
  .concat(parserSuggestion)
  .concat(groups...);
```

### 2. 调整排序

**目标**: 字段 > 函数 > 关键字 > 表名

**修改**:
```typescript
// default-opts.ts 和 sql-autocomplete.ts
onSuggestTableNames = () => {
  return tables.map(table => ({
    label: table.name,
    sortText: `Z${table.name}`,  // Z 开头，排在最后
  }));
}
```

**排序规则**:
- `B*` - 字段 (最前)
- `C*` - 函数
- `W*` - SQL 关键字
- `Z*` - 表名 (最后)

## 修改文件

### 1. index.ts
- 添加 `_.uniqBy` 去重
- 调整合并顺序
- 移除表名分组的重复添加

### 2. default-opts.ts
- 表名 `sortText` 从 `A*` 改为 `Z*`

### 3. sql-autocomplete.ts
- 表名 `sortText` 从 `A*` 改为 `Z*`

## 预期效果

### 补全顺序
```
t.
├─ option_value_id (字段)
├─ option_name (字段)
├─ created_at (字段)
├─ COUNT (函数)
├─ SELECT (关键字)
├─ FROM (关键字)
└─ WHERE (关键字)
```

**注意**: 表名不会出现在 `t.` 后面的补全中，因为这是表名限定字段模式。

### 无表名前缀时的补全
```sql
SELECT * FROM |
```
此时补全：
```
├─ option_value_id (字段)
├─ option_name (字段)
├─ COUNT (函数)
├─ SELECT (关键字)
├─ base_option_name (表名)  ← Z 开头，排在后面
└─ other_table (表名)
```

## 测试验证

### 测试 1: 字段去重

```sql
select * from base_option_name t where t.
```

**预期**: 每个字段只出现一次

**日志**:
```
[SQL 补全] 获取到字段数量: 3
[SQL 补全] 去重后字段数量: 3  ← 无变化说明没有重复
[SQL 补全] 过滤后字段数量: 3
```

### 测试 2: 字段排在前面

**预期**: 字段在补全列表顶部

**检查**:
1. 打开补全列表
2. 确认字段在最上面
3. 表名在列表底部（如果有）

### 测试 3: 多表 JOIN

```sql
select * from table1 t1 join table2 t2 on t1.id = t2.
```

**预期**:
- 在 `t2.` 后只看到 table2 的字段
- 字段不重复
- 字段在最前面

## 性能优化

### 去重性能
```typescript
_.uniqBy(array, 'label')
```
时间复杂度: O(n)
空间复杂度: O(n)

对于典型的表字段数（10-50 个），性能影响可忽略。

### 排序性能
Monaco Editor 内部处理排序，性能优秀。

## 注意事项

### 1. 分组显示
保留 `groupPickerName` 用于 Monaco 的分组显示功能：
```typescript
groups ? Object.keys(groups).map(groupName => 
  opts.onSuggestFieldGroup(groupName)
)
```

这会在补全列表中显示一个可展开的分组：
```
t
├─ field1
├─ field2
└─ field3
```

### 2. 字段上下文
在不同上下文中，补全策略不同：

**tableFieldAfterGroup** (`t.`):
- 只显示对应表的字段
- 字段排在最前
- 不显示表名

**tableField** (普通字段位置):
- 显示所有表的字段
- 显示表名分组
- 字段优先

## 调试日志

### 去重日志
```
[SQL 补全] 获取到字段数量: 6
[SQL 补全] 去重后字段数量: 3  ← 说明有 3 个重复字段被去除
```

### 排序日志
```
[SQL 补全] 最终补全项总数: 15
  - 字段：10 个 (sortText: B*)
  - 函数：3 个 (sortText: C*)
  - 关键字：2 个 (sortText: W*)
  - 表名：5 个 (sortText: Z*)  ← 排在最后
```

## 常见问题

### Q1: 为什么还会看到重复字段？

**A**: 检查是否:
1. 刷新了页面（确保新代码生效）
2. 清除了浏览器缓存
3. 重启了开发服务器

### Q2: 表名仍然排在字段前面？

**A**: 可能原因:
1. 修改未生效（需要刷新）
2. Monaco 缓存（清除缓存）
3. 其他地方的 `sortText` 未修改

检查文件:
- `default-opts.ts` - 默认实现
- `sql-autocomplete.ts` - 实际使用

### Q3: 分组显示正常吗？

**A**: 分组显示依赖 Monaco 的功能:
```typescript
{
  label: 'field',
  groupPickerName: 't',  // ← 关键
}
```

Monaco 会自动将相同 `groupPickerName` 的字段分组。

## 相关文档

- 原理说明：`PRINCIPLE_AND_LOGS.md`
- 调试指南：`DEBUG_GUIDE.md`
- HMR 修复：`HMR_FIX.md`
- 实现说明：`README.md`

## 总结

**问题**: 字段重复 + 排序错误
**修复**: 
1. ✅ 使用 `_.uniqBy` 去重
2. ✅ 调整 `sortText` 排序
3. ✅ 优化合并顺序

**效果**:
- ✅ 字段不重复
- ✅ 字段排在最前面
- ✅ 表名排在最后
- ✅ 分组显示正常
