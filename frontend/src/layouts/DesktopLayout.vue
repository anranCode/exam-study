<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'

interface NavItem {
  label: string
  to: string
  icon: string
}

const navItems: NavItem[] = [
  { label: '今日', to: '/', icon: 'home' },
  { label: '自由练习', to: '/practice/custom', icon: 'target' },
  { label: '题库', to: '/questions', icon: 'list' },
  { label: '错题', to: '/wrong', icon: 'bookmark' },
  { label: '统计', to: '/stats', icon: 'chart' },
  { label: '科目', to: '/subjects', icon: 'book' },
  { label: '导入', to: '/questions/import', icon: 'upload' },
  { label: '设置', to: '/settings', icon: 'settings' }
]

const route = useRoute()

function matches(to: string): boolean {
  const path = route.path
  if (to === '/') return path === '/'
  return path === to || path.startsWith(`${to}/`)
}

/** 取匹配到的最长路径,避免 /questions/import 同时点亮「题库」与「导入」 */
const activeTo = computed(() => {
  let best = ''
  for (const item of navItems) {
    if (matches(item.to) && item.to.length > best.length) best = item.to
  }
  return best
})
</script>

<template>
  <div class="desktop-layout">
    <aside class="sidebar">
      <div class="sidebar__brand">
        <AppIcon name="book" :size="20" />
        <span>自考学习中心</span>
      </div>
      <nav class="sidebar__nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ 'nav-item--active': activeTo === item.to }"
          :title="item.label"
          :aria-label="item.label"
        >
          <AppIcon :name="item.icon" :size="18" />
          <span class="nav-item__label">{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <main class="desktop-layout__content">
      <div class="desktop-layout__inner">
        <slot />
      </div>
    </main>
  </div>
</template>

<style scoped>
.desktop-layout {
  display: flex;
  min-height: 100vh;
  background-color: var(--bg-page);
}

.sidebar {
  flex-shrink: 0;
  width: 208px;
  padding: var(--space-md) var(--space-sm);
  border-right: 1px solid var(--border-color);
  background-color: var(--bg-card);
  /* 内容区滚动时侧栏留在原地:sticky + 自身撑满一屏,导航项多时在内部滚动 */
  position: sticky;
  top: 0;
  align-self: flex-start;
  height: 100vh;
  overflow-y: auto;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-sm) var(--space-md);
  font-size: var(--font-size-card-title);
  font-weight: var(--font-weight-card-title);
  color: var(--color-primary);
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: var(--tap-target-min);
  padding: 0 var(--space-md);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
}

.nav-item:hover {
  background-color: var(--bg-page);
  color: var(--text-primary);
}

.nav-item--active {
  background-color: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 500;
}

.nav-item__label {
  font-size: var(--font-size-body);
}

.desktop-layout__content {
  flex: 1 1 auto;
  min-width: 0;
}

.desktop-layout__inner {
  width: 100%;
  max-width: var(--content-max-tablet);
  margin: 0 auto;
  padding: var(--space-lg) var(--page-padding) var(--space-xl);
}

@media (min-width: 1025px) {
  .desktop-layout__inner {
    max-width: var(--content-max-desktop);
  }
}

/*
 * ---------- 平板(768–1024) ----------
 * 这个区间用的还是桌面布局,但 208px 的侧栏在 820px 的屏幕上要吃掉四分之一,
 * 内容区只剩 ~600px。收成 64px 的纯图标「侧轨」,桌面(>1024)完全不受影响。
 * 导航项补了 title / aria-label,图标模式下仍能看出每一项是什么。
 */
@media (min-width: 768px) and (max-width: 1024px) {
  .sidebar {
    width: 64px;
    padding: var(--space-md) var(--space-xs);
  }

  .sidebar__brand {
    justify-content: center;
    padding: var(--space-xs) 0 var(--space-md);
  }

  .sidebar__brand span,
  .nav-item__label {
    display: none;
  }

  .nav-item {
    justify-content: center;
    padding: 0;
  }
}

/*
 * ---------- 移动端:侧栏一律不出现 ----------
 * 布局切换的真实保险在 useBreakpoint(screen.width + 输入聚焦冻结 + 变宽迟滞):
 * 手机屏幕宽度 < 768 时永远判移动端。这里再补一条纯 CSS 的兜底,
 * 防止某些瞬间(尺寸切换的 250ms 迟滞里、或 JS 被冻结时)侧栏先冒出来把画面挤乱。
 */
@media (max-width: 767.98px) {
  .sidebar {
    display: none;
  }
}
</style>