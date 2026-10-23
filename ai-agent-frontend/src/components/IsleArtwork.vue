<script setup>
import HeroArtwork from "./HeroArtwork.vue";
defineProps({ variant: { type: String, default: "hero" } });
</script>

<template>
  <HeroArtwork v-if="variant === 'hero'" />
  <svg
    v-else-if="variant === 'mind'"
    class="isle-art"
    viewBox="0 0 220 190"
    fill="none"
    aria-hidden="true"
  >
    <ellipse cx="114" cy="167" rx="81" ry="11" fill="#DBB992" opacity=".17" />
    <circle cx="150" cy="52" r="25" fill="#F9CF82" />
    <g stroke="#F2BD67" stroke-width="2" stroke-linecap="round">
      <path
        d="M150 16v-6m0 84v-6m36-36h6m-84 0h6m9-27-4-4m62 62-4-4m0-54 4-4"
      />
    </g>
    <path
      d="M66 153c-25-10-32-40-17-58-18-19-4-48 20-48 1-29 45-39 58-13 22-4 39 18 30 38 27 15 21 49-2 57-4 30-60 41-89 24Z"
      fill="#A8BE93"
    />
    <path
      d="M93 163V84m0 43-22-20m22 4 29-26"
      stroke="#68846B"
      stroke-width="7"
      stroke-linecap="round"
    />
    <path
      d="M80 164h29"
      stroke="#68846B"
      stroke-width="7"
      stroke-linecap="round"
    />
    <rect x="118" y="105" width="68" height="45" rx="16" fill="#FFFDFA" />
    <path d="m139 144-7 14 22-10" fill="#FFFDFA" />
    <path
      d="M151 134s-12-7-12-14c0-8 9-9 12-3 3-6 12-5 12 3 0 7-12 14-12 14Z"
      fill="#EB9C7B"
    />
    <path d="m37 52 2 7 7 2-7 2-2 7-2-7-7-2 7-2 2-7Z" fill="#D7A16C" />
  </svg>
  <svg
    v-else
    class="isle-art"
    viewBox="0 0 220 190"
    fill="none"
    aria-hidden="true"
  >
    <defs>
      <linearGradient
        id="bot-body"
        x1="63"
        y1="53"
        x2="167"
        y2="150"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#FDFDFF" />
        <stop offset="1" stop-color="#D9D6F8" />
      </linearGradient>
    </defs>
    <ellipse cx="112" cy="164" rx="77" ry="12" fill="#AFA8DB" opacity=".18" />
    <ellipse
      cx="112"
      cy="109"
      rx="88"
      ry="35"
      stroke="#B7B0E5"
      stroke-dasharray="3 6"
      transform="rotate(-25 112 109)"
    />
    <rect
      x="59"
      y="57"
      width="107"
      height="91"
      rx="29"
      fill="url(#bot-body)"
      stroke="#C4BDEB"
    />
    <rect x="74" y="74" width="77" height="48" rx="18" fill="#8C87BE" />
    <rect x="88" y="87" width="8" height="15" rx="4" fill="#E7F4FF" />
    <rect x="129" y="87" width="8" height="15" rx="4" fill="#E7F4FF" />
    <path
      d="M106 108q7 5 14 0"
      stroke="#E7F4FF"
      stroke-width="2.5"
      stroke-linecap="round"
    />
    <path d="M112 57V40" stroke="#AAA1D8" stroke-width="4" />
    <circle cx="112" cy="34" r="8" fill="#F5C86D" />
    <rect x="47" y="86" width="11" height="33" rx="5.5" fill="#BFB9E2" />
    <rect x="167" y="86" width="11" height="33" rx="5.5" fill="#BFB9E2" />
    <path d="m174 38 4 12 12 4-12 4-4 12-4-12-12-4 12-4 4-12Z" fill="#B0A4DE" />
    <path d="m44 126 3 7 8 3-8 2-3 8-2-8-8-2 8-3 2-7Z" fill="#D3B365" />
    <path
      d="M99 136h26"
      stroke="#B4ACDA"
      stroke-width="3"
      stroke-linecap="round"
    />
  </svg>
</template>
