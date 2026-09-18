# MCP 写操作审批

在「设置 → MCP 服务器」配置「客户端审批白名单关键字」。默认包含 `codex`，
按客户端 `clientInfo.name` / `clientInfo.title` 忽略大小写进行包含匹配。
匹配的客户端由其自身审批机制处理；未匹配的客户端必须通过 MCP elicitation
接受请求并勾选确认。不支持 elicitation、拒绝、取消或未勾选确认时不执行变更。
白名单是客户端兼容配置，不验证客户端身份，也不会替客户端启用审批策略。

配置保存在 `mcp.server.config` 的 `approvalClientKeywords` 字段，点击「应用」后对后续调用生效。
旧配置缺少该字段时沿用默认值；保存空数组 `[]` 可让所有客户端使用 elicitation。
未保存界面配置时，也可通过 `chat2db.mcp.approval-client-keywords` 设置默认值。

SQL 使用 `executeUpdate` / `executeDdl`；MongoDB 使用 `executeMongoWrite` /
`executeMongoAdmin`；Redis 使用 `executeRedisKeyChange`。MongoDB 和 Redis 的
旧两步工具已移除，客户端更新后需重新连接以刷新工具列表。

白名单只省略服务端 elicitation。写入/结构管理开关、生产数据源限制、参数校验、
MongoDB 影响文档数检查和 Redis key 指纹复核仍然执行。
