import test from "node:test";
import assert from "node:assert/strict";
import { consumeSse } from "../src/lib/sse.js";
import {
  buildAgentHistory,
  createAgentAnswerHandler,
} from "../src/lib/agentConversation.js";

function stream(text) {
  const bytes = new TextEncoder().encode(text);
  return new ReadableStream({
    start(controller) {
      for (let index = 0; index < bytes.length; index += 3)
        controller.enqueue(bytes.slice(index, index + 3));
      controller.close();
    },
  });
}

async function readAnswer(text) {
  let content = "";
  const handler = createAgentAnswerHandler({
    append: (chunk) => {
      content += chunk;
    },
    replace: (text) => {
      content = text;
    },
  });
  await consumeSse(stream(text), {
    onChunk: handler.onChunk,
    separateEvents: true,
    requireDone: true,
  });
  handler.assertComplete();
  return content;
}

test("joins final-answer UTF-8 tokens exactly without introducing blank lines", async () => {
  const text = await readAnswer(
    "event: status\ndata: 正在检索\n\nevent: answer\ndata: 每天\n\nevent: answer\ndata: 学习 1\n\nevent: answer\ndata:  小时。\n\nevent: done\ndata: [DONE]\n\n",
  );
  assert.equal(text, "每天学习 1 小时。");
});

test("preserves paragraph breaks supplied by the answer itself", async () => {
  const text = await readAnswer(
    "event: answer\ndata: 先建立基础。\n\nevent: answer\ndata:\ndata:\ndata: 再练习工作场景。\n\nevent: done\ndata: [DONE]\n\n",
  );
  assert.equal(text, "先建立基础。\n\n再练习工作场景。");
});

test("a missing completion marker is an error rather than a successful partial answer", async () => {
  await assert.rejects(
    () => readAnswer("event: answer\ndata: 尚未写完\n\n"),
    /未收到完整回答/,
  );
});

test("a generation error after partial output stays an error", async () => {
  await assert.rejects(
    () =>
      readAnswer(
        "event: answer\ndata: 尚未写完\n\nevent: error\ndata: 本次未能生成完整回答\n\n",
      ),
    /未能生成完整回答/,
  );
});

test("legacy tool reports are ignored and only the final answer is retained", async () => {
  const result = await readAnswer(
    'data: Step 1: 工具 searchWeb 完成了它的任务！结果: {"snippet":"摘要..."}\n\ndata: Step 2: 每天练习，周末复盘。\n\nevent: done\ndata: [DONE]\n\n',
  );
  assert.equal(result.trim(), "每天练习，周末复盘。");
});

test("tool records alone cannot be treated as a complete answer", async () => {
  await assert.rejects(
    () =>
      readAnswer(
        'data: Step 1: 工具 searchWeb 完成了它的任务！结果: {"snippet":"摘要..."}\n\nevent: done\ndata: [DONE]\n\n',
      ),
    /未收到整理后的完整回答/,
  );
});

test("conversation history contains complete exchanges and excludes stopped, failed and pending answers", () => {
  const messages = [
    { role: "user", content: "我想学习英语。" },
    {
      role: "assistant",
      content: "请告诉我目标和每天的时间。",
      status: "complete",
    },
    { role: "user", content: "错误问题" },
    { role: "assistant", content: "部分输出", status: "error" },
    { role: "user", content: "停止的问题" },
    { role: "assistant", content: "未完成", status: "stopped" },
    { role: "user", content: "三个月，每天一小时。" },
    { role: "assistant", content: "", status: "streaming" },
  ];
  assert.deepEqual(buildAgentHistory(messages), [
    { role: "user", content: "我想学习英语。" },
    { role: "assistant", content: "请告诉我目标和每天的时间。" },
  ]);
});

test("each session supplies only its own history", () => {
  assert.deepEqual(buildAgentHistory([]), []);
  const history = buildAgentHistory([
    { role: "user", content: "当前会话问题" },
    { role: "assistant", content: "当前会话回答", status: "complete" },
  ]);
  assert.equal(history.length, 2);
  assert.doesNotMatch(JSON.stringify(history), /其他会话/);
});

test("history retains at most ten recent complete exchanges without slicing individual answers", () => {
  const messages = Array.from({ length: 12 }, (_, i) => [
    { role: "user", content: `问题${i}` },
    { role: "assistant", content: `回答${i}`, status: "complete" },
  ]).flat();
  const history = buildAgentHistory(messages);
  assert.equal(history.length, 20);
  assert.equal(history[0].content, "问题2");
  assert.equal(history.at(-1).content, "回答11");
});

test("history size limit drops whole old exchanges instead of truncating paragraphs", () => {
  const messages = Array.from({ length: 5 }, () => [
    { role: "user", content: "问".repeat(6000) },
    { role: "assistant", content: "答".repeat(12000), status: "complete" },
  ]).flat();
  const history = buildAgentHistory(messages);
  assert.equal(history.length, 6);
  assert.equal(history.at(-1).content.length, 12000);
});
