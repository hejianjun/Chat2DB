# SQL 智能补全测试用例

## 测试环境准备

1. 启动前端项目
```bash
cd chat2db-client
yarn run start
```

2. 启动后端项目
```bash
cd chat2db-server
mvn spring-boot:run -pl chat2db-server-start
```

## 测试步骤

### 测试 1：基础表别名补全（核心功能）

**操作步骤：**
1. 打开 SQL 编辑器
2. 选择一个数据源和数据库
3. 输入以下 SQL：
```sql
select * from AKMG_APP t where t.
```

**预期结果：**
- 在 `t.` 后自动弹出补全列表
- 显示 AKMG_APP 表的所有字段
- 每个字段显示字段名和类型

**验证点：**
- [ ] 补全列表正常弹出
- [ ] 字段列表完整
- [ ] 字段类型显示正确
- [ ] 字段注释显示正确（如果有）

### 测试 2：直接表名补全

**操作步骤：**
1. 在 SQL 编辑器中输入：
```sql
select * from AKMG_APP.
```

**预期结果：**
- 在 `AKMG_APP.` 后自动弹出补全列表
- 显示该表的所有字段

### 测试 3：多表 JOIN 补全

**操作步骤：**
1. 输入以下 SQL：
```sql
select t1.id, t2.name 
from table1 t1 
join table2 t2 on t1.id = t2.
```

**预期结果：**
- 在 `t2.` 后弹出 table2 的字段列表
- 在 `t1.` 后弹出 table1 的字段列表

### 测试 4：表名补全

**操作步骤：**
1. 输入以下 SQL：
```sql
select * from 
```

**预期结果：**
- 在 `from ` 后弹出当前数据库的所有表列表

### 测试 5：关键字补全

**操作步骤：**
1. 输入以下 SQL 片段：
```sql
SEL
```

**预期结果：**
- 弹出 SQL 关键字补全列表，包含 SELECT

### 测试 6：Hover 提示

**操作步骤：**
1. 输入完整的 SQL 并执行
2. 将鼠标悬停在字段名上

**预期结果：**
- 显示字段的详细信息（类型、注释等）

### 测试 7：数据库切换

**操作步骤：**
1. 在 SQL 编辑器中选择不同的数据库
2. 重复测试 1

**预期结果：**
- 显示新数据库中的表字段
- 补全列表自动更新

### 测试 8：性能测试

**操作步骤：**
1. 快速输入 SQL，触发多次补全
2. 观察响应速度

**预期结果：**
- 补全响应流畅
- 无明显卡顿
- 无重复 API 调用

## 测试数据准备

为了测试表别名补全功能，需要确保数据库中有表。可以使用以下 SQL 创建测试表：

```sql
CREATE TABLE test_users (
    id BIGINT PRIMARY KEY COMMENT '用户 ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    email VARCHAR(100) COMMENT '邮箱',
    created_at DATETIME COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间'
) COMMENT '测试用户表';

CREATE TABLE test_orders (
    id BIGINT PRIMARY KEY COMMENT '订单 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    amount DECIMAL(10,2) COMMENT '订单金额',
    status INT COMMENT '订单状态',
    created_at DATETIME COMMENT '创建时间'
) COMMENT '测试订单表';
```

## 测试 SQL 示例

```sql
-- 测试 1：表别名补全
select * from test_users t where t.

-- 测试 2：直接表名补全
select * from test_users.

-- 测试 3：多表 JOIN
select u.username, o.amount
from test_users u
join test_orders o on u.id = o.

-- 测试 4：WHERE 条件
select * from test_users where .

-- 测试 5：ORDER BY
select * from test_users order by .

-- 测试 6：GROUP BY
select username, count(*) from test_users group by .
```

## 问题排查

### 问题 1：补全列表不弹出

**可能原因：**
- boundInfo 未正确传递
- 后端 API 调用失败
- SQL 解析失败

**排查步骤：**
1. 打开浏览器控制台，查看是否有错误日志
2. 检查 Network 面板，查看 API 调用是否成功
3. 检查 boundInfo 是否正确传递到 MonacoEditor

### 问题 2：补全列表为空

**可能原因：**
- 数据库中没有表
- API 返回数据格式不正确
- 表名解析失败

**排查步骤：**
1. 检查数据库中是否有表
2. 检查 `/api/rdb/table/column_list` API 返回数据
3. 检查 tableInfo 参数是否正确

### 问题 3：只补全表名，不补全字段

**可能原因：**
- syntax-parser 未正确初始化
- onSuggestTableFields 回调未正确实现

**排查步骤：**
1. 检查 MonacoEditor 是否收到 boundInfo
2. 检查 initSqlAutocomplete 是否被调用
3. 检查 monacoSqlAutocomplete 的 opts 配置

## 成功标准

✅ 所有测试用例通过
✅ 表别名补全功能正常工作
✅ 无明显性能问题
✅ 无控制台错误
✅ Hover 提示正常显示

## 后续改进

1. 添加缓存机制，减少 API 调用
2. 优化补全项排序
3. 支持更多数据库类型
4. 添加单元测试
