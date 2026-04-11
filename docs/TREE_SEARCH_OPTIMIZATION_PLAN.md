# Chat2DB 工作台树搜索优化方案

> **文档版本**: v1.0  
> **创建日期**: 2026-04-11  
> **作者**: AI Agent  
> **状态**: 待评审

---

## 目录

- [一、背景与问题](#一背景与问题)
- [二、方案概述](#二方案概述)
- [三、当前架构分析](#三当前架构分析)
- [四、整体架构图](#四整体架构图)
- [五、时序图](#五时序图)
  - [5.1 初始化加载时序图](#51-初始化加载时序图)
  - [5.2 搜索时序图（后端模式）](#52-搜索时序图后端模式)
  - [5.3 数据源切换时序图](#53-数据源切换时序图)
- [六、流程图](#六流程图)
  - [6.1 总体实施流程](#61-总体实施流程)
  - [6.2 索引更新流程](#62-索引更新流程)
  - [6.3 搜索执行流程](#63-搜索执行流程)
  - [6.4 前端树重构流程](#64-前端树重构流程)
- [七、详细设计方案](#七详细设计方案)
  - [7.1 数据模型改造](#71-数据模型改造)
  - [7.2 Service 层改造](#72-service-层改造)
  - [7.3 API 层改造](#73-api-层改造)
  - [7.4 前端改造](#74-前端改造)
- [八、数据存储架构](#八数据存储架构)
- [九、实施计划](#九实施计划)
- [十、风险与应对](#十风险与应对)
- [十一、成功标准](#十一成功标准)
- [十二、待确认问题](#十二待确认问题)

---

## 一、背景与问题

### 1.1 当前问题

**问题描述**：工作台树搜索存在以下问题：

1. **数据源切换后搜索不刷新**  
   - 前端树过滤 useEffect 只监听 `searchValue`，不监听 `treeData`
   - 切换数据源后，`searchTreeData` 仍是旧数据源的过滤结果
   - **Bug 触发位置**: `Tree/index.tsx:167-175`

2. **前端全量过滤性能差**  
   - 表数量 > 500 时，前端 DFS 过滤响应慢
   - 需要先加载所有表到内存，加载时间长

3. **视图/函数/存储过程不支持搜索**  
   - View/Function/Procedure 没有使用 Lucene 索引
   - 每次从数据库实时查询，不支持 `searchKey` 参数
   - 返回全量数据，性能差

### 1.2 优化目标

- ✅ 修复数据源切换后搜索不刷新的 Bug
- ✅ 支持后端搜索（大数据量场景）
- ✅ 支持视图、函数、存储过程的搜索
- ✅ 支持树自动展开路径
- ✅ 性能优化：搜索响应时间 < 500ms

---

## 二、方案概述

### 2.1 核心思路

**方案 A**：扩展 Lucene 索引支持

1. 让 `Function`/`Procedure`/`Trigger` 实现 `IndexModel` 接口
2. 在对应 Service 中引入 `LuceneIndexManager`
3. 复用现有的缓存和搜索基础设施
4. 新增统一的树搜索接口

### 2.2 搜索模式决策

```
数据量判断：
├─ treeData.length < 200  → 前端 DFS 过滤（快速）
└─ treeData.length >= 200 → 后端 Lucene 搜索（大数据量）
```

---

## 三、当前架构分析

### 3.1 数据存储层次结构

```
物理存储位置：
└─ ~/.chat2db/index/{dataSourceId}_dev/  (Lucene 索引文件)

逻辑存储结构：
┌─ Table           ✅ 使用 Lucene 索引  ✅ 支持搜索
├─ TableColumn     ✅ 使用 Lucene 索引  ✅ 支持搜索  
├─ ForeignKey      ✅ 使用 Lucene 索引  ✅ 支持搜索
├─ View            ❌ 直接查询数据库    ❌ 不支持搜索
├─ Function        ❌ 直接查询数据库    ❌ 不支持搜索
├─ Procedure       ❌ 直接查询数据库    ❌ 不支持搜索
└─ Trigger         ❌ 直接查询数据库    ❌ 不支持搜索
```

### 3.2 详细对比

| 对象类型 | 存储位置 | 索引支持 | 搜索支持 | Service 实现 |
|---------|---------|---------|---------|-------------|
| **Table** | Lucene 索引文件 | ✅ 有 | ✅ 支持 | TableServiceImpl + LuceneIndexManager |
| **TableColumn** | Lucene 索引文件 | ✅ 有 | ✅ 支持 | TableServiceImpl + LuceneIndexManager |
| **ForeignKey** | Lucene 索引文件 | ✅ 有 | ✅ 支持 | TableServiceImpl + LuceneIndexManager |
| **View** | 数据库元数据 | ❌ 无 | ❌ 不支持 | ViewServiceImpl (直接查询) |
| **Function** | 数据库元数据 | ❌ 无 | ❌ 不支持 | FunctionServiceImpl (直接查询) |
| **Procedure** | 数据库元数据 | ❌ 无 | ❌ 不支持 | ProcedureServiceImpl (直接查询) |
| **Trigger** | 数据库元数据 | ❌ 无 | ❌ 不支持 | 无 Service |

### 3.3 代码证据

**表使用 Lucene 索引** (`TableServiceImpl.java:428-440`):
```java
private void loadAndCacheMetadata(LuceneIndexManager<Table> mgr, TablePageQueryParam param, Long version) {
    mgr.getLock().writeLock().lock();
    try {
        Connection conn = Chat2DBContext.getConnection();
        MetaData meta = Chat2DBContext.getMetaData();
        List<Table> tables = meta.tables(conn, param.getDatabaseName(), param.getSchemaName(), null);
        mgr.updateDocuments(tables, version);  // ✅ 存入 Lucene 索引
    } catch (Exception e) {
        log.error("loadAndCacheMetadata error,version:{}", version, e);
    } finally {
        mgr.getLock().writeLock().unlock();
    }
}
```

**视图直接查询数据库** (`ViewServiceImpl.java:14-18`):
```java
@Override
public ListResult<Table> views(String databaseName, String schemaName) {
    return ListResult.of(
        Chat2DBContext.getMetaData().views(
            Chat2DBContext.getConnection(),
            databaseName, 
            schemaName
        )
    );  // ❌ 直接查询，无缓存
}
```

---

## 四、整体架构图

```
┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│   前端层          │         │   后端层          │         │   数据库层        │
│  (React + TS)    │         │  (Spring Boot)   │         │  (Metadata)      │
└────────┬─────────┘         └────────┬─────────┘         └────────┬─────────┘
         │                            │                            │
         │ 1. 输入搜索词              │                            │
         │───────────────────────────>│                            │
         │                            │                            │
         │                            │ 2. 判断搜索模式            │
         │                            │    (数据量 < 200?)         │
         │                            │                            │
         │                            │         ├─ YES ─┐         │
         │                            │         │       │         │
         │                            │ 3a. 前端 DFS 过滤         │
         │                            │     (searchTree())        │
         │                            │                            │
         │                            │         ├─ NO ──┐         │
         │                            │         │       │         │
         │                            │ 3b. 后端搜索 API          │
         │<───────────────────────────│    /api/rdb/tree/search   │
         │ 4. 返回搜索结果            │                            │
         │    (扁平化 + parentPath)   │ 4. LuceneIndexManager     │
         │                            │    ├─ Table 索引          │
         │                            │    ├─ View 索引  (新增)   │
         │                            │    ├─ Function 索引 (新增)│
         │                            │    ├─ Procedure 索引 (新增)│
         │                            │    └─ Trigger 索引 (新增) │
         │                            │                            │
         │                            │ 5. meta.views()           │
         │                            │    meta.functions()       │
         │                            │    meta.procedures()      │
         │                            │    meta.triggers()        │
         │                            │──────────────────────────>│
         │                            │                            │
         │                            │ 6. 返回元数据             │
         │                            │<──────────────────────────│
         │                            │                            │
         │                            │ 7. updateDocuments()      │
         │                            │    (写入 Lucene 索引)      │
         │                            │                            │
         │ 5. 构建树结构              │ 8. 返回搜索结果           │
         │    (buildTreeFromFlatData) │<──────────────────────────│
         │    自动展开路径            │                            │
         │                            │                            │
```

---

## 五、时序图

### 5.1 初始化加载时序图

```
┌─────────────┐  ┌──────────────┐  ┌─────────────────┐  ┌──────────────┐  ┌─────────────┐
│    User     │  │  Tree Component│  │ LuceneManagerFactory│  │ LuceneManager│  │  Database   │
└──────┬──────┘  └──────┬───────┘  └────────┬────────┘  └──────┬───────┘  └──────┬──────┘
       │                │                   │                 │                │
       │ 1. 连接数据源   │                   │                 │                │
       │───────────────>│                   │                 │                │
       │                │                   │                 │                │
       │                │ 2. getManager(id) │                 │                │
       │                │──────────────────>│                 │                │
       │                │                   │                 │                │
       │                │                   │ 3. 创建/获取    │                │
       │                │                   │ LuceneIndexManager│               │
       │                │                   │────────────────>│                │
       │                │                   │                 │                │
       │                │ 4. 返回 manager   │                 │                │
       │                │<──────────────────│                 │                │
       │                │                   │                 │                │
       │                │ 5. getMaxVersion  │                 │                │
       │                │────────────────────────────────────>│                │
       │                │                   │                 │                │
       │                │                   │ 6. 查询 Lucene  │                │
       │                │                   │ version        │                │
       │                │                   │────────────────>│                │
       │                │                   │                 │                │
       │                │ 7. 返回 version=null│                │                │
       │                │<────────────────────────────────────│                │
       │                │ (首次连接，无缓存) │                 │                │
       │                │                   │                 │                │
       │                │ 8. loadAndCacheMetadata()          │                │
       │                │────────────────────────────────────>│                │
       │                │                   │                 │                │
       │                │                   │ 9. meta.tables()│                │
       │                │                   │────────────────>│───────────────>│
       │                │                   │                 │                │
       │                │                   │ 10. 返回表元数据│                │
       │                │                   │<────────────────│<───────────────│
       │                │                   │                 │                │
       │                │                   │ 11. meta.views()│                │
       │                │                   │────────────────>│───────────────>│
       │                │                   │                 │                │
       │                │                   │ 12. 返回视图元数据│              │
       │                │                   │<────────────────│<───────────────│
       │                │                   │                 │                │
       │                │                   │ 13. meta.functions()│            │
       │                │                   │────────────────>│───────────────>│
       │                │                   │                 │                │
       │                │                   │ 14. 返回函数元数据│              │
       │                │                   │<────────────────│<───────────────│
       │                │                   │                 │                │
       │                │                   │ 15. meta.procedures()│           │
       │                │                   │────────────────>│───────────────>│
       │                │                   │                 │                │
       │                │                   │ 16. 返回存储过程元数据│          │
       │                │                   │<────────────────│<───────────────│
       │                │                   │                 │                │
       │                │                   │ 17. updateDocuments()│           │
       │                │                   │ (Table/View/Function/Procedure) │
       │                │                   │────────────────>│                │
       │                │                   │                 │                │
       │                │                   │ 18. 写入 Lucene 索引│            │
       │                │                   │────────────────>│                │
       │                │                   │                 │                │
       │                │ 19. 缓存完成      │                 │                │
       │                │<────────────────────────────────────│                │
       │                │                   │                 │                │
       │ 20. 树加载完成 │                   │                 │                │
       │<──────────────│                   │                 │                │
```

---

### 5.2 搜索时序图（后端模式）

```
┌─────────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐  ┌─────────────┐
│    User     │  │  Tree Component│  │  Search API   │  │LuceneManager │  │   Database  │
└──────┬──────┘  └──────┬───────┘  └───────┬───────┘  └──────┬───────┘  └──────┬──────┘
       │                │                  │                │               │
       │ 1. 输入搜索词   │                  │                │               │
       │───────────────>│                  │                │               │
       │                │                  │                │               │
       │                │ 2. useEffect 触发│                │               │
       │                │ (searchValue + treeData)│        │               │
       │                │                  │                │               │
       │                │ 3. 判断：数据量 >= 200│         │               │
       │                │─────────────────>│                │               │
       │                │                  │                │               │
       │                │ 4. POST /api/rdb/tree/search    │               │
       │                │    {searchKey, treeNodeType}    │                │
       │                │─────────────────────────────────>│               │
       │                │                  │                │               │
       │                │                  │ 5. 根据类型分发│               │
       │                │                  │───────────────>│               │
       │                │                  │                │               │
       │                │                  │ 6. search()   │               │
       │                │                  │ (Lucene 全文检索)│              │
       │                │                  │───────────────>│               │
       │                │                  │                │               │
       │                │                  │ 7. 构建 BooleanQuery│         │
       │                │                  │ - type:VIEW   │               │
       │                │                  │ - databaseName│               │
       │                │                  │ - schemaName  │               │
       │                │                  │ - searchKey   │               │
       │                │                  │                │               │
       │                │                  │ 8. searcher.search()│        │
       │                │                  │───────────────>│               │
       │                │                  │                │               │
       │                │                  │ 9. 返回 ScoreDocs│            │
       │                │                  │<───────────────│               │
       │                │                  │                │               │
       │                │                  │ 10. 映射为 TreeNode│          │
       │                │                  │     - 添加 parentPath│        │
       │                │                  │     - 添加 extraParams│       │
       │                │                  │                │               │
       │                │ 11. 返回 TreeNodeVO List│       │               │
       │                │<─────────────────────────────────│               │
       │                │                  │                │               │
       │                │ 12. buildTreeFromFlatData()     │               │
       │                │     - 创建路径节点               │               │
       │                │     - 构建父子关系               │               │
       │                │     - 自动展开匹配路径           │               │
       │                │                  │                │               │
       │                │ 13. 渲染搜索结果│                │               │
       │                │ (高亮 + 可展开) │                │               │
       │                │                  │                │               │
```

---

### 5.3 数据源切换时序图

```
┌─────────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐
│    User     │  │  TableList   │  │  Tree Component│  │   Backend    │
└──────┬──────┘  └──────┬───────┘  └───────┬───────┘  └──────┬───────┘
       │                │                  │                 │
       │ 1. 切换数据源   │                  │                 │
       │───────────────>│                  │                 │
       │                │                  │                 │
       │                │ 2. currentConnectionDetails 变化  │
       │                │─────────────────>│                 │
       │                │                  │                 │
       │                │ 3. getTreeData() │                 │
       │                │─────────────────>│                 │
       │                │                  │                 │
       │                │ 4. getChildren API│                │
       │                │────────────────────────────────────>│
       │                │                  │                 │
       │                │ 5. 返回新数据源树│                 │
       │                │<────────────────────────────────────│
       │                │                  │                 │
       │                │ 6. setTreeData(newData)│           │
       │                │─────────────────>│                 │
       │                │                  │                 │
       │                │                  │ 7. useEffect 触发│
       │                │                  │    (treeData 变化)│
       │                │                  │                 │
       │                │                  │ 8. searchValue 仍有效│
       │                │                  │                 │
       │                │                  │ 9. 重新执行搜索逻辑│
       │                │                  │                 │
       │                │                  │ ├─ 前端搜索      │
       │                │                  │ │ 或            │
       │                │                  │ ├─ 后端搜索 API  │
       │                │                  │                 │
       │                │                  │ 10. 显示新数据源│
       │                │                  │     搜索结果    │
       │                │                  │                 │
       │                │                  │ ✅ BUG 修复完成  │
       │                │                  │                 │
```

---

## 六、流程图

### 6.1 总体实施流程

```
┌──────────────────────┐
│  阶段 1: 数据模型改造   │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  1.1 Function 实现 IndexModel 接口      │
│      - 添加 version 字段                │
│      - 添加 name/comment/aiComment 字段 │
│      - 实现 getClassType() 方法         │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  1.2 Procedure 实现 IndexModel 接口     │
│      - 添加 version 字段                │
│      - 添加 name/comment/aiComment 字段 │
│      - 实现 getClassType() 方法         │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  1.3 Trigger 实现 IndexModel 接口       │
│      - 添加 version 字段                │
│      - 添加 name/comment/aiComment 字段 │
│      - 实现 getClassType() 方法         │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  1.4 View 已实现 IndexModel (Table)     │
│      - 无需改造（View 返回 Table 类型）  │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌──────────────────────┐
│  阶段 2: Service 改造  │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  2.1 FunctionServiceImpl 改造           │
│      - 注入 LuceneIndexManagerFactory   │
│      - 新增 loadAndCacheMetadata()      │
│      - 新增 searchTreeNodes() 方法      │
│      - functions() 方法使用索引         │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  2.2 ProcedureServiceImpl 改造          │
│      - 注入 LuceneIndexManagerFactory   │
│      - 新增 loadAndCacheMetadata()      │
│      - 新增 searchTreeNodes() 方法      │
│      - procedures() 方法使用索引        │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  2.3 ViewServiceImpl 改造               │
│      - 注入 LuceneIndexManagerFactory   │
│      - 新增 loadAndCacheMetadata()      │
│      - 新增 searchTreeNodes() 方法      │
│      - views() 方法使用索引             │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  2.4 Trigger 新增 TriggerServiceImpl    │
│      - 创建 TriggerService 接口         │
│      - 创建 TriggerServiceImpl 实现     │
│      - 支持 Lucene 索引                 │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌──────────────────────┐
│  阶段 3: API 层改造    │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  3.1 新增 TreeSearchRequest             │
│      - dataSourceId                     │
│      - databaseName/schemaName          │
│      - searchKey                        │
│      - treeNodeType (TABLE/VIEW/...)    │
│      - pageSize                         │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  3.2 新增 TreeNodeVO                    │
│      - uuid/key/name                    │
│      - treeNodeType                     │
│      - parentPath                       │
│      - extraParams                      │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  3.3 新增 TreeSearchParam               │
│      - 业务参数对象                     │
│      - 用于 Service 层调用              │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  3.4 新增/改造 Controller               │
│      ├─ TableController.searchTree()   │
│      ├─ ViewController.searchTree()    │
│      ├─ FunctionController.searchTree()│
│      ├─ ProcedureController.searchTree()│
│      └─ 或统一入口 TreeController      │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  3.5 新增 Converter                     │
│      - toTreeSearchParam()              │
│      - treeNode2vo()                    │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌──────────────────────┐
│  阶段 4: 前端改造      │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  4.1 新增 API 调用                       │
│      - searchTree(): ITreeSearchParams  │
│      - 返回 ITreeNodeResponse[]         │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  4.2 Tree 组件改造                      │
│      - 新增 backendSearchData 状态      │
│      - 新增 useEffect 调用后端搜索      │
│      - 依赖添加 treeData                │
│      - 修复切换数据源 BUG              │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  4.3 树重构算法                         │
│      - buildTreeFromFlatData()          │
│      - 根据 parentPath 创建路径节点     │
│      - 构建父子关系                     │
│      - 自动展开匹配路径                 │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  4.4 搜索模式决策                       │
│      - treeData.length < 200 → 前端    │
│      - treeData.length >= 200 → 后端   │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌──────────────────────┐
│  阶段 5: 测试优化      │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  5.1 单元测试                           │
│      - Function/Procedure/Trigger 索引 │
│      - 搜索功能测试                     │
│      - 缓存刷新测试                     │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  5.2 集成测试                           │
│      - 前端搜索 UI 测试                 │
│      - 数据源切换测试                   │
│      - 性能测试                         │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  5.3 优化                               │
│      - 搜索防抖                         │
│      - 结果缓存                         │
│      - 高亮显示                         │
│      - 分页加载                         │
└─────────────────────────────────────────┘
```

---

### 6.2 索引更新流程

```
┌──────────────────────┐
│  1. 连接数据库       │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  2. 获取 LuceneIndexManager             │
│     managerFactory.getManager(dataSourceId)│
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  3. 检查缓存版本                        │
│     version = mgr.getMaxVersion(param)  │
└──────────────────┬──────────────────────┘
                   │
                   ▼
         ┌─────────────────────┐
         │ version == null ?   │
         └────────┬────────────┘
                  │
        ┌─────────┴─────────┐
        │ YES               │ NO
        ▼                   ▼
┌──────────────────┐ ┌──────────────────┐
│ 4. 首次加载       │ │ 4. 检查 refresh 标志│
│ 或缓存失效       │ │                  │
└────────┬─────────┘ └────────┬─────────┘
         │                    │
         └────────┬───────────┘
                  │
        ┌─────────┴─────────┐
        │ refresh == true ? │
        └────────┬──────────┘
                 │
        ┌────────┴────────┐
        │ YES             │ NO
        ▼                 ▼
┌─────────────────┐ ┌──────────────────┐
│ 5. 加载元数据   │ │ 5. 使用现有索引  │
│ 写锁保护       │ │    直接返回      │
└────────┬────────┘ └────────┬─────────┘
         │                   │
         ▼                   │
┌─────────────────────────┐ │
│ 6. 元数据获取           │ │
│ ├─ meta.tables()       │ │
│ ├─ meta.views()        │ │
│ ├─ meta.functions()    │ │
│ ├─ meta.procedures()   │ │
│ └─ meta.triggers()     │ │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ 7. 批量更新索引         │
│ mgr.updateDocuments()   │
│ ├─ Table 列表          │
│ ├─ View 列表           │
│ ├─ Function 列表       │
│ ├─ Procedure 列表      │
│ └─ Trigger 列表        │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ 8. 设置版本号和 aiComment│
│ source.setVersion(v)    │
│ source.setAiComment(...)│
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ 9. 构建 Document        │
│ ├─ type 字段           │
│ ├─ name 字段 (TextField)│
│ ├─ comment (TextField) │
│ ├─ aiComment (TextField)│
│ ├─ databaseName        │
│ ├─ schemaName          │
│ ├─ tableName           │
│ └─ source (StoredField)│
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ 10. writer.updateDocuments│
│     (BooleanQuery, docs) │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ 11. reload()            │
│     刷新 IndexReader    │
│     刷新 IndexSearcher  │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ 12. 释放写锁            │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ 13. 索引更新完成        │
└─────────────────────────┘
```

---

### 6.3 搜索执行流程

```
┌──────────────────────┐
│  1. 接收搜索请求     │
│     TreeSearchParam  │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  2. 获取 LuceneIndexManager             │
│     managerFactory.getManager(id)       │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  3. 检查/刷新缓存                       │
│     needRefreshCache ? loadMetadata()   │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  4. 构建搜索查询                        │
│     buildSearchQuery(param, searchKey)  │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  5. 构建 BooleanQuery                   │
│     ├─ type: Function/Procedure/...    │
│     ├─ databaseName (FILTER)           │
│     ├─ schemaName (FILTER)             │
│     └─ searchKey (MUST)                │
└──────────────────┬──────────────────────┘
                   │
         ┌─────────┴─────────┐
         │ searchKey 是否为空│
         └────────┬──────────┘
                  │
        ┌─────────┴─────────┐
        │ YES               │ NO
        ▼                   ▼
┌──────────────────┐ ┌──────────────────────┐
│ 6a. MatchAllDocs │ │ 6b. MultiFieldQuery  │
│     返回全部      │ │     ├─ name 字段     │
│                  │ │     ├─ comment 字段  │
│                  │ │     └─ aiComment 字段│
└────────┬─────────┘ └──────────────────────┘
         │                    │
         └────────┬───────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│  7. 执行搜索                            │
│     searcher.searchAfter(query, 1000)   │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  8. 返回 ScoreDocs                      │
│     包含 docId 和评分                   │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  9. 读取文档                            │
│     storedFields.document(docId)        │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  10. 解析 source 字段                   │
│      JSONObject.parseObject(source)     │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  11. 构建 TreeNode                      │
│      ├─ 设置基本信息                    │
│      ├─ 构建 parentPath                 │
│      │   ├─ databaseName               │
│      │   └─ schemaName (如果有)        │
│      └─ 设置 extraParams                │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  12. 返回 TreeNode List                 │
└─────────────────────────────────────────┘
```

---

### 6.4 前端树重构流程

```
┌──────────────────────┐
│  1. 接收扁平化结果   │
│     ITreeNodeResponse[]│
│     (来自后端 API)    │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  2. 初始化数据结构                      │
│     map = new Map()                     │
│     pathMap = new Map()                 │
│     roots = []                          │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  3. 创建所有节点                        │
│     forEach item:                       │
│       map.set(uuid, {                   │
│         ...item,                        │
│         children: []                    │
│       })                                │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  4. 收集路径节点                        │
│     forEach node:                       │
│       if node.parentPath:               │
│         currentPath = ""                │
│         forEach pathItem:               │
│           currentPath += pathItem       │
│           if !pathMap.has(currentPath): │
│             pathNode = {                │
│               uuid: path-xxx,           │
│               name: pathItem,           │
│               treeNodeType: 推断类型，   │
│               children: null,           │
│               extraParams: ...          │
│             }                           │
│             pathMap.set(path, node)     │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  5. 构建路径树                          │
│     forEach pathNode in pathMap:        │
│       parentPath = 获取上层路径         │
│       if parentPath exists:             │
│         parent = pathMap.get(parentPath)│
│         parent.children.push(pathNode)  │
│       else:                             │
│         roots.push(pathNode)            │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  6. 添加搜索结果到路径                  │
│     forEach node:                       │
│       if node.parentPath:               │
│         parentKey = parentPath.join('/')│
│         parent = pathMap.get(parentKey) │
│         parent.children.push(node)      │
│       else:                             │
│         roots.push(node)                │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  7. 标记展开状态                        │
│     forEach pathNode:                   │
│       pathNode.expanded = true          │
│       (自动展开匹配路径)                │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  8. 返回树根节点集合                    │
│     return roots                        │
└─────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  9. 转平级用于虚拟滚动                  │
│     smoothTree(roots, result)           │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  10. 渲染树节点                         │
│      virtualScroll.render(nodes)        │
└─────────────────────────────────────────┘
```

---

## 七、详细设计方案

### 7.1 数据模型改造

#### Function.java（改造后）

```java
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Function implements IndexModel {  // ✅ 实现 IndexModel
    
    @JsonAlias({"FUNCTION_NAME"})
    private String name;  // ✅ 实现 IndexModel
    
    @JsonAlias({"REMARKS"})
    private String comment;  // ✅ 实现 IndexModel
    
    private String aiComment;  // ✅ 新增
    
    private Long version;  // ✅ 新增
    
    @JsonAlias({"FUNCTION_CAT"})
    private String databaseName;
    
    @JsonAlias({"FUNCTION_SCHEM"})
    private String schemaName;
    
    @JsonAlias({"FUNCTION_TYPE"})
    private Short functionType;
    
    @JsonAlias({"SPECIFIC_NAME"})
    private String specificName;
    
    private String functionBody;
    
    // ✅ 实现 IndexModel 接口方法
    @Override
    public String getTableName() {
        return null;  // 函数没有表名
    }
    
    @Override
    public Class<? extends IndexModel> getClassType() {
        return Function.class;
    }
    
    @Override
    public void setTableName(String tableName) {
        // 不支持
    }
}
```

#### Procedure.java（改造后）

```java
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Procedure implements IndexModel {  // ✅ 实现 IndexModel
    
    @JsonAlias({"PROCEDURE_NAME"})
    private String name;  // ✅ 实现 IndexModel
    
    @JsonAlias({"REMARKS"})
    private String comment;  // ✅ 实现 IndexModel
    
    private String aiComment;  // ✅ 新增
    
    private Long version;  // ✅ 新增
    
    @JsonAlias({"PROCEDURE_CAT"})
    private String databaseName;
    
    @JsonAlias({"PROCEDURE_SCHEM"})
    private String schemaName;
    
    // ... 其他字段不变
    
    @Override
    public String getTableName() {
        return null;
    }
    
    @Override
    public Class<? extends IndexModel> getClassType() {
        return Procedure.class;
    }
}
```

#### Trigger.java（改造后）

```java
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Trigger implements IndexModel {  // ✅ 实现 IndexModel
    
    private String name;  // ✅ 实现 IndexModel
    
    private String comment;  // ✅ 实现 IndexModel
    
    private String aiComment;  // ✅ 新增
    
    private Long version;  // ✅ 新增
    
    private String databaseName;
    
    private String schemaName;
    
    private String eventManipulation;
    
    private String triggerBody;
    
    @Override
    public String getTableName() {
        return null;
    }
    
    @Override
    public Class<? extends IndexModel> getClassType() {
        return Trigger.class;
    }
}
```

---

### 7.2 Service 层改造

#### FunctionServiceImpl.java（改造后）

```java
@Service
@Slf4j
public class FunctionServiceImpl implements FunctionService {
    
    @Autowired
    private LuceneIndexManagerFactory managerFactory;
    
    @Override
    public ListResult<Function> functions(String databaseName, String schemaName) {
        // 使用索引（如果有）
        FunctionQueryParams params = FunctionQueryParams.builder()
            .databaseName(databaseName)
            .schemaName(schemaName)
            .build();
        
        LuceneIndexManager<Function> mgr = managerFactory.getManager(getDataSourceId());
        Long version = mgr.getMaxVersion(params);
        
        if (version == null) {
            loadAndCacheMetadata(mgr, databaseName, schemaName, version);
        }
        
        List<Function> functions = (List<Function>) mgr.search(params, null, null);
        return ListResult.of(functions);
    }
    
    /**
     * 搜索函数树节点
     */
    public List<Function> searchTreeNodes(FunctionSearchParam param) {
        LuceneIndexManager<Function> mgr = managerFactory.getManager(param.getDataSourceId());
        Long version = mgr.getMaxVersion(param);
        
        if (needRefreshCache(param, version)) {
            loadAndCacheMetadata(mgr, param.getDatabaseName(), param.getSchemaName(), version);
        }
        
        return (List<Function>) mgr.search(param, null, param.getSearchKey());
    }
    
    /**
     * 加载并缓存元数据
     */
    private void loadAndCacheMetadata(LuceneIndexManager<Function> mgr, 
                                      String databaseName, 
                                      String schemaName, 
                                      Long version) {
        mgr.getLock().writeLock().lock();
        try {
            Connection conn = Chat2DBContext.getConnection();
            MetaData meta = Chat2DBContext.getMetaData();
            List<Function> functions = meta.functions(conn, databaseName, schemaName);
            
            // 设置版本和 AI 注释
            functions.forEach(f -> {
                f.setVersion(version);
                // 可以调用 AI 服务生成注释
                // f.setAiComment(aiService.generateComment(f));
            });
            
            mgr.updateDocuments(functions, version);
        } catch (Exception e) {
            log.error("loadAndCacheMetadata error", e);
        } finally {
            mgr.getLock().writeLock().unlock();
        }
    }
}
```

---

### 7.3 API 层改造

#### TreeSearchRequest.java（新建）

```java
@Data
public class TreeSearchRequest extends DataSourceBaseRequestInfo {
    
    @NotNull
    private Long dataSourceId;
    
    private String databaseName;
    
    private String schemaName;
    
    @NotBlank
    private String searchKey;
    
    /**
     * 树节点类型过滤
     * TABLE / VIEW / FUNCTION / PROCEDURE / TRIGGER
     */
    private String treeNodeType;
    
    private Integer pageSize = 100;
}
```

#### TreeNodeVO.java（新建）

```java
@Data
public class TreeNodeVO {
    
    private String uuid;
    
    private String key;
    
    private String name;
    
    private String treeNodeType;
    
    private String pretendNodeType;
    
    private String comment;
    
    private Boolean isLeaf;
    
    private Boolean pinned;
    
    /**
     * 父路径（核心字段）
     * 例如：["database_name", "schema_name"]
     */
    private List<String> parentPath;
    
    /**
     * 扩展参数
     */
    private Map<String, Object> extraParams;
}
```

#### 统一搜索接口（TreeController.java 新建）

```java
@Slf4j
@ConnectionInfoAspect
@RequestMapping("/api/rdb/tree")
@RestController
public class TreeController {
    
    @Autowired
    private TableService tableService;
    
    @Autowired
    private ViewService viewService;
    
    @Autowired
    private FunctionService functionService;
    
    @Autowired
    private ProcedureService procedureService;
    
    @Autowired
    private RdbWebConverter rdbWebConverter;
    
    /**
     * 统一树搜索接口
     */
    @GetMapping("/search")
    public ListResult<TreeNodeVO> searchTree(@Valid TreeSearchRequest request) {
        TreeSearchParam param = rdbWebConverter.toTreeSearchParam(request);
        
        List<TreeNode> result = new ArrayList<>();
        
        // 根据类型分发
        String type = request.getTreeNodeType();
        if (StringUtils.isBlank(type) || "ALL".equals(type)) {
            // 搜索所有类型
            result.addAll(tableService.searchTreeNodes(param));
            result.addAll(viewService.searchTreeNodes(param));
            result.addAll(functionService.searchTreeNodes(param));
            result.addAll(procedureService.searchTreeNodes(param));
        } else {
            switch (type) {
                case "TABLE":
                    result.addAll(tableService.searchTreeNodes(param));
                    break;
                case "VIEW":
                    result.addAll(viewService.searchTreeNodes(param));
                    break;
                case "FUNCTION":
                    result.addAll(functionService.searchTreeNodes(param));
                    break;
                case "PROCEDURE":
                    result.addAll(procedureService.searchTreeNodes(param));
                    break;
                default:
                    log.warn("Unknown tree node type: {}", type);
            }
        }
        
        return ListResult.of(rdbWebConverter.treeNode2vo(result));
    }
}
```

---

### 7.4 前端改造

#### API 定义（src/service/sql.ts）

```typescript
export interface ITreeSearchParams {
  dataSourceId: number;
  dataSourceName: string;
  databaseType: DatabaseTypeCode;
  databaseName?: string;
  schemaName?: string;
  searchKey: string;
  treeNodeType?: string;
  pageSize?: number;
}

export interface ITreeNodeResponse {
  uuid: string;
  key: string;
  name: string;
  treeNodeType: TreeNodeType;
  pretendNodeType?: TreeNodeType;
  comment?: string;
  isLeaf?: boolean;
  pinned?: boolean;
  parentPath?: string[];
  databaseName?: string;
  schemaName?: string;
  extraParams: {
    dataSourceId: number;
    dataSourceName: string;
    databaseName?: string;
    schemaName?: string;
    tableName?: string;
    functionName?: string;
    procedureName?: string;
    [key: string]: any;
  };
}

const searchTree = createRequest<ITreeSearchParams, ITreeNodeResponse[]>(
  '/api/rdb/tree/search', 
  { method: 'get' }
);
```

#### Tree 组件改造（src/blocks/Tree/index.tsx）

```typescript
// 新增状态
const [backendSearchData, setBackendSearchData] = useState<ITreeNode[] | null>(null);
const [backendSearchLoading, setBackendSearchLoading] = useState(false);

const currentConnectionDetails = useWorkspaceStore((state) => state.currentConnectionDetails);

// 新增 useEffect 调用后端搜索
useEffect(() => {
  if (searchValue && currentConnectionDetails?.id) {
    // 决策：数据量 >= 200 使用后端搜索
    const useBackendSearch = treeData && treeData.length >= 200;
    
    if (useBackendSearch) {
      setBackendSearchLoading(true);
      searchTree({
        dataSourceId: currentConnectionDetails.id,
        dataSourceName: currentConnectionDetails.alias,
        databaseType: currentConnectionDetails.type,
        searchKey: searchValue,
        pageSize: 100,
      })
        .then((res) => {
          // 将后端返回的扁平数据转为树结构
          const treeData = buildTreeFromFlatData(res);
          setBackendSearchData(treeData);
          setScrollTop(0);
        })
        .catch(() => {
          setBackendSearchData([]);
        })
        .finally(() => {
          setBackendSearchLoading(false);
        });
    }
  } else {
    setBackendSearchData(null);
  }
}, [searchValue, treeData, currentConnectionDetails]);  // ✅ 修复：添加 treeData 依赖

// 树重构算法
function buildTreeFromFlatData(flatNodes: ITreeNode[]): ITreeNode[] {
  const map = new Map<string, ITreeNode>();
  const pathMap = new Map<string, ITreeNode>();
  const roots: ITreeNode[] = [];
  
  // 1. 创建所有节点
  flatNodes.forEach(item => {
    map.set(item.uuid, { ...item, children: [] });
  });
  
  // 2. 收集路径节点
  flatNodes.forEach(node => {
    if (node.parentPath && node.parentPath.length > 0) {
      let currentPath = '';
      node.parentPath.forEach((pathItem, index) => {
        const prevPath = currentPath;
        currentPath = prevPath ? `${prevPath}/${pathItem}` : pathItem;
        
        if (!pathMap.has(currentPath)) {
          const pathNode: ITreeNode = {
            uuid: `path-${currentPath}`,
            name: pathItem,
            treeNodeType: getPathNodeType(index),
            children: null,
            extraParams: buildPathNodeExtraParams(node.extraParams, index),
          };
          pathMap.set(currentPath, pathNode);
        }
      });
    }
  });
  
  // 3. 构建路径树
  pathMap.forEach((pathNode, path) => {
    const pathParts = path.split('/');
    if (pathParts.length > 1) {
      const parentPath = pathParts.slice(0, -1).join('/');
      const parentNode = pathMap.get(parentPath);
      if (parentNode) {
        parentNode.children = [...(parentNode.children || []), pathNode];
      }
    } else {
      roots.push(pathNode);
    }
  });
  
  // 4. 添加搜索结果到路径
  flatNodes.forEach(node => {
    if (node.parentPath && node.parentPath.length > 0) {
      const parentKey = node.parentPath.join('/');
      const parentNode = pathMap.get(parentKey);
      if (parentNode) {
        parentNode.children = [...(parentNode.children || []), node];
      }
    } else {
      roots.push(node);
    }
  });
  
  // 5. 标记展开状态
  pathMap.forEach(node => {
    // 自动展开路径节点
  });
  
  return roots;
}

// 修改渲染逻辑
const realNodeList = (backendSearchData || searchSmoothTreeData || smoothTreeData).slice(startIdx, startIdx + 50);
```

---

## 八、数据存储架构

### 8.1 Lucene 索引文件位置

```
物理存储：
└─ ~/.chat2db/index/
   ├─ {dataSourceId}_dev/
   │  ├─ segments_*
   │  ├─ write.lock
   │  ├─ _0.cfs
   │  └─ _1.cfs
   └─ {dataSourceId}_test/
      └─ ...
```

**代码位置**：`LuceneIndexManager.java:127-137`

```java
private String getIndexPath(Long id) {
    String environment = StringUtils.defaultString(System.getProperty("spring.profiles.active"), "dev");
    String basePath = System.getProperty("user.home") + "/.chat2db/index/";
    switch (environment.toLowerCase()) {
        case "test":
            return basePath + id + "_test";
        case "dev":
        default:
            return basePath + id + "_dev";
    }
}
```

### 8.2 索引字段结构

```
Document 结构：
├─ type (StringField)           - 类型标识：Table/View/Function/Procedure
├─ name (TextField)             - 名称（支持全文搜索）
├─ comment (TextField)          - 注释（支持全文搜索）
├─ aiComment (TextField)        - AI 注释（支持全文搜索）
├─ databaseName (StringField)   - 数据库名
├─ schemaName (StringField)     - Schema 名
├─ tableName (StringField)      - 表名
├─ version (NumericDocValuesField) - 版本号
└─ source (StoredField)         - JSON 序列化的完整对象
```

---

## 九、实施计划

### 9.1 工作分解

| 阶段 | 任务 | 子任务 | 预估工时 | 优先级 |
|------|------|--------|----------|--------|
| **1** | **数据模型改造** | 1.1 Function 实现 IndexModel | 2h | P0 |
| | | 1.2 Procedure 实现 IndexModel | 2h | P0 |
| | | 1.3 Trigger 实现 IndexModel | 2h | P0 |
| | | 1.4 单元测试 | 1h | P0 |
| **2** | **Service 层改造** | 2.1 FunctionServiceImpl | 3h | P0 |
| | | 2.2 ProcedureServiceImpl | 3h | P0 |
| | | 2.3 ViewServiceImpl | 3h | P0 |
| | | 2.4 新增 TriggerServiceImpl | 4h | P1 |
| | | 2.5 集成测试 | 2h | P0 |
| **3** | **API 层改造** | 3.1 TreeSearchRequest | 1h | P0 |
| | | 3.2 TreeNodeVO | 1h | P0 |
| | | 3.3 TreeSearchParam | 1h | P0 |
| | | 3.4 TreeController | 3h | P0 |
| | | 3.5 Converter | 2h | P0 |
| **4** | **前端改造** | 4.1 API 定义 | 1h | P0 |
| | | 4.2 Tree 组件改造 | 4h | P0 |
| | | 4.3 树重构算法 | 3h | P0 |
| | | 4.4 搜索模式决策 | 2h | P0 |
| | | 4.5 UI 优化 | 2h | P1 |
| **5** | **测试优化** | 5.1 单元测试 | 3h | P0 |
| | | 5.2 集成测试 | 4h | P0 |
| | | 5.3 性能测试 | 2h | P1 |
| | | 5.4 Bug 修复 | 2h | P0 |
| **总计** | | | **46h** | |

### 9.2 里程碑

```
Week 1: 后端数据模型 + Service 改造
├─ Day 1-2: Function/Procedure/Trigger 模型改造
├─ Day 3-4: Service 层改造
└─ Day 5: 单元测试 + Code Review

Week 2: API 层 + 前端改造
├─ Day 1-2: API 层改造（Request/VO/Controller）
├─ Day 3-4: 前端 Tree 组件改造
└─ Day 5: 集成测试

Week 3: 测试优化
├─ Day 1-2: 集成测试 + Bug 修复
├─ Day 3: 性能优化
└─ Day 4-5: 上线准备
```

---

## 十、风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| **数据模型不兼容** | 高 | 中 | 保持现有字段，新增可选字段；向后兼容 |
| **Lucene 索引过大** | 中 | 中 | 设置索引大小限制；定期清理旧索引 |
| **性能下降** | 高 | 低 | 性能测试；优化索引策略；缓存优化 |
| **前端渲染卡顿** | 中 | 中 | 虚拟滚动；分页加载；防抖优化 |
| **AI Comment 生成慢** | 低 | 高 | 异步生成；批量处理；降级处理 |

---

## 十一、成功标准

### 11.1 功能标准

- ✅ View/Function/Procedure 支持 searchKey 搜索
- ✅ 搜索结果自动展开父路径
- ✅ 切换数据源后搜索自动刷新
- ✅ 支持大数据量场景（>1000 条）
- ✅ 搜索响应时间 < 500ms

### 11.2 性能标准

- ✅ 首次加载时间 < 3s
- ✅ 搜索响应时间 < 500ms
- ✅ 索引文件大小 < 100MB
- ✅ 内存占用 < 500MB

### 11.3 体验标准

- ✅ 搜索输入防抖（300ms）
- ✅ Loading 状态提示
- ✅ 搜索结果显示计数
- ✅ 支持"加载更多"（分页）
- ✅ 高亮匹配关键词

---

## 附录

### A. 相关文件位置

**前端**：
- `chat2db-client/src/pages/main/workspace/components/TableList/index.tsx`
- `chat2db-client/src/pages/main/workspace/components/OperationLine/index.tsx`
- `chat2db-client/src/blocks/Tree/index.tsx`
- `chat2db-client/src/blocks/Tree/treeConfig.tsx`
- `chat2db-client/src/service/sql.ts`

**后端**：
- `chat2db-server/chat2db-spi/src/main/java/ai/chat2db/spi/model/Table.java`
- `chat2db-server/chat2db-spi/src/main/java/ai/chat2db/spi/model/Function.java`
- `chat2db-server/chat2db-spi/src/main/java/ai/chat2db/spi/model/Procedure.java`
- `chat2db-server/chat2db-spi/src/main/java/ai/chat2db/spi/model/Trigger.java`
- `chat2db-server/chat2db-server-domain/chat2db-server-domain-core/src/main/java/ai/chat2db/server/domain/core/impl/TableServiceImpl.java`
- `chat2db-server/chat2db-server-domain/chat2db-server-domain-core/src/main/java/ai/chat2db/server/domain/core/impl/ViewServiceImpl.java`
- `chat2db-server/chat2db-server-domain/chat2db-server-domain-core/src/main/java/ai/chat2db/server/domain/core/impl/FunctionServiceImpl.java`
- `chat2db-server/chat2db-server-domain/chat2db-server-domain-core/src/main/java/ai/chat2db/server/domain/core/impl/ProcedureServiceImpl.java`
- `chat2db-server/chat2db-server-domain/chat2db-server-domain-core/src/main/java/ai/chat2db/server/domain/core/cache/LuceneIndexManager.java`
- `chat2db-server/chat2db-server-web/chat2db-server-web-api/src/main/java/ai/chat2db/server/web/api/controller/rdb/TableController.java`

### B. Bug 修复代码

**修复位置**：`Tree/index.tsx:167`

```typescript
// 修改前（Bug）
useEffect(() => {
  if (searchValue && treeData) {
    const _searchTreeData = searchTree(cloneDeep(treeData), searchValue);
    setSearchTreeData(_searchTreeData);
    setScrollTop(0);
  } else {
    setSearchTreeData(null);
  }
}, [searchValue]);  // ❌ 缺少 treeData 依赖

// 修改后（已修复）
useEffect(() => {
  if (searchValue && treeData) {
    const _searchTreeData = searchTree(cloneDeep(treeData), searchValue);
    setSearchTreeData(_searchTreeData);
    setScrollTop(0);
  } else {
    setSearchTreeData(null);
  }
}, [searchValue, treeData]);  // ✅ 添加 treeData 依赖
```

---

**文档结束**