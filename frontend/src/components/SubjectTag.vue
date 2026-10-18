<script setup lang="ts">
import { computed } from 'vue'

/**
 * 科目标签:左侧色条 + 简称。
 * 颜色来自 subject.color(后端数据),不写死。
 */
const props = withDefaults(
  defineProps<{
    name: string
    color?: string
    size?: 'sm' | 'md'
  }>(),
  { size: 'md' }
)

const barStyle = computed(() => ({ backgroundColor: props.color || 'var(--color-primary)' }))
</script>

<template>
  <span class="subject-tag" :class="`subject-tag--${size}`">
    <span class="subject-tag__bar" :style="barStyle" aria-hidden="true" />
    <span class="subject-tag__name wrap-anywhere">{{ name }}</span>
  </span>
</template>

<style scoped>
.subject-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  max-width: 100%;
  min-height: 24px;
  padding: 2px var(--space-sm) 2px var(--space-xs);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background-color: var(--bg-card);
  font-size: var(--font-size-aux);
  line-height: var(--line-height-aux);
  color: var(--text-primary);
}

.subject-tag--md {
  font-size: var(--font-size-body);
}

.subject-tag__bar {
  width: 4px;
  align-self: stretch;
  min-height: 16px;
  border-radius: var(--radius-full);
}

.subject-tag__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>