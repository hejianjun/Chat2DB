# SQL 智能补全测试用例

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

-- 测试 2：直接字段名补全
select * from test_users.

-- 测试 3：多表 JOIN
select u.username, o.amount
from test_users u
join test_orders o on u.id = o.

-- 测试 4：WHERE 条件
select * from test_users where 

-- 测试 5：ORDER BY
select * from test_users order by 

-- 测试 6：GROUP BY
select username, count(*) from test_users group by 
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
