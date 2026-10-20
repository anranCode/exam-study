import { computed, onMounted, onUnmounted, ref } from 'vue'

export type Breakpoint = 'mobile' | 'tablet' | 'desktop'

/** 手机断点(< 768px) */
export const MOBILE_MAX = 768
/** 桌面断点(> 1024px) */
export const DESKTOP_MIN = 1024

/** 当前焦点是否落在可输入元素上(软键盘很可能正在弹着) */
function isEditableFocused(): boolean {
  const el = document.activeElement as HTMLElement | null
  if (!el) return false
  const tag = el.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  return el.isContentEditable === true
}

/**
 * 量一次当前可用宽度。
 *
 * <p>手机软键盘弹出时,部分浏览器(尤其安卓上的 WebView / 微信内置浏览器)会把
 * `window.innerWidth` 报成明显偏小的值。如果布局只认 innerWidth,键盘一弹就可能
 * 被判成桌面端 —— 于是 208px 的左侧导航栏突然插进来,整个画面挤成一团。
 *
 * <p>所以这里取三个来源的**最小可信值**:
 * <ul>
 *   <li>`innerWidth` / `documentElement.clientWidth`:正常情况下的可用宽度;</li>
 *   <li>`screen.width`:键盘不会改它。屏幕本身就很窄(手机)时据此直接判移动端,
 *       不受浏览器把视口报小的影响。</li>
 * </ul>
 * 另外:输入框聚焦期间**冻结**宽度,彻底避免打字中途切换布局。
 */
function measureWidth(): number {
  const inner = window.innerWidth
  const client = document.documentElement?.clientWidth ?? inner
  return client > 0 ? Math.min(inner, client) : inner
}

/** 屏幕物理宽度;取不到时返回 0(表示「这招用不上」) */
function screenWidth(): number {
  const w = window.screen?.width ?? 0
  return Number.isFinite(w) && w > 0 ? w : 0
}

/**
 * 返回窗口宽度断点,供 App.vue 决定使用 MobileLayout 还是 DesktopLayout。
 *
 * <p>两条额外的保险,专门对付「手机软键盘把布局挤成桌面端」:
 * ① 屏幕本身窄于 768px 时永远算移动端;
 * ② 输入框聚焦期间不更新宽度,避免打字时布局跳动。
 */
export function useBreakpoint() {
  const initial = typeof window === 'undefined' ? DESKTOP_MIN : measureWidth()
  const width = ref(initial)
  const locked = ref(false)

  const update = () => {
    if (typeof window === 'undefined') return
    // 键盘弹着的时候不量宽度:此时读到的值不可信,冻结当前布局最稳
    if (locked.value || isEditableFocused()) return
    width.value = measureWidth()
  }

  /** 输入框获得/失去焦点时切换冻结:失焦后再量一次,补上期间发生的变化 */
  const onFocusChange = () => {
    locked.value = isEditableFocused()
    if (!locked.value) {
      // 等键盘收起动画结束再量,否则拿到的还是键盘缩小时的值
      window.setTimeout(update, 120)
    }
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', update)
    window.addEventListener('orientationchange', update)
    document.addEventListener('focusin', onFocusChange)
    document.addEventListener('focusout', onFocusChange)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', update)
    window.removeEventListener('orientationchange', update)
    document.removeEventListener('focusin', onFocusChange)
    document.removeEventListener('focusout', onFocusChange)
  })

  const breakpoint = computed<Breakpoint>(() => {
    if (width.value < MOBILE_MAX) return 'mobile'
    if (width.value <= DESKTOP_MIN) return 'tablet'
    return 'desktop'
  })

  /** 屏幕本身是手机尺寸时,无论视口怎么报都算移动端 */
  const smallScreen = computed(() => {
    const sw = typeof window === 'undefined' ? 0 : screenWidth()
    return sw > 0 && sw < MOBILE_MAX
  })

  const isMobile = computed(() => smallScreen.value || width.value < MOBILE_MAX)
  const isDesktop = computed(() => !isMobile.value && width.value >= MOBILE_MAX)

  return { width, breakpoint, isMobile, isDesktop }
}
