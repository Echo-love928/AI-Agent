import test from "node:test";
import assert from "node:assert/strict";
import { createSseParser, consumeSse } from "../src/lib/sse.js";

function bytesStream(text, chunkSize = 1) {
  const bytes = new TextEncoder().encode(text);
  return new ReadableStream({
    start(controller) {
      for (let index = 0; index < bytes.length; index += chunkSize)
        controller.enqueue(bytes.slice(index, index + chunkSize));
      controller.close();
    },
  });
}

test("parses CRLF split at every character, BOM, comments and multiline data", () => {
  const events = [];
  const parser = createSseParser((event) => events.push(event));
  for (const char of "\uFEFF: heartbeat\r\nevent: progress\r\ndata: hello\r\ndata: world\r\n\r\ndata:  trailing space \n\n")
    parser.feed(char);
  parser.end();
  assert.deepEqual(events, [
    { event: "progress", data: "hello\nworld" },
    { event: "message", data: " trailing space " },
  ]);
});

test("handles lone CR, unknown fields, data without colon and final unframed event", () => {
  const events = [];
  const parser = createSseParser((event) => events.push(event));
  parser.feed("id: 1\rretry: 1000\rdata: first\r\rdata\n\ndata: last");
  parser.end();
  assert.deepEqual(
    events.map((item) => item.data),
    ["first", "", "last"],
  );
});

test("decodes Chinese and emoji across individual UTF-8 bytes without adding token spaces", async () => {
  const chunks = [];
  await consumeSse(bytesStream("data: 你好\n\ndata: ，世界🌞\n\n"), {
    onChunk: (chunk) => chunks.push(chunk),
  });
  assert.equal(chunks.join(""), "你好，世界🌞");
});

test("separates Manus step events, preserving newlines within each step", async () => {
  const chunks = [];
  await consumeSse(
    bytesStream(
      "data: Step 1: 规划\ndata: 检查目标\n\ndata: Step 2: 完成\n\n",
      7,
    ),
    {
      separateEvents: true,
      onChunk: (chunk) => chunks.push(chunk),
    },
  );
  assert.equal(chunks.join(""), "Step 1: 规划\n检查目标\n\nStep 2: 完成");
});

test("stops at DONE and ignores subsequent content", async () => {
  const chunks = [];
  await consumeSse(
    bytesStream("data: hello\n\ndata: [DONE]\n\ndata: ignored\n\n", 100),
    { onChunk: (chunk) => chunks.push(chunk) },
  );
  assert.equal(chunks.join(""), "hello");
});

test("named completion event closes reader without waiting for server disconnect", async () => {
  let cancelled = false;
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(
        new TextEncoder().encode(
          "data: hello\n\nevent: complete\ndata: done\n\n",
        ),
      );
    },
    cancel() {
      cancelled = true;
    },
  });
  await consumeSse(stream, { onChunk() {} });
  assert.equal(cancelled, true);
});

test("named error event fails with the server message", async () => {
  await assert.rejects(
    () =>
      consumeSse(bytesStream("event: error\ndata: service unavailable\n\n"), {
        onChunk() {},
      }),
    /service unavailable/,
  );
});

test("empty stream fails rather than showing a successful reply", async () => {
  await assert.rejects(
    () => consumeSse(bytesStream(": heartbeat\n\n"), { onChunk() {} }),
    /未收到回复/,
  );
});

test("aborting a pending read cancels the stream and releases its lock", async () => {
  const controller = new AbortController();
  let cancelled = false;
  const stream = new ReadableStream({
    cancel() {
      cancelled = true;
    },
  });
  const result = consumeSse(stream, {
    signal: controller.signal,
    onChunk() {},
  });
  controller.abort();
  await assert.rejects(result, { name: "AbortError" });
  assert.equal(cancelled, true);
  assert.equal(stream.locked, false);
});

test("already aborted requests release the stream reader", async () => {
  const controller = new AbortController();
  controller.abort();
  const stream = bytesStream("data: ignored\n\n");
  await assert.rejects(
    () => consumeSse(stream, { signal: controller.signal, onChunk() {} }),
    { name: "AbortError" },
  );
  assert.equal(stream.locked, false);
});
