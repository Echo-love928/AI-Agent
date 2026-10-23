import { computed, ref } from "vue";
import { friendlyError, streamChat } from "../lib/api";
import { buildAgentHistory } from "../lib/agentConversation";

// Memory-only by design: private conversations are never written to browser storage.
const sessions = ref([]);
const activeIds = ref({ mind: null, agent: null });
const pending = new Map();

export function useConversations() {
  function createSession(kind) {
    const session = {
      id: crypto.randomUUID(),
      kind,
      title: kind === "mind" ? "新的一次倾诉" : "新的灵感任务",
      messages: [],
      createdAt: Date.now(),
      busy: false,
      error: "",
    };
    sessions.value.unshift(session);
    activeIds.value[kind] = session.id;
    return sessions.value[0];
  }
  function activeSession(kind) {
    return sessions.value.find(
      (session) => session.id === activeIds.value[kind],
    );
  }
  function ensureSession(kind) {
    return activeSession(kind) || createSession(kind);
  }
  function selectSession(id) {
    const session = sessions.value.find((item) => item.id === id);
    if (session) activeIds.value[session.kind] = id;
    return session;
  }
  function stopSession(id) {
    pending.get(id)?.abort();
  }

  async function sendMessage(session, text, retry = false) {
    if (session.busy || !text.trim()) return;
    const history =
      session.kind === "agent" ? buildAgentHistory(session.messages) : [];
    if (!retry) {
      session.messages.push({
        id: crypto.randomUUID(),
        role: "user",
        content: text.trim(),
        time: Date.now(),
      });
      if (session.messages.filter((item) => item.role === "user").length === 1)
        session.title = text.trim().slice(0, 22);
    }
    session.error = "";
    session.busy = true;
    const controller = new AbortController();
    pending.set(session.id, controller);
    const id = crypto.randomUUID();
    session.messages.push({
      id,
      role: "assistant",
      content: "",
      time: Date.now(),
      status: "streaming",
    });
    const answer = session.messages.find((item) => item.id === id);
    try {
      await streamChat({
        kind: session.kind,
        message: text.trim(),
        chatId: session.id,
        history,
        signal: controller.signal,
        onChunk: (chunk) => {
          answer.content += chunk;
        },
        onReplace: (content) => {
          answer.content = content;
        },
      });
      answer.status = "complete";
    } catch (error) {
      if (controller.signal.aborted) answer.status = "stopped";
      else {
        answer.status = "error";
        session.error = friendlyError(error);
      }
    } finally {
      session.busy = false;
      pending.delete(session.id);
    }
  }

  async function retryMessage(session) {
    if (session.busy) return;
    const last = session.messages.at(-1);
    if (last?.role !== "assistant" || last.status !== "error") return;
    const prompt = session.messages.at(-2)?.content;
    if (!prompt) return;
    session.messages.pop();
    await sendMessage(session, prompt, true);
  }

  return {
    sessions,
    recentSessions: computed(() =>
      sessions.value.filter((item) => item.messages.length).slice(0, 8),
    ),
    activeIds,
    createSession,
    activeSession,
    ensureSession,
    selectSession,
    sendMessage,
    stopSession,
    retryMessage,
  };
}
