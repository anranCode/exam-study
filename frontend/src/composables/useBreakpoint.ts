import { computed, onMounted, ref } from 'vue'

export type Breakpoint = 'mobile' | 'tablet' | 'desktop'

/** 手机断点(< 768px) */
export const MOBILE_MAX = 768
/** 桌面断点(> 1024px) */
export const DESKTOP_MIN = 1024
/**
 * 视口变宽后稳定多久才切到桌面布局。
 *
 * <p>软键盘弹出/收起、移动端地址栏收展都会让视口短暂变化;一变宽就切布局,
 * 做题时会闪一下 208px 的侧栏,输入框还可能因为节点被替换而失焦。
 */
const WIDEN_DELAY = 250

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
 * <p>取 `innerWidth` 与 `documentElement.clientWidth` 里较小的一个:
 * 两者不一致时(移动端地址栏、滚动条、缩放),小的是内容真正能用的宽度。
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

/*
 * 模块级共享状态。
 *
 * <p>App.vue、错题本、题库都会调 useBreakpoint。各自存一份宽度的话,
 * 键盘弹起时可能一份已经翻转、另一份还没翻,页面会出现「半桌面半移动」的错位,
 * 所以这里只维护一份,所有调用方看到同一个值。
 */
const width = ref(typeof window === 'undefined' ? DESKTOP_MIN : measureWidth())
/** 屏幕宽度(不随软键盘变化),用来把手机永久钉在移动端布局 */
const screenW = ref(typeof window === 'undefined' ? 0 : screenWidth())
let widenTimer: number | null = null
let bound = false

function cancelWiden() {
  if (widenTimer != null) {
    clearTimeout(widenTimer)
    widenTimer = null
  }
}

/**
 * 量一次尺寸并决定是否切换布局。
 *
 * <p>变窄立即生效(窗口缩小、横竖屏切换);变宽延迟 {@link WIDEN_DELAY},
 * 让软键盘、地址栏引起的瞬时变化没机会把做题画面切成桌面端。
 */
function refresh() {
  if (typeof window === 'undefined') return
  screenW.value = screenWidth()
  const next = measureWidth()
  // 屏幕本身是手机尺寸时,无论视口怎么报都不切桌面 —— 这是侧栏弹出的根因
  const forcedMobile = screenW.value > 0 && screenW.value < MOBILE_MAX
  const nextIsMobile = forcedMobile || next < MOBILE_MAX

  if (nextIsMobile || next <= width.value) {
    cancelWiden()
    width.value = next
    return
  }
  if (widenTimer != null) return
  widenTimer = window.setTimeout(() => {
    widenTimer = null
    if (isEditableFocused()) return
    width.value = measureWidth()
    screenW.value = screenWidth()
  }, WIDEN_DELAY)
}

/**
 * 视口变化入口。输入框聚焦期间不量:此时浏览器报的尺寸不可信,
 * 打字中途切布局会让输入框失焦、页面跳一下。
 */
function update() {
  if (typeof window === 'undefined') return
  if (isEditableFocused()) return
  refresh()
}

function onFocusChange() {
  if (isEditableFocused()) {
    // 键盘正在弹,取消待执行的「切桌面」
    cancelWiden()
    return
  }
  // 等键盘收起动画结束再量,否则拿到的还是键盘缩小时的尺寸
  window.setTimeout(update, 120)
}

function bind() {
  if (bound || typeof window === 'undefined') return
  bound = true
  update()
  window.addEventListener('resize', update)
  window.addEventListener('orientationchange', update)
  document.addEventListener('focusin', onFocusChange)
  document.addEventListener('focusout', onFocusChange)
}

/**
 * 返回窗口宽度断点,供 App.vue 决定使用 MobileLayout 还是 DesktopLayout。
 *
 * <p>两条额外的保险,专门对付「手机软键盘把布局挤成桌面端」:
 * ① 屏幕本身窄于 768px 时永远算移动端;
 * ② 输入框聚焦期间不更新宽度,变宽还要等 {@link WIDEN_DELAY} 稳定,避免打字时布局跳动。
 */
export function useBreakpoint() {
  // 监听全局只挂一次;应用是单页、根组件常驻,不需要在组件卸载时移除
  onMounted(bind)

  const breakpoint = computed<Breakpoint>(() => {
    if (width.value < MOBILE_MAX) return 'mobile'
    if (width.value <= DESKTOP_MIN) return 'tablet'
    return 'desktop'
  })

  /** 屏幕本身是手机尺寸时,无论视口怎么报都算移动端 */
  const smallScreen = computed(() => screenW.value > 0 && screenW.value < MOBILE_MAX)

  const isMobile = computed(() => smallScreen.value || width.value < MOBILE_MAX)
  const isDesktop = computed(() => !isMobile.value && width.value >= MOBILE_MAX)

  return { width, breakpoint, isMobile, isDesktop }
}
