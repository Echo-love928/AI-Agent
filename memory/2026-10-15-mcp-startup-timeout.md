# MCP 初始化超时导致 Swagger 不可用

- 症状：IDEA Debug 启动失败，Swagger/Knife4j 无法访问。
- 已确认的直接原因：异常链为 `manus -> dashScopeChatModel -> toolCallingManager -> mcpToolCallbacks -> mcpSyncClients`，MCP 初始化在 20000ms 后超时，Spring 上下文随之终止。
- 依赖源码证据：Spring AI 1.1.2 的 `McpClientAutoConfiguration` 仅向 SDK 设置 `requestTimeout`。MCP SDK 0.14.0 的 `initializationTimeout` 独立默认为 20 秒，实际异常来自 `LifecycleInitializer` 的初始化超时。
- 当前服务检查：地图、图片搜索 MCP 分别约 2.5 秒、3.3 秒完成握手。未复现用户在 Debug 时的具体延迟来源；冷启动下载、网络等待或断点暂停均尚未确认为本次诱因。
- 修改：`application.yml` 将 `spring.ai.mcp.client.request-timeout` 配为 120s；新增 `McpClientConfiguration`，将同步客户端的独立初始化超时与该值对齐。未禁用 MCP，也未修改 Swagger 依赖。
- 回归测试：`McpClientConfigurationTests` 用延迟握手复现较短初始化时限失败，验证配置较长时限后成功，验证超过配置时限仍会失败。
- 应用验证：`AiAgentApplicationTests` 保留真实 MCP 初始化，用 MockMvc 验证 `/v3/api-docs` 返回 200 且包含同步和 SSE 聊天接口，`/doc.html` 返回 200。知识库 Bean 和启动演示已由既有测试中的 mock 替换，未调用真实模型或搜索工具。
- 测试结果：运行 `mvn -o -Dtest=*Tests test`，71 项，67 项通过、4 项按条件跳过，0 失败、0 错误。没有执行以 `Test` 结尾且会调用真实模型或外网工具的演示测试。
- 证据：`tmp/diagnose-mcp-startup.mjs`、`tmp/mcp-startup-verification.log`、`tmp/mcp-startup-regression.log`。
- 访问：正常启动后打开 `http://localhost:8123/api/doc.html` 或 `http://localhost:8123/api/swagger-ui.html`。
- 限制：IDEA Debug 原场景需要用户重启后确认；暂停超过 120 秒或服务持续不可用时仍可能超时。延长等待时间不能修复持续的网络故障。
- 状态：DONE_WITH_CONCERNS。超时配置及应用文档端点已验证，用户 Debug 环境中的具体延迟诱因仍未确定。
