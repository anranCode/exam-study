<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import type { OptionVisualState } from '@/utils/constants'

/**
 * 选项卡片(刷题页最重要组件)。
 * 五种视觉状态:默认 / 选中 / 正确 / 错误 / 正确答案。
 * 不只用颜色区分,正确带 ✓、错误带 ✗;高度 ≥48px;字母固定 28px 等宽列。
 * 颜色全部来自 CSS 变量。
 */
const props = withDefaults(
  defineProps<{
    optionKey: string
    text: string
    state?: OptionVisualState
    /** 判定后不可再点击 */
    disabled?: boolean
  }>(),
  { state: 'default', disabled: false }
)

defineEmits<{ (e: 'select'): void }>()

const icon = computed(() => {
  if (props.state === 'correct') return 'check'
  if (props.state === 'wrong') return 'close'
  return ''
})

const label = computed(() => {
  if (props.state === 'correct') return '正确答案'
  if (props.state === 'wrong') return '你的选择'
  if (props.state === 'selected') return '已选择'
  return ''
})
</script>

<template>
  <button
    type="button"
    class="option"
    :class="[`option--${state}`, { 'option--disabled': disabled }]"
    :disabled="disabled"
    :aria-pressed="state === 'selected' || state === 'wrong'"
    @click="$emit('select')"
  >
    <span class="option__key mono">{{ optionKey }}</span>
    <span class="option__text wrap-anywhere">{{ text }}</span>
    <span v-if="icon" class="option__mark" aria-hidden="true">
      <AppIcon :name="icon" :size="16" />
      <span class="option__mark-label">{{ label }}</span>
    </span>
    <span v-else-if="label" class="option__mark option__mark--plain">{{ label }}</span>
  </button>
</template>

<style scoped>
.option {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  width: 100%;
  min-height: 48px;
  padding: 12px var(--space-md);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  color: var(--text-primary);
  text-align: left;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;
}

.option:not(.option--disabled):hover {
  border-color: var(--color-primary);
}

.option__key {
  flex: 0 0 28px;
  width: 28px;
  text-align: center;
  font-size: var(--font-size-option);
  font-weight: 600;
  line-height: var(--line-height-option);
  color: var(--text-secondary);
}

.option__text {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-size-option);
  line-height: var(--line-height-option);
}

.option__mark {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: var(--font-size-aux);
}

.option__mark-label {
  color: inherit;
}

.option__mark--plain {
  align-self: center;
  color: inherit;
}

/* 默认 */
.option--default .option__key {
  color: var(--text-secondary);
}

/* 选中 */
.option--selected {
  border-color: var(--color-primary);
  background-color: var(--color-primary-light);
}

.option--selected .option__key {
  color: var(--color-primary);
}

.option--selected .option__mark--plain {
  color: var(--color-primary);
}

/* 正确(正确答案始终标绿,即便用户答对也要能看出其他选项为何错) */
.option--correct {
  border-color: var(--color-success);
  background-color: var(--color-success-light);
}

.option--correct .option__key,
.option--correct .option__mark {
  color: var(--color-success);
}

/* 错误(用户错选) */
.option--wrong {
  border-color: var(--color-danger);
  background-color: var(--color-danger-light);
}

.option--wrong .option__key,
.option--wrong .option__mark {
  color: var(--color-danger);
}

.option--disabled {
  cursor: default;
}
</style>