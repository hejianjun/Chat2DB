# 后端启动优化与复测

2026-10-09，直接修改源码并构建 `chat2db-server-start` 测试。未替换桌面安装包，也未重启桌面端服务。

## 修改

- 在 Maven 注解处理器列表中加入与 Spring Framework 版本一致的 `spring-context-indexer`。此前虽然声明了依赖，但显式处理器列表未包含它，未生成组件索引。
- MCP 工具实例正常注册，业务服务通过构造参数上的 `@Lazy` 延迟到首次调用；Mongo 工具共享执行类也采用相同方式。
- Redis 审计接收器仍在启动时注册，操作日志服务延迟到使用时初始化。
- 轻量入口移除 `@EnableCaching`。源码中没有 Spring/JCache 缓存注解；现有缓存通过 `CacheManage`/`MemoryCacheManage` 直接管理。
- 增加可选的启动阶段录制：`-Dchat2db.startup.profile=true`。日志输出最慢的 20 个阶段；阶段包含嵌套耗时，不能直接相加。

MCP 仍在启动完成前初始化并加载数据源资源，没有改成后台加载，也没有修改鉴权或工具协议。

## 实测

同一台 Windows 机器、桌面安装包自带 Java 17.0.9、`-Xmx1024M -noverify`、release profile、独立端口 10829 和独立测试 user.home。使用同一测试数据库重复启动，测量为重复启动后的文件缓存条件，不代表冷磁盘测试或用户实际数据库规模。排除与 Maven 编译重叠的试验以及启用录制的诊断试验。

| 对照 | 三次启动完成耗时 | 中位数 |
|---|---|---|
| 基线复测 | 14.312 / 13.404 / 13.234 秒 | 13.404 秒 |
| 最终源码，默认 JVM 编译参数 | 12.231 / 12.310 / 12.742 秒 | 12.310 秒 |
| 最终源码，加 `-XX:TieredStopAtLevel=1` | 8.439 / 9.010 / 8.423 秒 | 8.439 秒 |

默认参数下中位数减少约 1.09 秒（8.2%）。快速编译参数下减少约 4.97 秒（37.0%），但该参数限制后续 JIT 优化层级，尚未验证长时间运行、大结果集和导出吞吐，所以未设为默认参数。

最终源码默认参数下，首个 `/api/system` 请求耗时 605～660ms，环境列表请求 46～49ms，MCP 初始化握手 75～80ms；以上为 PowerShell 客户端测量。

构建通过；HTTP、MCP initialize/initialized、tools/list、resources/list 均通过。改前改后 30 个工具的完整定义一致。

63 个针对性回归测试通过，包括新增的工具注册不提前实例化业务服务、首次调用能正确解析服务的 Spring 上下文测试。仓库原有 `RedisToolsTest` 引用了已不存在的 `PreparedRedisChangeStore`，正常测试编译受其阻塞；针对性运行使用临时 POM 排除了该文件，随后删除临时 POM。未修改这项既有问题，不能据此声称完整测试套件通过。

## 复测

在仓库根目录运行，Java 路径可通过 `-JavaPath` 指定：

```powershell
$env:JAVA_HOME = 'D:\tool\Java\jdk-17'
Push-Location chat2db-server
mvn -pl chat2db-server-start -am package '-Dmaven.test.skip=true'
Pop-Location

./script/measure-backend-startup.ps1 `
  -Jar "$PWD/chat2db-server/chat2db-server-start/target/chat2db-server-start.jar" `
  -Label source -Runs 3
```

追加 `-FastJit` 可测快速编译参数；追加 `-Profile` 输出启动阶段；追加 `-Record` 保存 JFR。录制试验应单独运行，不混入基线对比。

脚本检查端口是否空闲，启动独立进程，验证 HTTP/MCP，最后调用停止接口并等待进程退出。所有日志、工具定义快照及 CSV 保存在 `logs/startup-benchmark/`。应串行运行，避免构建或其他测试进程干扰结果。

剩余较大开销是配置类解析和 Web 容器初始化。诊断试验中配置类解析约 3.47 秒、Web 容器创建约 1.83 秒，MCP 服务创建约 1.37 秒；这些数字来自启用录制的试验，仅用于定位。真实 SQL 首次调用和大批量导出尚未压测。
