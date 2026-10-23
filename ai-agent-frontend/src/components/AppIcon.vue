<script setup>
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  AudioLines,
  Bot,
  Check,
  ChevronRight,
  CircleHelp,
  Clipboard,
  Compass,
  Flower2,
  Heart,
  House,
  Leaf,
  Menu,
  MessageCircle,
  Plus,
  Send,
  ShieldCheck,
  Sparkles,
  Square,
  Sun,
  Wind,
  X,
  Zap,
} from "@lucide/vue";
defineProps({
  name: { type: String, default: "sun" },
  size: { type: [Number, String], default: 20 },
});
const icons = {
  down: ArrowDown,
  back: ArrowLeft,
  arrow: ArrowRight,
  external: ArrowUpRight,
  audio: AudioLines,
  bot: Bot,
  check: Check,
  chevron: ChevronRight,
  help: CircleHelp,
  copy: Clipboard,
  compass: Compass,
  flower: Flower2,
  heart: Heart,
  home: House,
  leaf: Leaf,
  menu: Menu,
  chat: MessageCircle,
  plus: Plus,
  send: Send,
  shield: ShieldCheck,
  sparkles: Sparkles,
  stop: Square,
  sun: Sun,
  wind: Wind,
  close: X,
  zap: Zap,
};
</script>

<template>
  <component
    :is="icons[name] || Sun"
    :size="size"
    :stroke-width="1.7"
    aria-hidden="true"
  />
</template>
