import axios from "axios";
import { consumeSse } from "./sse";
import { createAgentAnswerHandler } from "./agentConversation";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  adapter: "fetch",
  timeout: 0,
});

export async function streamChat({
  kind,
  message,
  chatId,
  history = [],
  signal,
  onChunk,
  onReplace,
}) {
  const path =
    kind === "mind"
      ? import.meta.env.VITE_MIND_CHAT_PATH || "/ai/mind_app/chat/sse"
      : import.meta.env.VITE_MANUS_CHAT_PATH || "/ai/manus/chat";
  const config = {
    responseType: "stream",
    headers: { Accept: "text/event-stream" },
    signal,
  };
  const response =
    kind === "mind"
      ? await api.get(path, { ...config, params: { message, chatId } })
      : await api.post(path, { message, history }, config);
  if (!response.headers["content-type"]?.includes("text/event-stream")) {
    await response.data?.cancel?.();
    throw new Error("接口未返回 SSE 数据流，请检查接口地址和后端配置。");
  }
  const answer =
    kind === "agent"
      ? createAgentAnswerHandler({ append: onChunk, replace: onReplace })
      : null;
  await consumeSse(response.data, {
    signal,
    onChunk: answer ? answer.onChunk : onChunk,
    requireDone: kind === "agent",
  });
  answer?.assertComplete();
}

export function friendlyError(error) {
  const status = error.response?.status;
  if (status === 404) return "未找到聊天接口，请检查 .env.local 中的接口路径。";
  if (status === 405) return "智能体接口需要更新，请重启更新后的后端服务再试。";
  if (status >= 500) return "AI 服务暂时不可用，请检查后端服务后重试。";
  if (error.code === "ERR_NETWORK" || error instanceof TypeError) {
    return "无法连接 AI 服务，请确认后端已在 localhost:8123 启动。";
  }
  return error.message || "连接中断，请稍后重试。";
}
