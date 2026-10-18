<script setup lang="ts">
import AppIcon from './AppIcon.vue'

/**
 * 通用空状态:文案 + 可选的下一步动作按钮。
 * 文案要给出下一步动作,不要只说「暂无数据」。
 */
withDefaults(
  defineProps<{
    text: string
    title?: string
    actionText?: string
    icon?: string
  }>(),
  { icon: 'book' }
)

defineEmits<{ (e: 'action'): void }>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon" aria-hidden="true">
      <AppIcon :name="icon" :size="28" />
    </div>
    <p v-if="title" class="empty-state__title">{{ title }}</p>
    <p class="empty-state__text wrap-anywhere">{{ text }}</p>
    <button
      v-if="actionText"
      type="button"
      class="empty-state__action tap-target"
      @click="$emit('action')"
    >
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: var(--space-xl) var(--space-lg);
  text-align: center;
}

.empty-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: var(--space-xs);
  border-radius: var(--radius-full);
  background-color: var(--color-primary-light);
  color: var(--color-primary);
}

.empty-state__title {
  font-size: var(--font-size-card-title);
  font-weight: var(--font-weight-card-title);
  line-height: var(--line-height-card-title);
  color: var(--text-primary);
}

.empty-state__text {
  max-width: 32em;
  font-size: var(--font-size-body);
  line-height: var(--line-height-body);
  color: var(--text-secondary);
}

.empty-state__action {
  margin-top: var(--space-sm);
  min-height: var(--tap-target-min);
  padding: 0 var(--space-lg);
  border: none;
  border-radius: var(--radius-full);
  background-color: var(--color-primary);
  color: var(--text-on-primary);
  font-size: var(--font-size-body);
  font-weight: 500;
}

.empty-state__action:hover {
  background-color: var(--color-primary-hover);
}
</style>