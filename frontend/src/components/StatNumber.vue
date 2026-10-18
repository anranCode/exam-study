<script setup lang="ts">
import { computed } from 'vue'

/** 统计大数字 */
const props = withDefaults(
  defineProps<{
    value: number | string | null | undefined
    label: string
    unit?: string
    size?: 'md' | 'lg'
  }>(),
  { size: 'lg' }
)

const display = computed(() => {
  const v = props.value
  if (v === null || v === undefined) return '—'
  if (typeof v === 'number') return Number.isNaN(v) ? '—' : String(v)
  return v
})
</script>

<template>
  <div class="stat-number" :class="`stat-number--${size}`">
    <div class="stat-number__value mono">
      <span>{{ display }}</span>
      <span v-if="unit" class="stat-number__unit">{{ unit }}</span>
    </div>
    <div class="stat-number__label">{{ label }}</div>
  </div>
</template>

<style scoped>
.stat-number {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.stat-number__value {
  font-size: var(--font-size-stat);
  font-weight: var(--font-weight-stat);
  line-height: var(--line-height-stat);
  color: var(--text-primary);
}

.stat-number--md .stat-number__value {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
}

.stat-number__unit {
  margin-left: 2px;
  font-size: var(--font-size-body);
  font-weight: var(--font-weight-body);
  color: var(--text-secondary);
}

.stat-number__label {
  font-size: var(--font-size-aux);
  line-height: var(--line-height-aux);
  color: var(--text-secondary);
}
</style>