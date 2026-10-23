import { formatAgentText } from "./agentText.js";

/** Send only complete exchanges from this session, never internal tool output. */
export function buildAgentHistory(messages) {
  const pairs = [];
  for (let index = 0; index < messages.length - 1; index++) {
    const user = messages[index];
    const assistant = messages[index + 1];
    if (
      user.role !== "user" ||
      assistant.role !== "assistant" ||
      assistant.status !== "complete"
    )
      continue;
    const answer = formatAgentText(assistant.content);
    if (
      !user.content?.trim() ||
      !answer ||
      user.content.length > 12000 ||
      answer.length > 12000
    )
      continue;
    pairs.push([
      { role: "user", content: user.content },
      { role: "assistant", content: answer },
    ]);
    index++;
  }
  const history = [];
  let size = 0;
  for (const pair of pairs.slice(-10).reverse()) {
    const length = pair[0].content.length + pair[1].content.length;
    if (size + length > 60000) break;
    history.unshift(...pair);
    size += length;
  }
  return history;
}

/** The answer protocol appends tokens; legacy complete-step messages replace. */
export function createAgentAnswerHandler({ append, replace }) {
  let received = false;
  return {
    onChunk(chunk, { event } = {}) {
      if (event === "answer") {
        if (chunk.trim()) received = true;
        append(chunk);
      } else {
        if (event && event !== "message") return;
        const text = formatAgentText(chunk);
        if (!text) return;
        received = true;
        replace(text);
      }
    },
    assertComplete() {
      if (!received) throw new Error("未收到整理后的完整回答，请重试。");
    },
  };
}
