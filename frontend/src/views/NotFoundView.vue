<script setup lang="ts">
import { useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
</script>

<template>
  <section class="not-found">
    <div class="not-found__code mono">404</div>
    <EmptyState
      text="这里没有你要找的页面,它可能已经被移走了。"
      icon="target"
      action-text="回到今日"
      @action="router.push('/')"
    />
  </section>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-xl) 0;
}

.not-found__code {
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  color: var(--color-primary);
}
</style>