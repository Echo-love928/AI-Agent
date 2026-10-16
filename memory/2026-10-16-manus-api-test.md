# doChatWithManus 接口实测

测试日期：2026-10-16，北京时间。

接口：`GET http://localhost:8123/api/ai/manus/chat?message=...`，返回 `SseEmitter`。

测试方式：使用当前编译后的代码启动应用，通过真实 HTTP 请求调用真实模型和已注册工具。测试脚本逐条读取 SSE 事件，读取流结束后检查结果，未通过模拟模型替代实际执行。

| 场景 | 实际结果 | 结论 |
| --- | --- | --- |
| 普通回复 | HTTP 200，`text/event-stream`，收到 `Step 1: MANUS_STREAM_OK` 后正常结束 | 通过 |
| 文件工具执行 | 依次收到 writeFile、readFile、doTerminate 三条事件；磁盘文件内容与随机测试标记完全一致 | 通过 |
| message 为空字符串 | 返回“错误：不能使用空提示词运行代理”，连接正常结束 | 通过 |
| 缺少 message 参数 | 返回相同的空提示词错误，连接正常结束 | 通过 |

文件任务首条事件约 2.49 秒到达，总耗时约 6.57 秒。三个步骤分别送达，确认接口按执行步骤推送结果。当前实现不是模型逐 token 推送。

本轮 runId：`7c72f4d5-716a-4b70-8021-4920ebab3e1a`。

实际生成文件：`tmp/file/manus-api-7c72f4d5-716a-4b70-8021-4920ebab3e1a.txt`。

文件内容：`MANUS_TOOL_TEST_7c72f4d5-716a-4b70-8021-4920ebab3e1a`。

注意：空参数目前通过 HTTP 200 + SSE 错误文本表达，不返回 HTTP 400。本测试验证了现有行为，不能将 HTTP 200 单独作为业务成功依据。

证据：`tmp/manus-api-test.mjs`、`tmp/manus-api-results.json`、`tmp/manus-api-server.log`。

运行说明：测试前本机 8123 无运行实例。离线打包在 JAR 插件阶段因本地缺少插件依赖而失败，源码及测试代码编译已完成；随后使用已有本地依赖类路径启动当前应用并完成上述 HTTP 测试，没有交付新的打包产物。

范围：未测试五分钟超时、客户端中途断开、最大步骤限制、并发请求或模型故障场景。未修改应用源代码。
