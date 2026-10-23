# 晴屿 AI · Vue 3 前端

暖色应用广场、阳光配色的 AI 心理树洞，以及明亮科技风的 Manus 智能体。使用 Vue 3 Composition API、Vue Router、Axios 和 Vite，图标来自 Lucide。所有插画均为本地 SVG，无需外部图片或字体服务。

## 启动

需要 Node.js 20.19+ 或 22.12+（推荐 Node.js 24）。在本目录执行：

```sh
npm install
npm run dev
```

访问 http://localhost:5173。开发服务器将 `/api` 请求代理到 `http://localhost:8123`，请先启动 Spring Boot 后端。哈希路由无需额外服务器路由回退配置。

```sh
npm test         # 流式解析、UTF-8、完成/错误事件、停止生成等测试
npm run build   # 生产构建，输出 dist/
npm run preview # 预览生产构建，也提供本地 API 代理
```

如需在不调用真实模型的情况下检查流式交互，可在后端未运行时，另开终端执行 `node tests/fixture-server.mjs`，启动显式测试服务。回复会标明测试内容；`测试停止生成` 会持续输出，`测试服务错误` 会返回 503。测试结束后用 Ctrl+C 关闭。该服务不会随前端启动，也不作为接口失败时的自动降级。

## 接口配置

默认配置匹配**当前仓库的实际 Controller**：

| 应用     | 方法与路径（相对于 `/api`） | 参数                             |
| -------- | --------------------------- | -------------------------------- |
| 心理树洞 | `GET /ai/mind_app/chat/sse` | 查询参数 `message`、`chatId`     |
| Manus    | `POST /ai/manus/chat`       | JSON 请求体 `message`、`history` |

智能体接口已升级，请同时更新并重启 Spring Boot 后端。POST 请求体中的 `history` 是当前会话最近最多 10 轮完整的用户问题与最终助手回答，总计最多 60000 字符。不包含失败、停止或尚未结束的回复，不在服务器保存跨会话状态。使用请求体可以避免 GET 地址长度限制。原 GET 接口保留为单轮调用入口。

需求示例中树洞路径为 `/ai/mindapp/chat/sse`，与仓库的 `mind_app` 不同。若后端采用示例路径，将 `.env.example` 复制为 `.env.local` 并修改：

```dotenv
VITE_MIND_CHAT_PATH=/ai/mindapp/chat/sse
```

其他环境变量：

```dotenv
VITE_API_BASE_URL=/api
API_PROXY_TARGET=http://localhost:8123
VITE_MANUS_CHAT_PATH=/ai/manus/chat
```

修改后需重启 Vite。生产环境的 Vite 开发代理不生效，应通过 Nginx 等将 `/api/` 反向代理至后端，或将 `VITE_API_BASE_URL` 设置为允许跨域的后端完整地址后重新构建。Nginx 代理 SSE 时应关闭缓冲，例如：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8123;
    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 360s;
}
```

## 会话与流式行为

- 初次进入聊天应用自动通过 `crypto.randomUUID()` 生成会话 ID；“开启新对话”生成新 ID。树洞请求始终携带当前会话 ID。
- 切换应用保留当前页面内的对话，左侧可切换最近的会话。对话仅存储在内存中，刷新、关闭页面后清空；前端不将内容写入 localStorage、sessionStorage 或日志。内容仍会发送至后端及其 AI 提供商。
- Axios 使用浏览器 `fetch` adapter 和 `responseType: 'stream'`，读取 `ReadableStream`，无需 EventSource 自动重连，避免重复提交用户消息。
- SSE 支持 UTF-8 分片、CR/LF/CRLF、注释心跳和多行 data。树洞 token 直接拼接。Manus 仅通过 `answer` 事件输出最终答复，token 按原样拼接，不额外插入空格或换行；收到 `done` 后才视为完成。中途断开、模型长度截断或 `error` 事件会保留部分内容并明确提示失败。
- 停止生成通过 AbortController 关闭当前前端请求。后端是否停止工具执行取决于服务端取消逻辑。
- 支持等待状态、部分回复保留、连接失败提示、手动重试、中文输入法、Enter 发送、Shift+Enter 换行、复制回复、复制会话 ID 和按用户滚动位置跟随新内容。
- Manus 在内部完成检索与工具调用，再通过无工具生成阶段整合为一份完整答复。搜索摘要、JSON 与工具日志不会直接显示；前端也会过滤旧执行记录及其步骤前缀。普通计划问题可直接回答或询问必要信息。模型回答质量仍取决于资料与模型能力，不足和失败应明确说明。
- 树洞提供日常陪伴，不替代专业心理咨询。

## 目录

```text
src/
  components/       # 图标与本地 SVG 插画
  composables/      # 会话状态、停止及重试
  lib/api.js        # Axios 与接口配置
  lib/sse.js        # 增量 SSE 解析和读取
  views/            # 主页与两种聊天界面
  tokens.css        # 色彩、字体设计变量
  styles.css        # 响应式样式及减少动态效果支持
tests/              # Node.js 原生测试，无额外测试运行时
```

主页提供心理树洞与智能体的直接入口、三个预填话题、可选择的心情标签和一分钟呼吸练习。快捷话题只预填输入框，需用户确认发送。首屏采用本地 SVG 绘制的静态 2.5D 哑光太阳小岛，包含统一光照、柔和接触阴影和轻量磨砂纹理。页面支持窄屏导航、键盘焦点、跳过导航链接、可访问的图标按钮和 `prefers-reduced-motion`。
