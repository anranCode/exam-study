import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getDueSummary } from '@/api/review'
import { getOverview } from '@/api/stats'
import { getOngoingSession, getPracticeSessions } from '@/api/practice'
import { PRACTICE_MODE_LOWER } from '@/utils/constants'
import type {
  DueSubject,
  DueSummaryVO,
  LastSessionVO,
  OngoingSessionVO,
  StatsOverviewVO
} from '@/api/types'

export const useTodayStore = defineStore('today', () => {
  const dueSummary = ref<DueSummaryVO | null>(null)
  const overview = ref<StatsOverviewVO | null>(null)
  const lastSession = ref<LastSessionVO | null>(null)
  const ongoingSession = ref<OngoingSessionVO | null>(null)
  const ongoingSessionId = ref<number | null>(null)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)

  const dueTotal = computed(() => dueSummary.value?.totalDue ?? 0)
  const dueBySubject = computed<DueSubject[]>(() => dueSummary.value?.bySubject ?? [])
  const checkedIn = computed(() => dueSummary.value?.checkedInToday ?? false)
  const streakDays = computed(() => overview.value?.streakDays ?? 0)
  const totalQuestions = computed(() => overview.value?.totalQuestions ?? 0)
  const todayAnswered = computed(() => overview.value?.todayAnswered ?? 0)
  const newQuestionQuota = computed(() => dueSummary.value?.newQuestionQuota ?? 0)
  const ongoingRemaining = computed(() => {
    const s = ongoingSession.value
    if (!s) return 0
    return Math.max(0, s.totalCount - s.answeredCount)
  })

  /**
   * 拉取今日汇总。关键数据失败时记录 error 供页面降级(空态);
   * 未完成场次与最近练习属于锦上添花,失败不影响主流程。
   */
  async function refresh(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const [due, stats] = await Promise.all([getDueSummary(), getOverview()])
      dueSummary.value = due
      overview.value = stats
      loaded.value = true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载失败'
    } finally {
      loading.value = false
    }

    await Promise.all([refreshOngoing(), refreshLastSession()])
  }

  async function refreshOngoing(): Promise<void> {
    try {
      const ongoing = await getOngoingSession()
      ongoingSession.value = ongoing
      ongoingSessionId.value = ongoing?.sessionId ?? null
    } catch {
      ongoingSession.value = null
      ongoingSessionId.value = null
    }
  }

  /** 最近一次已完成的练习(取最近几条里第一条 FINISHED) */
  async function refreshLastSession(): Promise<void> {
    try {
      const page = await getPracticeSessions({ page: 1, size: 3, sort: 'startedAt,desc' })
      const item = page.list.find((s) => s.status === 'FINISHED')
      lastSession.value = item
        ? {
            sessionId: item.id,
            mode: PRACTICE_MODE_LOWER[item.mode] ?? 'daily',
            date: item.startedAt,
            score: item.score ?? 0,
            totalScore: item.totalScore ?? 0,
            accuracy: item.accuracy
          }
        : null
    } catch {
      lastSession.value = null
    }
  }

  return {
    dueSummary,
    overview,
    lastSession,
    ongoingSession,
    ongoingSessionId,
    loading,
    loaded,
    error,
    dueTotal,
    dueBySubject,
    checkedIn,
    streakDays,
    totalQuestions,
    todayAnswered,
    newQuestionQuota,
    ongoingRemaining,
    refresh
  }
})