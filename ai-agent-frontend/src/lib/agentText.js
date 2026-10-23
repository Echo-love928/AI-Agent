// Tool payloads stay internal. Prepare final-answer text without execution
// prefixes, and never render model output as HTML.
const TEXT_FIELDS = [
  "title",
  "name",
  "snippet",
  "description",
  "summary",
  "content",
  "text",
  "answer",
  "message",
  "result",
  "output",
  "error",
  "reason",
  "recommendation",
];
const CONTAINER_FIELDS = [
  "organic_results",
  "results",
  "items",
  "data",
  "steps",
];
const EMPTY_RESULT = "已收到工具结果，暂无可展示的文字说明。";
const INVALID_RESULT = "工具返回的内容不完整，暂时无法整理为文字。";

function readableJson(value, depth = 0) {
  if (depth > 12 || value == null) return "";
  if (typeof value === "string") {
    const text = value.trim();
    if (isJsonStart(text, 0)) {
      try {
        return readableJson(JSON.parse(text), depth + 1);
      } catch {
        return INVALID_RESULT;
      }
    }
    return value;
  }
  if (Array.isArray(value)) {
    return value
      .map((item) => readableJson(item, depth + 1))
      .filter(Boolean)
      .join("\n\n");
  }
  if (typeof value !== "object") return "";

  const lines = [];
  for (const key of [...TEXT_FIELDS, ...CONTAINER_FIELDS]) {
    const text = readableJson(value[key], depth + 1);
    if (text && !lines.includes(text)) lines.push(text);
  }
  // Preserve human-readable Chinese fields without exposing English API keys.
  for (const [key, item] of Object.entries(value)) {
    if (
      !/[\u4e00-\u9fff]/u.test(key) ||
      /链接|网址|图片|缩略图|地址|标识/u.test(key)
    )
      continue;
    if (typeof item === "number" || typeof item === "string")
      lines.push(`${key}：${item}`);
  }
  return lines.join("\n");
}

function isJsonStart(text, index) {
  const rest = text.slice(index);
  return (
    /^\{\s*"[^"\r\n]+"\s*:/.test(rest) || /^\[\s*(?:\{|"|\[\s*\{)/.test(rest)
  );
}

function readJson(text, start) {
  const stack = [];
  let quoted = false;
  let escaped = false;
  for (let index = start; index < text.length; index++) {
    const char = text[index];
    if (quoted) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === '"') quoted = false;
      continue;
    }
    if (char === '"') {
      quoted = true;
      continue;
    }
    if (char === "{" || char === "[") stack.push(char);
    else if (char === "}" || char === "]") {
      const open = stack.pop();
      if ((open === "{" && char !== "}") || (open === "[" && char !== "]")) {
        return { end: index + 1, text: INVALID_RESULT };
      }
      if (!stack.length) {
        try {
          const value = JSON.parse(text.slice(start, index + 1));
          return { end: index + 1, text: readableJson(value) || EMPTY_RESULT };
        } catch {
          return { end: index + 1, text: INVALID_RESULT };
        }
      }
    }
  }
  // Do not briefly expose a half-written JSON payload while streaming.
  return { end: text.length, text: INVALID_RESULT };
}

function plainText(text) {
  return text
    .replace(/!\[[^\]]*\]\([^\n)]*\)/g, "")
    .replace(/\[([^\]\n]+)\]\(<?(?:https?:\/\/|www\.)[^\n)]*\>?\)/gi, "$1")
    .replace(/<(?:https?:\/\/|www\.)[^>\s]+>/gi, "")
    .replace(/(?:https?:\/\/|www\.)[^\s<>"'，。；！？、（）【】]+/gi, "")
    .replace(/([。！？；])[，、]/g, "$1")
    .replace(
      /^\s*(?:来源链接|参考链接|图片地址|链接|网址|URL)\s*[:：]\s*$/gim,
      "",
    )
    .replace(/^\s*```[^\n]*$/gm, "")
    .replace(/^\s{0,3}#{1,6}\s+/gm, "")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/__([^_]+)__/g, "$1")
    .replace(/`([^`]+)`/g, "$1")
    .replace(
      /(^|\n)[ \t]*(?:Step[ \t]+\d+|第[ \t]*[\d一二三四五六七八九十百]+[ \t]*步)[ \t]*[:：][ \t]*/gi,
      "$1",
    )
    .replace(
      /工具\s+([\w.-]+)\s+完成了它的任务[！!]?\s*结果\s*[:：]\s*/g,
      "",
    )
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function formatAgentText(content) {
  if (!content) return "";
  // Old servers sent each tool execution as a step. These are evidence for the
  // model, not answers: do not turn disconnected snippets into apparent advice.
  const chunks = String(content).split(
    /\n(?=[ \t]*(?:Step[ \t]+\d+|第[ \t]*\d+[ \t]*步)[ \t]*[:：])/i,
  );
  const candidates = chunks.filter((chunk) => !isInternalReport(chunk));
  if (!candidates.length) return "";
  const answer = chunks.some(isInternalReport)
    ? candidates.at(-1)
    : chunks.join("\n");
  const text = answer.replace(/^\s*```(?:json)?\s*$/gim, "");
  const pieces = [];
  let cursor = 0;
  for (let index = 0; index < text.length; index++) {
    if (
      (text[index] !== "{" && text[index] !== "[") ||
      !isJsonStart(text, index)
    )
      continue;
    pieces.push(text.slice(cursor, index));
    const result = readJson(text, index);
    pieces.push(result.text);
    cursor = result.end;
    index = result.end - 1;
    // The search tool returns comma-separated JSON objects without an array.
    const separator = /^\s*,\s*(?=[{[])/.exec(text.slice(cursor));
    if (separator) {
      pieces.push("\n\n");
      cursor += separator[0].length;
      index = cursor - 1;
    }
  }
  pieces.push(text.slice(cursor));
  return plainText(pieces.join(""));
}

function isInternalReport(text) {
  const body = text
    .replace(
      /^[ \t]*(?:Step[ \t]+\d+|第[ \t]*[\d一二三四五六七八九十百]+[ \t]*步)[ \t]*[:：][ \t]*/i,
      "",
    )
    .trim();
  return (
    /^(?:工具\s+[\w.-]+\s+完成了它的任务|搜索结果\s*[:：]|执行结果\s*[:：]|执行结束\s*[:：]|任务结束\s*$)/.test(
      body,
    ) ||
    (/^[{[]/.test(body) &&
      /"(?:snippet|organic_results|thumbnail|position|displayed_link)"\s*:/.test(
        body,
      ))
  );
}
