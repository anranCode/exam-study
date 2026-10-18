import { computed, onMounted, onUnmounted, ref } from 'vue'

export type Breakpoint = 'mobile' | 'tablet' | 'desktop'

/** 手机断点(< 768px) */
export const MOBILE_MAX = 768
/** 桌面断点(> 1024px) */
export const DESKTOP_MIN = 1024

/**
 * 返回窗口宽度断点,供 App.vue 决定使用 MobileLayout 还是 DesktopLayout。
 * 在 768px 处切换移动 / 桌面布局。
 */
export function useBreakpoint() {
  const width = ref(typeof window === 'undefined' ? DESKTOP_MIN : window.innerWidth)

  const update = () => {
    width.value = window.innerWidth
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', update)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', update)
  })

  const breakpoint = computed<Breakpoint>(() => {
    if (width.value < MOBILE_MAX) return 'mobile'
    if (width.value <= DESKTOP_MIN) return 'tablet'
    return 'desktop'
  })

  const isMobile = computed(() => width.value < MOBILE_MAX)
  const isDesktop = computed(() => width.value >= MOBILE_MAX)

  return { width, breakpoint, isMobile, isDesktop }
}