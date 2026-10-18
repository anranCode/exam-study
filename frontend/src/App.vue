<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useBreakpoint } from '@/composables/useBreakpoint'
import MobileLayout from '@/layouts/MobileLayout.vue'
import DesktopLayout from '@/layouts/DesktopLayout.vue'

/** 按 768px 断点切换根布局 */
const { isMobile } = useBreakpoint()
const route = useRoute()

/**
 * 做题 / 本轮小结标的是 `meta.layout: 'single'` —— 手机上不显示底部 Tab,
 * 免得做一半误触离开。桌面端侧栏保持原样(那边一直有,布局已经调好了)。
 */
const hideTabbar = computed(() => isMobile.value && String(route.meta.layout) === 'single')
</script>

<template>
  <MobileLayout v-if="isMobile" :show-tabbar="!hideTabbar">
    <RouterView />
  </MobileLayout>
  <DesktopLayout v-else>
    <RouterView />
  </DesktopLayout>
</template>
