// Explicit test fixture. Never started by the app or used as a production fallback.
import { createServer } from "node:http";

const server = createServer(async (request, response) => {
  const url = new URL(request.url, "http://localhost:8123");
  if (
    !["/api/ai/mind_app/chat/sse", "/api/ai/manus/chat"].includes(url.pathname)
  ) {
    response.writeHead(404).end();
    return;
  }
  let body = {};
  if (request.method === "POST") {
    try {
      let content = "";
      for await (const chunk of request) content += chunk;
      body = JSON.parse(content);
    } catch {
      response.writeHead(400).end("Invalid JSON");
      return;
    }
  }
  const message = body.message || url.searchParams.get("message");
  if (
    !message ||
    (url.pathname.includes("mind_app") && !url.searchParams.get("chatId"))
  ) {
    response.writeHead(400).end("Missing message or chatId");
    return;
  }
  console.log(
    JSON.stringify({
      path: url.pathname,
      message,
      chatId: url.searchParams.get("chatId"),
    }),
  );
  if (message === "测试服务错误") {
    response.writeHead(503).end("Test error");
    return;
  }
  response.writeHead(200, {
    "Content-Type": "text/event-stream;charset=UTF-8",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });
  response.flushHeaders();
  const agent = url.pathname.includes("manus");
  const chunks = agent
    ? [
        "这里是测试服务。建议先明确目标，再按阶段练习。",
        "\n\n每天安排学习和复盘，周末检查进展。",
        "结合检查结果调整下一周的安排。",
      ]
    : [
        "你好，",
        "这里是测试服务。",
        "\n这是逐步到达的流式回复。",
        "中文显示正常。",
      ];
  let index = 0;
  const interval = setInterval(
    () => {
      const chunk = chunks[index++ % chunks.length];
      response.write(
        `${agent ? "event: answer\n" : ""}data: ${chunk.replaceAll("\n", "\ndata: ")}\r\n\r\n`,
      );
      if (message !== "测试停止生成" && index >= chunks.length) {
        clearInterval(interval);
        if (agent) response.write("event: done\ndata: [DONE]\n\n");
        response.end();
      }
    },
    message === "测试停止生成" ? 400 : 220,
  );
  response.on("close", () => clearInterval(interval));
});

server.listen(Number(process.env.PORT || 8123), () =>
  console.log("TEST FIXTURE ONLY — SSE server ready"),
);
process.on("SIGTERM", () => server.close());
