import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { ReviewItemVO, ReviewQueueStatus, ReviewTypeCountVO } from '@/api/types'
import {
  getReviewItems,
  getReviewTypeCounts,
  markReviewItemMastered,
  removeReviewItem
} from '@/api/review'
import { useTodayStore } from './today'

/**
 * 错题本一次拉多少条。
 *
 * 页面要按科目分组,而分页会让分组语义错乱(第 2 页的题接不到第 1 页的组里),
 * 所以干脆一次取满后端允许的上限(见 03-api-spec.md「size 最大 200」)。
 * 单用户场景下几百条错题足够,不做无限滚动;超过时页面会提示还剩多少。
 */
export const WRONG_PAGE_SIZE = 200

export interface WrongFilters {
  subjectId: number | null
  /** 题型,null = 不限 */
  type: string | null
  /** null = 不限 / LEARNING 未掌握(还在队列)/ MASTERED 已掌握 */
  status: ReviewQueueStatus | null
  /** 只看今天到期的 */
  dueOnly: boolean
}

export interface WrongGroup {
  subjectId: number
  shortName: string | null
  color: string | null
  items: ReviewItemVO[]
}

/**
 * 错题本列表与筛选条件(见 docs/study-exam/frontend/03-state-and-api.md)。
 *
 * <p>**不持久化**:筛选条件属于页面状态,刷新后回到默认(全部、不限)即可。
 */
/**
 * 按科目分组;组内保持后端给的顺序(下次复习时间升序,最该复习的在前)。
 *
 * 提成纯函数是为了让页面能在「只显示前几条」的切片上再分组 ——
 * 手机端一次渲染 90 多张卡片会卡,于是先显示出 20 条,点「显示更多」再加。
 */
export function groupItems(source: ReviewItemVO[]): WrongGroup[] {
  const map = new Map<number, WrongGroup>()
  for (const it of source) {
    let g = map.get(it.subjectId)
    if (!g) {
      g = {
        subjectId: it.subjectId,
        shortName: it.subjectShortName,
        color: it.subjectColor,
        items: []
      }
      map.set(it.subjectId, g)
    }
    g.items.push(it)
  }
  return [...map.values()]
}

export const useWrongStore = defineStore('wrong', () => {
  const items = ref<ReviewItemVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const loaded = ref(false)
  /** 各题型的错题数,给「按题型复习」的下拉用 */
  const typeCounts = ref<ReviewTypeCountVO[]>([])
  /**
   * 默认只看**未掌握**的。
   *
   * 错题本是「还要复习什么」的清单,已掌握的不该混在里面;要看它们切一下筛选即可。
   * 阶段 5 的验收标准也要求「标记已掌握后该题从默认列表消失,切到已掌握筛选能看到」。
   */
  const filters = ref<WrongFilters>({
    subjectId: null,
    type: null,
    status: 'LEARNING',
    dueOnly: false
  })

  const groups = computed<WrongGroup[]>(() => groupItems(items.value))

  /** 已经拿到的是否被截断了(超过一页) */
  const truncated = computed(() => total.value > items.value.length)

  async function fetch() {
    loading.value = true
    try {
      const res = await getReviewItems({
        subjectId: filters.value.subjectId ?? undefined,
        type: filters.value.type ?? undefined,
        status: filters.value.status ?? undefined,
        due: filters.value.dueOnly ? true : undefined,
        page: 1,
        size: WRONG_PAGE_SIZE
      })
      items.value = res.list
      total.value = res.total
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /** 供练习结束时调用:错题本要立刻反映刚答错的题 */
  async function refresh() {
    await Promise.all([fetch(), fetchTypeCounts()])
  }

  /**
   * 拉题型分布。换了科目或状态就该重新拉 —— 下拉里只该出现「当前范围下确实有错题」的题型,
   * 计数也要和列表用同一个状态口径,否则切到「已掌握」会看到一堆点了就空的题型。
   */
  async function fetchTypeCounts() {
    try {
      typeCounts.value = await getReviewTypeCounts(
        filters.value.subjectId ?? undefined,
        filters.value.status ?? undefined
      )
    } catch {
      // 题型分布只是筛选辅助,失败不该阻塞错题列表
      typeCounts.value = []
    }
  }

  function setFilters(patch: Partial<WrongFilters>) {
    const next = { ...filters.value, ...patch }
    // 换科目后,原来选的题型可能在新科目下根本没有错题 → 直接清掉,免得筛出空列表
    if (patch.subjectId !== undefined && patch.subjectId !== filters.value.subjectId) {
      next.type = null
    }
    filters.value = next
    void fetch()
    if (patch.subjectId !== undefined || patch.status !== undefined) void fetchTypeCounts()
  }

  /** 回到默认视图:全部科目 / 全部题型、只看未掌握、不限定到期 */
  function resetFilters() {
    filters.value = { subjectId: null, type: null, status: 'LEARNING', dueOnly: false }
    void fetch()
    void fetchTypeCounts()
  }

  /**
   * 标记已掌握。成功后就地更新而不是整页重拉:
   * 当前筛选下这条题还该不该留着,取决于筛的是不是「未掌握」或「只看到期」——
   * 已出队的题两种情况都不该再出现。
   */
  async function markMastered(questionId: number) {
    await markReviewItemMastered(questionId)
    const stillVisible = filters.value.status !== 'LEARNING' && !filters.value.dueOnly
    items.value = stillVisible
      ? items.value.map((it) =>
          it.questionId === questionId
            ? {
                ...it,
                status: 'MASTERED' as const,
                masteryStage: 'MASTERED' as const,
                masteryLabel: '已掌握',
                due: false
              }
            : it
        )
      : items.value.filter((it) => it.questionId !== questionId)
    if (!stillVisible) total.value = Math.max(0, total.value - 1)
    // 顶部到期数、底部 Tab 角标、题型下拉的计数都要跟着变
    void useTodayStore().refresh()
    void fetchTypeCounts()
  }

  /** 移出错题本:这条从列表里彻底消失 */
  async function remove(questionId: number) {
    await removeReviewItem(questionId)
    const before = items.value.length
    items.value = items.value.filter((it) => it.questionId !== questionId)
    if (items.value.length < before) total.value = Math.max(0, total.value - 1)
    void useTodayStore().refresh()
    void fetchTypeCounts()
  }

  return {
    items,
    total,
    loading,
    loaded,
    typeCounts,
    filters,
    groups,
    truncated,
    fetch,
    fetchTypeCounts,
    refresh,
    setFilters,
    resetFilters,
    markMastered,
    remove
  }
})
