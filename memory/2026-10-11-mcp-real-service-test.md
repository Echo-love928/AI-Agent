# MCP 真实服务测试记录

测试时间：2026-10-11，北京时间。

用户消息：今天工作很烦，想出去走走，我住在广东潮州，想找个公园

## 结果

- `MindAppMcpIntegrationTests` 已启用真实服务运行：1 项测试、0 失败、0 错误、0 跳过。
- 地图 MCP 初始化成功，DashScope 返回真实回复。
- 指定消息这一轮没有地图工具调用记录。模型回复仍询问所在城市，没有给出查询结果。因此“回复非空”断言通过只能证明对话可执行，不能证明模型实际调用了地图 MCP。
- 额外使用已安装的官方 MCP SDK 调用同一地图服务的 `maps_text_search`，参数为 `{"keywords":"公园","city":"潮州"}`。返回 `isError=false` 和 20 个地点，包括潮州西湖公园、潮人公园、凤凰洲公园、池湖公园。
- 地图 MCP 暴露 12 个工具，地图凭证和地点查询接口可用。

## 初始化超时原因和处理

首次执行在 20 秒的 MCP 初始化等待后失败。npm 日志显示下载 `@amap/amap-maps-mcp-server` 时，访问官方源产生 `ETIMEDOUT`。

系统已启用本机 HTTP 代理，PowerShell 经系统代理访问 npm 官方源返回 HTTP 200；npx 进程没有使用该代理。为本次进程设置 `npm_config_proxy`、`npm_config_https_proxy` 为系统代理地址后，官方包下载成功，重试初始化也成功。没有修改全局 npm 配置或项目配置。

## 复现和证据

配置真实 DashScope、地图 MCP，并在测试进程设置 `RUN_MCP_INTEGRATION_TESTS=true`，执行 `mvnw.cmd -Dtest=MindAppMcpIntegrationTests test`。如果 npx 无法访问 npm 官方源，需要在同一进程配置可用的 npm 代理。

- `tmp/mcp-live-test.log`：真实 Spring Boot、MCP 初始化和模型对话日志。
- `tmp/mcp-package-prepare.log`：依赖准备结果。
- `tmp/mcp-live-smoke.mjs`：直接调用真实地图 MCP 的诊断脚本。
- `tmp/mcp-map-search-result.json`：已脱敏的地图查询结果。

未修改应用或测试代码，未修改凭证。无需因本次环境修复扩展执行其他真实服务测试。

状态：DONE_WITH_CONCERNS。服务连接和直接地图查询已验证；自然语言请求触发地图工具的完整流程尚未验证通过。后续可记录实际工具调用并完善测试断言，再检查工具选择提示和模型配置。

## 后续完整流程验证

`MindAppMcpIntegrationTests` 已增加真实回调记录与断言：模型必须自动调用地点搜索，调用参数必须包含潮州位置，地图结果必须包含 POI，最终回复必须引用至少一个返回地点。保留原始用户消息，不手动执行自动调用测试的工具，也不强制工具选择。另有独立的 `mapServiceReturnsSearchResults` 检查真实服务和凭证。

一次真实运行观察到 `maps_text_search`（city=潮州，keywords=公园）以及两次 `maps_search_detail`，最终推荐潮州西湖公园和慧如公园。该轮发现测试解析错误：Spring AI 将 TextContent 序列化为只有 `text` 的数组项，没有 `type` 字段。独立服务调用复现了实际格式，随后修正解析，并通过独立服务测试验证。

最新运行：11 项测试，10 项通过，1 项失败。独立地图服务检查和 9 项 MindApp 单元测试通过；完整自动调用测试失败，模型只回答“你在哪个城市呢？”，没有实际搜索。说明自然语言触发不稳定，当前不能宣称完整流程稳定通过。

运行方式：启用 `RUN_MCP_INTEGRATION_TESTS=true` 后执行 `mvnw.cmd -Dtest=MindAppMcpIntegrationTests test`；需要代理时，在测试进程设置 npm 代理。

新增证据：`tmp/mcp-end-to-end-test.log`、`tmp/mcp-observed-calls.json`、`tmp/mcp-map-callback-result.json`。

## MCP 提示词调整与复测

为 `doChatWithMcp` 追加独立的 `prompts/mcp-map-system.txt`，保留心理支持系统提示词。已知城市且明确找地点时，要求先执行真实搜索，再依据返回地点推荐；不重复询问已知城市，不声称已经执行未发生的查询。普通对话不追加地图规则。原始测试消息未修改，每次使用新的 chatId，自动调用测试重复 3 次，并分别保存调用记录。

首轮提示词调整的 3 次自动调用中，2 次完成真实搜索和结果引用，1 次未调用工具却声称搜索。随后强化查询完成条件，并将 MCP 对话温度降为 0。

通用 ChatOptions 导致 HTTP 400 的原因已从 DashScope 依赖源码确认：转换为 DashScopeChatOptions 时，multiModel 默认 false 覆盖配置中的 true。已改为复制模型默认的 DashScopeChatOptions 后只修改温度，保留模型和 multi-model 设置；普通对话仍使用原选项。单元断言同时验证这些行为和默认提示词不变。

最终代码的真实服务复测：独立地图服务检查通过，3 次自动调用中 1 次完整通过、2 次未发出工具调用。通过的一次调用 maps_text_search 两次，返回合计 40 个 POI，最终回复引用潮州西湖公园、凤凰洲公园、慧如公园。9 项 MindApp 单元测试最终全部通过，git diff --check 通过。

状态：DONE_WITH_CONCERNS。提示词与采样参数调整已完成，但当前模型的自动地图选择仍不稳定，不能宣称完整链路稳定通过。若要求每个明确地点请求都必定查询，需要进一步设计意图路由与工具选择约束；不应仅靠提示词或将回复非空视为调用成功。

最终证据：`tmp/mcp-prompt-options-verification.log`、`tmp/mcp-prompt-unit-verification.log`、`tmp/mcp-observed-calls-<chatId>.json`。HTTP 400 的中间诊断记录为 `tmp/mcp-prompt-verification-final.log`。
