<template>
  <span class="embed-thinking" role="status" :aria-label="t('chat.thinkingAlt')">
    <span class="embed-thinking__icon" aria-hidden="true" />
    <span class="embed-thinking__text">{{ t(phaseKey) }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const PHASES = [
  'chat.thinkingPhases.thinking',
  'chat.thinkingPhases.searching',
  'chat.thinkingPhases.analyzing',
  'chat.thinkingPhases.waiting',
] as const

const phaseIndex = ref(0)
const phaseKey = computed(() => PHASES[phaseIndex.value])
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    phaseIndex.value = (phaseIndex.value + 1) % PHASES.length
  }, 2600)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<style scoped lang="less">
.embed-thinking {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  min-height: 20px;

  &__icon {
    width: 18px;
    height: 18px;
    flex: 0 0 auto;
    background-color: var(--td-brand-color, #2f52f8);
    -webkit-mask: url('../../assets/img/thinking.webp') center / contain no-repeat;
    mask: url('../../assets/img/thinking.webp') center / contain no-repeat;
    animation: embed-thinking-breathe 1.6s ease-in-out infinite;
  }

  &__text {
    font-size: 13px;
    line-height: 1;
    color: var(--td-text-color-secondary);
    white-space: nowrap;
  }
}

@keyframes embed-thinking-breathe {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.96);
  }

  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .embed-thinking__icon {
    animation: none;
    opacity: 1;
  }
}
</style>
