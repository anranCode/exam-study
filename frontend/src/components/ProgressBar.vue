<script setup lang="ts">
import { computed } from 'vue'

/**
 * 胶囊进度条,高度 6px。
 * 颜色按完成度:0–59% 主色 / 60–89% 成功色 / 90%+ 深绿。
 * 只显示 `12/20` 这类分数,不显示百分比(设计规范要求)。
 */
const props = withDefaults(
  defineProps<{
    value: number
    total: number
    showText?: boolean
  }>(),
  { showText: true }
)

const safeTotal = computed(() => (props.total > 0 ? props.total : 0))
const safeValue = computed(() => Math.max(0, Math.min(props.value, safeTotal.value || props.value)))
const percent = computed(() =>
  safeTotal.value > 0 ? Math.round((safeValue.value / safeTotal.value) * 100) : 0
)

const tier = computed(() => {
  if (percent.value >= 90) return 'high'
  if (percent.value >= 60) return 'mid'
  return 'low'
})

const fractionText = computed(() => `${safeValue.value}/${safeTotal.value}`)
</script>

<template>
  <div class="progress">
    <div
      class="progress__track"
      role="progressbar"
      :aria-valuenow="percent"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <div class="progress__fill" :class="`progress__fill--${tier}`" :style="{ width: percent + '%' }" />
    </div>
    <span v-if="showText" class="progress__text mono">{{ fractionText }}</span>
  </div>
</template>

<style scoped>
.progress {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
}

.progress__track {
  flex: 1;
  height: 6px;
  border-radius: var(--radius-full);
  background-color: var(--border-color);
  overflow: hidden;
}

.progress__fill {
  height: 100%;
  border-radius: var(--radius-full);
  transition: width 0.3s ease;
}

.progress__fill--low {
  background-color: var(--color-primary);
}

.progress__fill--mid {
  background-color: var(--color-success);
}

.progress__fill--high {
  background-color: var(--color-success-dark);
}

.progress__text {
  flex-shrink: 0;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}
</style>