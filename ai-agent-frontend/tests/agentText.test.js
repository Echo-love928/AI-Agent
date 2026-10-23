import test from "node:test";
import assert from "node:assert/strict";
import { formatAgentText } from "../src/lib/agentText.js";

const source = JSON.stringify({
  position: 1,
  title: "网页标题",
  snippet: "第三个月的学习...",
  link: "https://example.com",
  thumbnail: "https://example.com/a.png",
});

test("removes internal English and Chinese step prefixes from an ordinary answer", () => {
  const answer = "请告诉我学习目标和每天可用的时间。";
  assert.equal(formatAgentText(`Step 1: ${answer}`), answer);
  assert.equal(formatAgentText(`第 1 步：${answer}`), answer);
  assert.equal(formatAgentText(`第一步：${answer}`), answer);
});

test("does not disguise tool search snippets as a coherent answer", () => {
  assert.equal(
    formatAgentText(`Step 1: 工具 searchWeb 完成了它的任务！结果: ${source}`),
    "",
  );
  assert.equal(
    formatAgentText(`第 1 步：搜索结果：\n网页标题\n第三个月的学习...`),
    "",
  );
  assert.equal(formatAgentText(source), "");
});

test("uses the final legacy answer without mixing tool reports into it", () => {
  const answer =
    "建议用三个月分阶段练习，每天预留一小时。\n先建立基础，再练习工作场景，最后通过模拟会议检查进展。";
  assert.equal(
    formatAgentText(
      `Step 1: 工具 searchWeb 完成了它的任务！结果: ${source}\n\nStep 2: ${answer}\n\nStep 3: 工具 doTerminate 完成了它的任务！结果: 任务结束`,
    ),
    answer,
  );
});

test("preserves all meaningful plan sections rather than retaining only the last", () => {
  const text =
    "第一步：明确商务英语目标。\n第二步：安排每天的听说练习。\n第三步：每周复盘。";
  assert.equal(
    formatAgentText(text),
    "明确商务英语目标。\n安排每天的听说练习。\n每周复盘。",
  );
});

test("keeps human numbered schedules, punctuation, and numerical intervals", () => {
  const text =
    "1. 第一个月：建立基础。\n2. 第二个月：练习场景。\n3. 第三个月：模拟会议。\n强度在 [0,1]，集合 {a,b} 是示例。";
  assert.equal(formatAgentText(text), text);
});

test("keeps Markdown link labels and normal prose without exposing addresses", () => {
  const text =
    "## 学习安排\n**每天练习**，可参考[学习材料](https://example.com)。\n![示意图](https://example.com/a.png)\n最后检查进展。";
  assert.equal(
    formatAgentText(text),
    "学习安排\n每天练习，可参考学习材料。\n\n最后检查进展。",
  );
});

test("supports a complete structured final answer but hides API search envelopes", () => {
  assert.equal(
    formatAgentText(JSON.stringify({ answer: "每天先听，再说，最后复盘。" })),
    "每天先听，再说，最后复盘。",
  );
  assert.equal(
    formatAgentText(
      JSON.stringify({
        organic_results: [{ title: "标题", snippet: "摘要..." }],
      }),
    ),
    "",
  );
});

test("hides incomplete raw search JSON rather than briefly showing it", () => {
  assert.equal(
    formatAgentText(
      'Step 1: 工具 searchWeb 完成了它的任务！结果: {"position":1,"title":"学习',
    ),
    "",
  );
});

test("keeps complete natural-language answers and whitespace between paragraphs", () => {
  const answer = "建议采用三个阶段。\n\n每天安排听说练习，周末检查进展。";
  assert.equal(formatAgentText(answer), answer);
  assert.equal(formatAgentText(""), "");
});
