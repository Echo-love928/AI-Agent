<script setup>
import { computed, onBeforeUnmount, ref } from "vue";
import { useRouter } from "vue-router";
import AppIcon from "../components/AppIcon.vue";
import IsleArtwork from "../components/IsleArtwork.vue";

const router = useRouter();
const mood = ref("");
const moods = [
  { label: "心情不错", icon: "sun" },
  { label: "有点疲惫", icon: "wind" },
  { label: "想聊一聊", icon: "chat" },
  { label: "灵感满满", icon: "sparkles" },
];
const starters = [
  {
    label: "最近有点内耗",
    icon: "leaf",
    path: "/mind",
    draft: "最近总是想太多，有点内耗，想和你聊聊。",
  },
  {
    label: "记录一件开心小事",
    icon: "sun",
    path: "/mind",
    draft: "今天发生了一件让我开心的小事，想分享给你。",
  },
  {
    label: "制定一个学习计划",
    icon: "sparkles",
    path: "/agent",
    draft: "我想制定一个循序渐进的学习计划，请先帮我明确目标和可用时间。",
  },
];
const moodHint = computed(
  () =>
    ({
      心情不错: "把今天的小确幸，也分享给树洞吧。",
      有点疲惫: "先放慢一点，树洞愿意听你说。",
      想聊一聊: "不需要想好开场白，随时都可以开始。",
      灵感满满: "把想法交给智能体，一起迈出第一步。",
    })[mood.value] || "无论哪种心情，这里都有你的位置。",
);
const breatheDialog = ref(null);
const elapsed = ref(0);
const breathing = ref(false);
let timer;
const breathPhase = computed(() =>
  elapsed.value >= 60
    ? "完成了，慢慢回到当下"
    : ["轻轻吸气", "缓缓呼气"][Math.floor((elapsed.value % 10) / 5)],
);
function startBreathing() {
  elapsed.value = 0;
  breathing.value = true;
  breatheDialog.value.showModal();
  timer = setInterval(() => {
    elapsed.value++;
    if (elapsed.value >= 60) {
      clearInterval(timer);
      breathing.value = false;
    }
  }, 1000);
}
function stopBreathing() {
  clearInterval(timer);
  breathing.value = false;
}
function openMood() {
  if (mood.value === "灵感满满") router.push("/agent");
  else
    router.push({
      path: "/mind",
      query: mood.value ? { draft: `我今天${mood.value}，想和你聊聊。` } : {},
    });
}
onBeforeUnmount(stopBreathing);
</script>

<template>
  <div class="home-page page-enter">
    <section class="home-hero" aria-labelledby="hero-title">
      <div class="hero-copy">
        <div class="hero-eyebrow">
          <span class="tiny-sun"><AppIcon name="sun" :size="16" /></span
          >属于你的，一小片晴天
        </div>
        <h1 id="hero-title">
          心事，慢慢说。<br />想法，<span class="highlight-word">一起做</span
          ><span class="headline-period">。</span>
        </h1>
        <p class="hero-description">
          心里有点乱，或有件事不知从哪开始？<br />和晴屿聊聊感受，也把想法变成下一步。
        </p>
        <div class="hero-actions">
          <RouterLink to="/mind" class="hero-action hero-action-primary"
            ><AppIcon name="chat" :size="18" />聊聊我的心事<AppIcon
              name="arrow"
              :size="17"
          /></RouterLink>
          <RouterLink to="/agent" class="hero-action hero-action-secondary"
            ><AppIcon name="sparkles" :size="18" />让 AI 帮我做件事</RouterLink
          >
        </div>
        <nav class="hero-starters" aria-label="选择话题并预填聊天输入框">
          <span class="starters-label">试着聊聊</span>
          <RouterLink
            v-for="starter in starters"
            :key="starter.label"
            :to="{ path: starter.path, query: { draft: starter.draft } }"
            :title="`${starter.label}，进入后可编辑再发送`"
            ><AppIcon :name="starter.icon" :size="13" />{{
              starter.label
            }}</RouterLink
          >
        </nav>
      </div>
      <div class="hero-art-wrap">
        <IsleArtwork />
      </div>
    </section>
    <section class="apps-section" aria-labelledby="apps-title">
      <div class="section-heading">
        <div>
          <h2 id="apps-title">选一位伙伴，从一句话开始</h2>
          <p>想被倾听，或想把事情往前推进一点，都可以。</p>
        </div>
      </div>
      <div class="app-cards">
        <article class="application-card mind-card">
          <div class="card-topline">
            <span class="app-symbol"><AppIcon name="leaf" :size="22" /></span
            ><span class="card-category">情绪陪伴</span
            ><span class="card-sparkle"
              ><AppIcon name="sparkles" :size="16"
            /></span>
          </div>
          <div class="application-card-content">
            <div class="card-copy">
              <h3>AI 心理树洞</h3>
              <p class="card-description">
                说说烦心事，梳理此刻的感受。<br />不必想好怎么开口，按自己的节奏来。
              </p>
            </div>
            <IsleArtwork variant="mind" />
          </div>
          <div class="card-tags">
            <span>倾听心事</span><span>梳理情绪</span><span>记录日常</span>
          </div>
          <RouterLink to="/mind" class="card-action"
            ><span>开始倾诉<AppIcon name="arrow" :size="18" /></span
            ><span class="card-action-caption"
              >从“我今天……”开始</span
            ></RouterLink
          >
        </article>
        <article class="application-card agent-card">
          <div class="card-topline">
            <span class="app-symbol"><AppIcon name="bot" :size="22" /></span
            ><span class="card-category">任务助手</span>
          </div>
          <div class="application-card-content">
            <div class="card-copy">
              <h3>AI 智能体</h3>
              <p class="card-description">
                把目标拆成步骤，把想法变成行动。<br />制定计划、整理信息，看见每一步进展。
              </p>
            </div>
            <IsleArtwork variant="agent" />
          </div>
          <div class="card-tags">
            <span>拆解目标</span><span>整理信息</span><span>推进任务</span>
          </div>
          <RouterLink to="/agent" class="card-action"
            ><span>开始一个任务<AppIcon name="arrow" :size="18" /></span
            ><span class="card-action-caption">从“帮我……”开始</span></RouterLink
          >
        </article>
      </div>
    </section>
    <section class="mood-checkin" aria-labelledby="mood-title">
      <div class="mood-heading">
        <span class="mood-flower"><AppIcon name="flower" :size="27" /></span>
        <div>
          <h2 id="mood-title">此刻的你，感觉怎么样？</h2>
          <p>{{ moodHint }}</p>
        </div>
      </div>
      <div class="mood-options">
        <button
          v-for="item in moods"
          :key="item.label"
          :aria-pressed="mood === item.label"
          :class="{ selected: mood === item.label }"
          @click="mood = mood === item.label ? '' : item.label"
        >
          <AppIcon :name="item.icon" :size="17" />{{ item.label }}</button
        ><button
          v-if="mood"
          class="mood-go"
          aria-label="带着此刻的心情开始对话"
          @click="openMood"
        >
          <AppIcon name="arrow" :size="18" />
        </button>
      </div>
    </section>
    <section class="pause-banner">
      <span class="pause-icon"><AppIcon name="wind" :size="24" /></span>
      <div>
        <h2>忙碌的间隙，记得照顾自己。</h2>
        <p>暂停一下，深呼吸。你不必总是赶路。</p>
      </div>
      <button @click="startBreathing">
        给自己一分钟<AppIcon name="external" :size="17" />
      </button>
    </section>
    <footer class="home-footer">
      <span
        ><AppIcon name="sun" :size="14" />用一点点
        AI，接住生活里的每一种可能。</span
      >
    </footer>
  </div>
  <dialog
    ref="breatheDialog"
    class="modal breathing-modal"
    aria-labelledby="breathe-title"
    @close="stopBreathing"
    @click="
      (event) => {
        if (event.target === breatheDialog) breatheDialog.close();
      }
    "
  >
    <button
      class="icon-button modal-close"
      aria-label="结束呼吸练习"
      @click="breatheDialog.close()"
    >
      <AppIcon name="close" />
    </button>
    <h2 id="breathe-title">把这一分钟，留给自己</h2>
    <p>保持舒适，跟随自己的节奏。</p>
    <div class="breath-orbit" :class="{ breathing }">
      <span><AppIcon name="wind" :size="42" /></span>
    </div>
    <p class="breath-instruction" role="status">{{ breathPhase }}</p>
    <p class="breath-time">{{ Math.max(0, 60 - elapsed) }} 秒</p>
    <button class="primary-button" @click="breatheDialog.close()">
      {{ elapsed >= 60 ? "带着轻松继续" : "结束练习"
      }}<AppIcon name="arrow" :size="17" />
    </button>
  </dialog>
</template>
