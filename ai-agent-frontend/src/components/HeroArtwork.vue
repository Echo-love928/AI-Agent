<script setup>
import { useId } from "vue";

const prefix = `sunny-isle-${useId()}`;
const paint = (name) => `url(#${prefix}-${name})`;
</script>

<template>
  <svg
    class="isle-art hero-artwork"
    viewBox="0 0 560 410"
    fill="none"
    aria-hidden="true"
  >
    <defs>
      <radialGradient :id="`${prefix}-halo`">
        <stop stop-color="#F9DBB4" stop-opacity=".58" />
        <stop offset="1" stop-color="#F9DBB4" stop-opacity="0" />
      </radialGradient>
      <radialGradient :id="`${prefix}-sun`" cx=".32" cy=".26" r=".8">
        <stop stop-color="#FFE9B7" />
        <stop offset=".4" stop-color="#FFD38F" />
        <stop offset=".78" stop-color="#F3B773" />
        <stop offset="1" stop-color="#DD9B5B" />
      </radialGradient>
      <linearGradient
        :id="`${prefix}-edge`"
        x1="154"
        y1="295"
        x2="395"
        y2="373"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#ABB797" />
        <stop offset=".6" stop-color="#8C9F85" />
        <stop offset="1" stop-color="#718B7B" />
      </linearGradient>
      <linearGradient
        :id="`${prefix}-island`"
        x1="166"
        y1="216"
        x2="363"
        y2="340"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#D1D8B7" />
        <stop offset=".5" stop-color="#B4C5A4" />
        <stop offset="1" stop-color="#91AB93" />
      </linearGradient>
      <linearGradient
        :id="`${prefix}-path`"
        x1="240"
        y1="236"
        x2="271"
        y2="339"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#F3EBCF" />
        <stop offset="1" stop-color="#E1DFBC" />
      </linearGradient>
      <linearGradient
        :id="`${prefix}-card`"
        x1="30"
        y1="0"
        x2="123"
        y2="91"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#FFFFFC" />
        <stop offset="1" stop-color="#F5ECDD" />
      </linearGradient>
      <linearGradient
        :id="`${prefix}-card-edge`"
        x1="0"
        y1="65"
        x2="135"
        y2="94"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#E9DDC9" />
        <stop offset="1" stop-color="#E5D5BF" />
      </linearGradient>
      <linearGradient
        :id="`${prefix}-heart`"
        x1="28"
        y1="25"
        x2="58"
        y2="59"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#F6B196" />
        <stop offset="1" stop-color="#D98770" />
      </linearGradient>
      <linearGradient
        :id="`${prefix}-leaf`"
        x1="360"
        y1="239"
        x2="381"
        y2="284"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#BDCF9F" />
        <stop offset="1" stop-color="#779977" />
      </linearGradient>
      <filter
        :id="`${prefix}-ground`"
        x="63"
        y="303"
        width="444"
        height="105"
        filterUnits="userSpaceOnUse"
      >
        <feGaussianBlur stdDeviation="11" />
      </filter>
      <filter
        :id="`${prefix}-sun-shadow`"
        x="173"
        y="38"
        width="233"
        height="252"
        filterUnits="userSpaceOnUse"
      >
        <feDropShadow
          dx="3"
          dy="14"
          stdDeviation="13"
          flood-color="#C38B51"
          flood-opacity=".16"
        />
      </filter>
      <filter
        :id="`${prefix}-card-shadow`"
        x="-35%"
        y="-35%"
        width="180%"
        height="210%"
      >
        <feDropShadow
          dx="3"
          dy="11"
          stdDeviation="10"
          flood-color="#AD8563"
          flood-opacity=".15"
        />
      </filter>
      <filter :id="`${prefix}-grain`" x="0" y="0" width="100%" height="100%">
        <feTurbulence
          type="fractalNoise"
          baseFrequency=".72"
          numOctaves="3"
          seed="8"
          result="noise"
        />
        <feColorMatrix in="noise" type="saturate" values="0" />
        <feComponentTransfer>
          <feFuncA type="linear" slope=".14" />
        </feComponentTransfer>
        <feComposite in2="SourceGraphic" operator="in" result="grain" />
        <feBlend in="SourceGraphic" in2="grain" mode="soft-light" />
      </filter>
    </defs>

    <ellipse cx="290" cy="199" rx="233" ry="182" :fill="paint('halo')" />
    <ellipse
      cx="286"
      cy="354"
      rx="164"
      ry="12"
      fill="#BFA383"
      opacity=".21"
      :filter="paint('ground')"
    />
    <ellipse cx="286" cy="352" rx="133" ry="6" fill="#BEAB8F" opacity=".15" />

    <!-- The rounded base and upper terrain give the island a physical edge. -->
    <path
      d="M123 303c20-39 78-67 144-67 87-1 150 28 178 65v17c-27 34-94 50-160 50-78 0-135-20-162-48v-17Z"
      :fill="paint('edge')"
    />
    <path
      d="M123 303c28-46 77-76 144-76 87 0 153 31 178 74-32 32-92 47-162 47-69 0-126-16-160-45Z"
      :fill="paint('island')"
      :filter="paint('grain')"
    />
    <path
      d="M247 231c11 23-46 36-43 54 3 17 61 21 79 57"
      :stroke="paint('path')"
      stroke-width="20"
    />
    <path
      d="M127 304c39 30 96 43 155 44 66 1 129-16 159-44"
      stroke="#E3E8CA"
      stroke-opacity=".46"
      stroke-width="1.5"
    />

    <g :filter="paint('sun-shadow')">
      <circle
        cx="285"
        cy="145"
        r="83"
        :fill="paint('sun')"
        :filter="paint('grain')"
      />
      <path
        d="M223 117c8-22 26-37 48-42"
        stroke="#FFF1CD"
        stroke-opacity=".2"
        stroke-width="3"
        stroke-linecap="round"
      />
      <g fill="#9A714A">
        <ellipse cx="265" cy="145" rx="3.2" ry="3.7" />
        <ellipse cx="304" cy="145" rx="3.2" ry="3.7" />
      </g>
      <path
        d="M274 162q11 10 22 0"
        stroke="#9A714A"
        stroke-width="2.5"
        stroke-linecap="round"
      />
      <ellipse cx="251" cy="159" rx="8" ry="4" fill="#EAA280" opacity=".38" />
      <ellipse cx="317" cy="159" rx="8" ry="4" fill="#EAA280" opacity=".38" />
    </g>
    <g stroke="#D9AA71" stroke-width="4" stroke-linecap="round">
      <path d="M285 36V24m86 56 10-9m-182 8-9-9m199 81h12" />
    </g>

    <!-- Small, grounded leaves share the same upper-left lighting as the sun. -->
    <ellipse cx="369" cy="313" rx="18" ry="4" fill="#6D886F" opacity=".13" />
    <path
      d="M370 310v-57"
      stroke="#799477"
      stroke-width="5"
      stroke-linecap="round"
    />
    <path
      d="M370 273c-24 0-34-12-30-29 20 0 31 11 30 29Z"
      :fill="paint('leaf')"
    />
    <path
      d="M371 287c24-1 35-12 32-27-19 0-32 10-32 27Z"
      :fill="paint('leaf')"
    />
    <path
      d="m345 249 25 24m28-9-27 23"
      stroke="#6F8D70"
      stroke-width="1.4"
      stroke-linecap="round"
      opacity=".65"
    />

    <g
      transform="translate(65 170) rotate(-11 65 43)"
      :filter="paint('card-shadow')"
    >
      <path
        d="M24 5h88q22 0 22 22v41q0 22-22 22H54L32 103l4-13H24Q2 90 2 68V27Q2 5 24 5Z"
        :fill="paint('card-edge')"
      />
      <path
        d="M24 0h88q22 0 22 22v41q0 22-22 22H54L32 98l4-13H24Q2 85 2 63V22Q2 0 24 0Z"
        :fill="paint('card')"
        stroke="#FFFEF7"
        stroke-width="1.5"
      />
      <rect x="24" y="23" width="38" height="38" rx="12" fill="#EDF0E1" />
      <path
        d="M43 51s-13-8-13-16c0-7 9-9 13-3 4-6 13-4 13 3 0 8-13 16-13 16Z"
        :fill="paint('heart')"
        transform="translate(0 4)"
      />
      <path
        d="M77 34h29M77 47h20"
        stroke="#C5B9A6"
        stroke-width="4"
        stroke-linecap="round"
      />
    </g>
    <g
      transform="translate(383 216) rotate(9 59 42)"
      :filter="paint('card-shadow')"
    >
      <path
        d="M22 5h76q22 0 22 22v35q0 22-22 22h-9l7 13-24-13H22Q0 84 0 62V27Q0 5 22 5Z"
        :fill="paint('card-edge')"
      />
      <path
        d="M22 0h76q22 0 22 22v35q0 22-22 22h-9l7 13-24-13H22Q0 79 0 57V22Q0 0 22 0Z"
        :fill="paint('card')"
        stroke="#FFFEF7"
        stroke-width="1.5"
      />
      <rect x="21" y="21" width="34" height="35" rx="10" fill="#ECE9F6" />
      <path
        d="m30 39 6 6 11-13"
        stroke="#9990BB"
        stroke-width="4"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <path
        d="M69 31h28M69 44h19"
        stroke="#C5B9A6"
        stroke-width="4"
        stroke-linecap="round"
      />
    </g>
    <path
      d="m447 109 2.5 7 7 2.5-7 2.5-2.5 7-2.5-7-7-2.5 7-2.5 2.5-7Z"
      fill="#D7B98B"
    />
  </svg>
</template>
