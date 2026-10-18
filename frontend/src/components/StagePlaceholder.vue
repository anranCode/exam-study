<script setup lang="ts">
import { useRouter } from 'vue-router'
import EmptyState from './EmptyState.vue'

/** 未实现阶段的统一占位 */
withDefaults(
  defineProps<{
    title: string
    description?: string
  }>(),
  { description: '该功能尚未实现,敬请期待。' }
)

const router = useRouter()
</script>

<template>
  <section class="stage-placeholder">
    <h1 class="stage-placeholder__title">{{ title }}</h1>
    <div class="stage-placeholder__body">
      <EmptyState
        :text="description"
        icon="target"
        action-text="回到今日"
        @action="router.push('/')"
      />
    </div>
  </section>
</template>

<style scoped>
.stage-placeholder {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.stage-placeholder__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.stage-placeholder__body {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  box-shadow: var(--shadow-card);
}
</style>