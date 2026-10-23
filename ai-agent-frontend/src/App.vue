<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "./components/AppIcon.vue";
import { useConversations } from "./composables/useConversations";

const route = useRoute();
const router = useRouter();
const { recentSessions, activeIds, selectSession } = useConversations();
const activeKind = computed(() =>
  route.name === "agent" ? "agent" : route.name === "mind" ? "mind" : "",
);
const mobileOpen = ref(false);
const sidebar = ref(null);
const menuButton = ref(null);
const mobileMedia = window.matchMedia("(max-width: 760px)");
const isMobile = ref(mobileMedia.matches);
const updateMobile = (event) => {
  isMobile.value = event.matches;
  mobileOpen.value = false;
};
mobileMedia.addEventListener("change", updateMobile);
onBeforeUnmount(() => mobileMedia.removeEventListener("change", updateMobile));
watch(mobileOpen, async (open) => {
  await nextTick();
  if (open) sidebar.value?.querySelector("a, button")?.focus();
  else if (isMobile.value) menuButton.value?.focus();
});
const aboutDialog = ref(null);
const title = computed(
  () =>
    ({ home: "应用广场", mind: "AI 心理树洞", agent: "AI 智能体" })[
      route.name
    ] || "应用广场",
);
watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false;
    document.title = `${title.value} · 晴屿 AI`;
  },
);
function openSession(session) {
  selectSession(session.id);
  router.push(session.kind === "mind" ? "/mind" : "/agent");
  mobileOpen.value = false;
}
function skipNavigation() {
  const main = document.getElementById("main-content");
  main?.focus();
  main?.scrollIntoView();
}
function handleNavigationKey(event) {
  if (!mobileOpen.value || !isMobile.value) return;
  if (event.key === "Escape") {
    mobileOpen.value = false;
    return;
  }
  if (event.key !== "Tab") return;
  const targets = sidebar.value.querySelectorAll(
    "a[href], button:not(:disabled)",
  );
  const first = targets[0];
  const last = targets[targets.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
</script>

<template>
  <a class="skip-link" href="#main-content" @click.prevent="skipNavigation"
    >跳至主要内容</a
  >
  <div class="app-shell" :class="{ 'mobile-open': mobileOpen }">
    <button
      v-if="mobileOpen"
      class="sidebar-backdrop"
      aria-label="关闭导航"
      @click="mobileOpen = false"
    ></button>
    <aside
      id="main-navigation"
      ref="sidebar"
      class="sidebar"
      aria-label="主导航"
      :inert="isMobile && !mobileOpen"
      :role="isMobile && mobileOpen ? 'dialog' : undefined"
      :aria-modal="isMobile && mobileOpen ? true : undefined"
      @keydown="handleNavigationKey"
    >
      <button
        v-if="isMobile"
        class="icon-button close-navigation"
        aria-label="关闭导航"
        @click="mobileOpen = false"
      >
        <AppIcon name="close" />
      </button>
      <RouterLink to="/" class="brand" aria-label="晴屿 AI 首页">
        <span class="brand-mark"><AppIcon name="sun" :size="27" /></span>
        <span
          ><strong>晴屿 <span class="brand-ai">AI</span></strong></span
        >
      </RouterLink>
      <div class="nav-caption">你的 AI 生活空间</div>
      <nav class="primary-nav">
        <RouterLink
          to="/"
          class="nav-item"
          :class="{ active: route.name === 'home' }"
          :aria-current="route.name === 'home' ? 'page' : undefined"
          ><AppIcon name="compass" /><span>应用广场</span
          ><span v-if="route.name === 'home'" class="nav-dot"></span
        ></RouterLink>
        <RouterLink
          to="/mind"
          class="nav-item"
          :class="{ active: route.name === 'mind' }"
          :aria-current="route.name === 'mind' ? 'page' : undefined"
          ><AppIcon name="leaf" /><span>AI 心理树洞</span></RouterLink
        >
        <RouterLink
          to="/agent"
          class="nav-item"
          :class="{ active: route.name === 'agent' }"
          :aria-current="route.name === 'agent' ? 'page' : undefined"
          ><AppIcon name="bot" /><span>AI 智能体</span
          ><span class="mini-tag">Manus</span></RouterLink
        >
      </nav>
      <div class="sidebar-rule"></div>
      <div class="nav-caption recent-heading">
        最近的对话<AppIcon name="chat" :size="14" />
      </div>
      <div v-if="!recentSessions.length" class="empty-history">
        <span class="empty-history-icon"
          ><AppIcon name="chat" :size="19"
        /></span>
        <p>故事，从一句话开始</p>
        <small>你的对话会出现在这里</small>
      </div>
      <div v-else class="recent-list">
        <button
          v-for="session in recentSessions"
          :key="session.id"
          class="recent-item"
          :class="{ selected: activeIds[activeKind] === session.id }"
          :title="session.title"
          @click="openSession(session)"
        >
          <AppIcon
            :name="session.kind === 'mind' ? 'leaf' : 'chat'"
            :size="16"
          /><span>{{ session.title }}</span
          ><span v-if="session.busy" class="running-dot"></span>
        </button>
      </div>
      <div class="sidebar-bottom">
        <div class="sidebar-note">
          <span class="note-sun"><AppIcon name="sun" :size="24" /></span>
          <p>不必时刻满分，<br />今天的你也很棒。</p>
          <span class="note-stroke"></span>
        </div>
        <button class="about-button" @click="aboutDialog.showModal()">
          <AppIcon name="help" :size="18" /><span>关于晴屿</span
          ><AppIcon name="external" :size="15" />
        </button>
        <div class="sidebar-foot">一点智能，一点温度<span>v1.0</span></div>
      </div>
    </aside>
    <div
      class="workspace"
      :class="`workspace-${route.name || 'home'}`"
      :inert="isMobile && mobileOpen"
    >
      <header class="topbar">
        <div class="breadcrumb">
          <button
            ref="menuButton"
            class="icon-button mobile-menu"
            aria-label="打开导航"
            :aria-expanded="mobileOpen"
            aria-controls="main-navigation"
            @click="mobileOpen = !mobileOpen"
          >
            <AppIcon name="menu" /></button
          ><AppIcon class="breadcrumb-icon" name="home" :size="16" /><span
            class="breadcrumb-root"
            >工作空间</span
          ><span class="breadcrumb-slash">/</span><span>{{ title }}</span>
        </div>
        <span class="topbar-note"
          ><span class="soft-dot"></span>让每一天，多一点晴朗</span
        >
        <span class="user-avatar" title="访客空间" aria-label="访客空间"
          >晴</span
        >
      </header>
      <main id="main-content" tabindex="-1">
        <RouterView :key="route.name" />
      </main>
    </div>
  </div>
  <dialog
    ref="aboutDialog"
    class="modal about-modal"
    aria-labelledby="about-title"
    @click="
      (event) => {
        if (event.target === aboutDialog) aboutDialog.close();
      }
    "
  >
    <button
      class="icon-button modal-close"
      aria-label="关闭"
      @click="aboutDialog.close()"
    >
      <AppIcon name="close" />
    </button>
    <span class="brand-mark"><AppIcon name="sun" :size="28" /></span>
    <h2 id="about-title">欢迎来到晴屿</h2>
    <p>
      这里有愿意倾听的树洞，也有陪你完成想法的 AI
      智能体。选择一个应用，从一句话开始。
    </p>
    <div class="privacy-note">
      <AppIcon name="shield" /><span
        >对话仅保留在当前页面内存中，刷新后清空。发送的内容由后端 AI
        服务处理。</span
      >
    </div>
    <p class="modal-fineprint">
      树洞提供日常情绪陪伴，不能替代专业心理咨询或医疗服务。
    </p>
    <button class="primary-button" @click="aboutDialog.close()">
      开始探索<AppIcon name="arrow" :size="17" />
    </button>
  </dialog>
</template>
