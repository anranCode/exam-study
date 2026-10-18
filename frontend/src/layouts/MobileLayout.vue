<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useTodayStore } from '@/stores/today'

interface TabItem {
  label: string
  name: string
  to: string
  icon: string
}

/**
 * showTabbar=false 用于做题 / 结果这类「单页」路由(路由 meta.layout === 'single'):
 * 做一半点到别的 Tab 会打断思路,还得多点一次退出。
 */
const props = withDefaults(defineProps<{ showTabbar?: boolean }>(), { showTabbar: true })

const tabs: TabItem[] = [
  { name: 'today', label: '今日', to: '/', icon: 'home' },
  { name: 'question-list', label: '题库', to: '/questions', icon: 'list' },
  { name: 'wrong-book', label: '错题', to: '/wrong', icon: 'bookmark' },
  { name: 'stats', label: '统计', to: '/stats', icon: 'chart' }
]

const route = useRoute()
const todayStore = useTodayStore()

const activeTab = computed(() => (route.meta.tab as string | undefined) ?? '')

function badgeFor(tab: TabItem): number {
  if (tab.name === 'today' || tab.name === 'wrong-book') return todayStore.dueTotal
  return 0
}
</script>

<template>
  <div class="mobile-layout" :class="{ 'mobile-layout--single': !showTabbar }">
    <main class="mobile-layout__content">
      <slot />
    </main>

    <nav v-if="showTabbar" class="tabbar" aria-label="主导航">
      <RouterLink
        v-for="tab in tabs"
        :key="tab.name"
        :to="tab.to"
        class="tabbar__item"
        :class="{ 'tabbar__item--active': activeTab === tab.name }"
      >
        <span class="tabbar__icon">
          <AppIcon :name="tab.icon" :size="22" />
          <span v-if="badgeFor(tab) > 0" class="tabbar__badge">
            {{ badgeFor(tab) > 99 ? '99+' : badgeFor(tab) }}
          </span>
        </span>
        <span class="tabbar__label">{{ tab.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.mobile-layout {
  min-height: 100vh;
  min-height: 100dvh;
  background-color: var(--bg-page);
}

.mobile-layout__content {
  padding: var(--page-padding);
  padding-bottom: calc(var(--tabbar-height) + var(--space-lg) + env(safe-area-inset-bottom));
}

/* 没有底部 Tab 时不必给 Tab 留位置 */
.mobile-layout--single .mobile-layout__content {
  padding-bottom: calc(var(--space-lg) + env(safe-area-inset-bottom));
}

.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: stretch;
  height: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  background-color: var(--bg-card);
  border-top: 1px solid var(--border-color);
}

.tabbar__item {
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: var(--tabbar-height);
  color: var(--text-secondary);
}

.tabbar__item--active {
  color: var(--color-primary);
}

.tabbar__icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tabbar__label {
  font-size: var(--font-size-aux);
  line-height: var(--line-height-aux);
}

.tabbar__badge {
  position: absolute;
  top: -6px;
  left: 12px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: var(--radius-full);
  background-color: var(--color-danger);
  color: var(--text-on-primary);
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}
</style>