/** Incremental SSE parser: handles UTF-8 externally, split CRLF, comments and multiline data. */
export function createSseParser(onEvent) {
  let buffer = "";
  let data = [];
  let event = "";
  let firstLine = true;

  function line(value) {
    if (firstLine) {
      value = value.replace(/^\uFEFF/, "");
      firstLine = false;
    }
    if (value === "") {
      if (data.length)
        onEvent({ event: event || "message", data: data.join("\n") });
      data = [];
      event = "";
      return;
    }
    if (value.startsWith(":")) return;
    const colon = value.indexOf(":");
    const field = colon < 0 ? value : value.slice(0, colon);
    let content = colon < 0 ? "" : value.slice(colon + 1);
    if (content.startsWith(" ")) content = content.slice(1);
    if (field === "data") data.push(content);
    if (field === "event") event = content;
  }

  return {
    feed(chunk) {
      buffer += chunk;
      let position = 0;
      for (let index = 0; index < buffer.length; index++) {
        const char = buffer[index];
        if (char !== "\r" && char !== "\n") continue;
        if (char === "\r" && index === buffer.length - 1) break;
        line(buffer.slice(position, index));
        if (char === "\r" && buffer[index + 1] === "\n") index++;
        position = index + 1;
      }
      buffer = buffer.slice(position);
    },
    end() {
      // Spring may close directly after its last data line; retain the final content.
      if (buffer) line(buffer.replace(/\r$/, ""));
      buffer = "";
      line("");
    },
  };
}

export async function consumeSse(
  stream,
  { signal, onChunk, separateEvents = false, requireDone = false },
) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let complete = false;
  let received = false;
  const parser = createSseParser(({ event, data }) => {
    if (complete) return;
    if (event === "error")
      throw new Error(data || "服务返回错误，请稍后重试。");
    if (data.trim() === "[DONE]" || event === "done" || event === "complete") {
      complete = true;
      return;
    }
    if (event === "status" || event === "progress") return;
    // Final-answer tokens must be joined exactly, without invented whitespace.
    onChunk(
      `${separateEvents && received && event !== "answer" ? "\n\n" : ""}${data}`,
      { event },
    );
    received = true;
  });
  const abort = () => {
    void reader.cancel().catch(() => {});
  };
  signal?.addEventListener("abort", abort, { once: true });
  try {
    if (signal?.aborted) throw new DOMException("Aborted", "AbortError");
    while (!complete) {
      const { value, done } = await reader.read();
      if (signal?.aborted) throw new DOMException("Aborted", "AbortError");
      if (done) break;
      parser.feed(decoder.decode(value, { stream: true }));
    }
    parser.feed(decoder.decode());
    parser.end();
    if (requireDone && !complete)
      throw new Error("回答连接提前结束，未收到完整回答，请重试。");
    if (!received) throw new Error("未收到回复，请重新发送。");
  } finally {
    signal?.removeEventListener("abort", abort);
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
