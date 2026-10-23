<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import AppIcon from "../components/AppIcon.vue";
import IsleArtwork from "../components/IsleArtwork.vue";
import { useConversations } from "../composables/useConversations";
import { formatAgentText } from "../lib/agentText";

const props = defineProps({ kind: { type: String, required: true } });
const route = useRoute();
const {
  activeSession,
  activeIds,
  ensureSession,
  createSession,
  sendMessage,
  stopSession,
  retryMessage,
} = useConversations();
ensureSession(props.kind);
const session = computed(() => activeSession(props.kind));
const isMind = computed(() => props.kind === "mind");
function messageText(message) {
  return !isMind.value && message.role === "assistant"
    ? formatAgentText(message.content)
    : message.content;
}
const config = computed(() =>
  isMind.value
    ? {
        name: "AI 心理树洞",
        subtitle: "在这里，每一种心情都有回声",
        icon: "leaf",
        title: "慢慢说，我在听。",
        description:
          "不需要组织好语言，也不用担心被评判。\n从此刻的感受说起，就很好。",
        placeholder: "今天，有什么想说给我听的？",
        prompts: [
          "最近有点累，想找人聊聊",
          "总是想太多，怎么放松自己？",
          "想记录今天的一件开心小事",
        ],
      }
    : {
        name: "AI 智能体",
        subtitle: "让想法发生，让行动更简单",
        icon: "bot",
        title: "有想法？一起实现。",
        description:
          "告诉我你的目标和背景，我会结合对话与所需资料，\n整理成清晰、完整、可执行的答复。",
        placeholder: "描述你的目标，把任务交给我…",
        prompts: [
          "帮我制定一个循序渐进的学习计划",
          "调研 AI 智能体的常见应用场景",
          "帮我规划一份周末出行方案",
        ],
      },
);
const draft = ref(
  typeof route.query.draft === "string" ? route.query.draft : "",
);
const messagesArea = ref(null);
const textarea = ref(null);
const following = ref(true);
const copiedId = ref("");
const copyError = ref("");
let copyTimer;
const assistantStatus = computed(() =>
  session.value.error
    ? "连接遇到问题"
    : session.value.busy
      ? isMind.value
        ? "正在认真倾听…"
        : "正在整理完整回答…"
      : session.value.messages.length
        ? "本次回复已结束"
        : "随时开始一段对话",
);
const time = (timestamp) =>
  new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(timestamp);

function scrollDown() {
  if (messagesArea.value)
    messagesArea.value.scrollTop = messagesArea.value.scrollHeight;
}
function onScroll() {
  const element = messagesArea.value;
  following.value =
    element.scrollHeight - element.scrollTop - element.clientHeight < 100;
}
watch(
  () => [
    session.value.messages.at(-1)?.content,
    session.value.busy,
    session.value.error,
  ],
  async () => {
    if (following.value) {
      await nextTick();
      scrollDown();
    }
  },
);
watch(
  () => activeIds.value[props.kind],
  async () => {
    draft.value = "";
    copyError.value = "";
    following.value = true;
    await nextTick();
    scrollDown();
  },
);
watch(draft, async () => {
  await nextTick();
  if (textarea.value) {
    textarea.value.style.height = "auto";
    textarea.value.style.height = `${Math.min(textarea.value.scrollHeight, 150)}px`;
  }
});
async function submit() {
  if (!draft.value.trim() || session.value.busy) return;
  const message = draft.value;
  draft.value = "";
  following.value = true;
  await sendMessage(session.value, message);
  await nextTick();
  textarea.value?.focus();
}
function handleEnter(event) {
  if (
    event.key === "Enter" &&
    !event.shiftKey &&
    !event.isComposing &&
    event.keyCode !== 229
  ) {
    event.preventDefault();
    void submit();
  }
}
function usePrompt(prompt) {
  draft.value = prompt;
  nextTick(() => textarea.value?.focus());
}
function newConversation() {
  createSession(props.kind);
  nextTick(() => textarea.value?.focus());
}
async function copy(text, id) {
  try {
    await navigator.clipboard.writeText(text);
    copiedId.value = id;
    copyError.value = "";
    clearTimeout(copyTimer);
    copyTimer = setTimeout(() => {
      copiedId.value = "";
    }, 2200);
  } catch {
    copyError.value = "复制失败，请选择文字后手动复制。";
  }
}
onBeforeUnmount(() => clearTimeout(copyTimer));
</script>

<template>
  <div
    class="chat-page page-enter"
    :class="isMind ? 'chat-mind' : 'chat-agent'"
  >
    <header class="chat-header">
      <div class="chat-identity">
        <span class="chat-app-icon"
          ><AppIcon :name="config.icon" :size="25"
        /></span>
        <div>
          <h1>
            {{ config.name
            }}<span v-if="!isMind" class="manus-label">MANUS</span>
          </h1>
          <p>{{ config.subtitle }}</p>
        </div>
      </div>
      <button class="new-chat-button" @click="newConversation">
        <AppIcon name="plus" :size="18" /><span>开启新对话</span>
      </button>
    </header>
    <div class="chat-body">
      <section class="chat-room" aria-label="聊天会话">
        <div class="room-topline">
          <span class="room-status" role="status"
            ><span
              :class="{ pulsing: session.busy, 'error-dot': session.error }"
            ></span
            >{{ assistantStatus }}</span
          ><button
            class="session-id"
            :title="`会话 ID：${session.id}，点击复制`"
            :aria-label="`复制会话 ID ${session.id}`"
            @click="copy(session.id, 'room')"
          >
            <span>会话 {{ session.id.slice(0, 8) }}</span
            ><AppIcon
              :name="copiedId === 'room' ? 'check' : 'copy'"
              :size="13"
            />
          </button>
        </div>
        <div
          ref="messagesArea"
          class="messages-area"
          @scroll.passive="onScroll"
        >
          <div v-if="!session.messages.length" class="chat-welcome">
            <span class="welcome-orbit"
              ><AppIcon :name="config.icon" :size="38"
            /></span>
            <h2>{{ config.title }}</h2>
            <p class="welcome-description">{{ config.description }}</p>
            <div class="starter-prompts">
              <button
                v-for="prompt in config.prompts"
                :key="prompt"
                @click="usePrompt(prompt)"
              >
                <AppIcon
                  :name="isMind ? 'chat' : 'sparkles'"
                  :size="16"
                /><span>{{ prompt }}</span
                ><AppIcon name="external" :size="15" />
              </button>
            </div>
          </div>
          <template v-else>
            <div class="conversation-divider">
              <span>新的对话，新的开始</span>
            </div>
            <article
              v-for="message in session.messages"
              :key="message.id"
              class="message-row"
              :class="`message-${message.role}`"
              :aria-label="message.role === 'user' ? '你的消息' : 'AI 回复'"
            >
              <span class="message-avatar"
                ><AppIcon
                  v-if="message.role === 'assistant'"
                  :name="config.icon"
                  :size="21"
                /><span v-else>我</span></span
              >
              <div class="message-main">
                <div class="message-meta">
                  <span>{{
                    message.role === "user"
                      ? "你"
                      : isMind
                        ? "晴屿 · 树洞"
                        : "Manus"
                  }}</span
                  ><time>{{ time(message.time) }}</time>
                </div>
                <div class="message-bubble">
                  <div v-if="messageText(message)" class="message-text">
                    {{ messageText(message) }}
                  </div>
                  <div
                    v-else-if="message.status === 'streaming'"
                    class="typing-indicator"
                    aria-label="等待 AI 回复"
                  >
                    <span></span><span></span><span></span>
                  </div>
                  <p v-else class="empty-answer">
                    {{
                      message.status === "stopped"
                        ? "已停止接收回复。"
                        : "这次没有收到回复。"
                    }}
                  </p>
                  <span
                    v-if="
                      message.status === 'streaming' && messageText(message)
                    "
                    class="stream-cursor"
                    aria-hidden="true"
                  ></span>
                </div>
                <div
                  v-if="
                    message.role === 'assistant' &&
                    message.status !== 'streaming'
                  "
                  class="message-tools"
                >
                  <span v-if="message.status === 'stopped'">已停止</span
                  ><span v-else-if="message.status === 'error'">回复未完成</span
                  ><button
                    v-if="messageText(message)"
                    :aria-label="
                      copiedId === message.id ? '已复制回复' : '复制回复'
                    "
                    @click="copy(messageText(message), message.id)"
                  >
                    <AppIcon
                      :name="copiedId === message.id ? 'check' : 'copy'"
                      :size="14"
                    />{{ copiedId === message.id ? "已复制" : "复制" }}
                  </button>
                </div>
              </div>
            </article>
          </template>
        </div>
        <div class="composer-area">
          <button
            v-if="!following && session.messages.length"
            class="jump-to-bottom"
            @click="
              following = true;
              scrollDown();
            "
          >
            <AppIcon name="down" :size="15" />回到最新消息
          </button>
          <div v-if="session.error" class="chat-error" role="alert">
            <span>{{ session.error }}</span
            ><button
              :disabled="session.busy"
              @click="
                following = true;
                retryMessage(session);
              "
            >
              重新尝试<AppIcon name="arrow" :size="14" />
            </button>
          </div>
          <p v-if="copyError" class="copy-error" role="status">
            {{ copyError }}
          </p>
          <form class="composer" @submit.prevent="submit">
            <label class="sr-only" for="chat-input">{{
              config.placeholder
            }}</label
            ><textarea
              id="chat-input"
              ref="textarea"
              v-model="draft"
              rows="2"
              maxlength="6000"
              :placeholder="config.placeholder"
              :aria-describedby="
                session.error ? 'input-hint error-help' : 'input-hint'
              "
              @keydown="handleEnter"
            ></textarea>
            <div class="composer-toolbar">
              <span class="composer-mode"
                ><AppIcon :name="isMind ? 'heart' : 'zap'" :size="15" />{{
                  isMind ? "安心倾诉，慢慢来" : "任务规划 · 工具执行"
                }}</span
              ><span class="composer-count">{{
                draft.length ? `${draft.length} / 6000` : ""
              }}</span
              ><button
                v-if="session.busy"
                type="button"
                class="send-button stop-button"
                aria-label="停止生成"
                title="停止生成"
                @click="stopSession(session.id)"
              >
                <AppIcon name="stop" :size="17" /></button
              ><button
                v-else
                class="send-button"
                type="submit"
                :disabled="!draft.trim()"
                aria-label="发送消息"
                title="发送消息"
              >
                <AppIcon name="send" :size="18" />
              </button>
            </div>
          </form>
          <div id="input-hint" class="input-hint">
            <span
              >Enter 发送<span class="desktop-hint">
                · Shift + Enter 换行</span
              ></span
            ><span>{{
              isMind
                ? "AI 陪伴不能替代专业心理咨询"
                : "结合当前对话，整理完整回答"
            }}</span>
          </div>
          <span v-if="session.error" id="error-help" class="sr-only">{{
            session.error
          }}</span>
        </div>
      </section>
      <aside class="chat-companion">
        <div class="companion-art">
          <IsleArtwork :variant="isMind ? 'mind' : 'agent'" />
        </div>
        <h2>
          {{
            isMind
              ? "在自己的节奏里，\n慢慢晴朗。"
              : "每一个大想法，\n都从小小一步开始。"
          }}
        </h2>
        <p class="companion-copy">
          {{
            isMind
              ? "快乐、失落、困惑或平静，\n都可以成为今天的开场白。"
              : "目标越具体，行动越清晰。\n把背景、要求和期待一起告诉我。"
          }}
        </p>
        <div class="companion-divider"></div>
        <div class="companion-tip">
          <AppIcon :name="isMind ? 'shield' : 'sparkles'" :size="19" />
          <div>
            <h3>{{ isMind ? "留一点空间给自己" : "试着这样描述任务" }}</h3>
            <p>
              {{
                isMind
                  ? "对话仅在本次页面中保留。你可以随时开启新对话，换一个起点。"
                  : "“帮我规划一次两天的杭州旅行，预算 1500 元，偏好自然风景。”"
              }}
            </p>
          </div>
        </div>
        <RouterLink to="/" class="back-to-apps"
          ><AppIcon name="back" :size="15" />探索其他应用</RouterLink
        >
      </aside>
    </div>
  </div>
</template>
