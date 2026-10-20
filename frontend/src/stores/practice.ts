import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  AnswerRecord,
  AnswerResultVO,
  Confidence,
  PracticeMode,
  PracticeQuestionVO,
  PracticeSessionCreateDTO,
  PracticeSessionVO,
  PracticeStatus,
  SessionSummaryVO
} from '@/api/types'
import {
  createPracticeSession,
  finishPracticeSession,
  submitPracticeAnswer,
  submitSelfScore as submitSelfScoreApi
} from '@/api/practice'
import { PRACTICE_MODE_UPPER } from '@/utils/constants'
import { useTodayStore } from './today'
import { useSubjectStore } from './subject'
import { useWrongStore } from './wrong'

const STORAGE_KEY = 'practice:session'

/** 持久化到 localStorage 的场次快照 */
interface PersistedSession {
  sessionId: number
  mode: PracticeMode
  questions: PracticeQuestionVO[]
  currentIndex: number
  answeredMap: Record<number, AnswerRecord>
  startedAt: number
  elapsedSeconds: number
}

/** 把选中字母规范成后端答案格式:多选升序连写,其余取单个字母 */
export function normalizeAnswer(type: string, keys: string[]): string | null {
  if (keys.length === 0) return null
  if (type === 'MULTIPLE_CHOICE') return [...keys].sort().join('')
  return keys[0]
}

/**
 * 当前练习场次 —— 答题状态机(阶段 4 核心)。
 * answeredMap 是判定状态的唯一来源;切换路由不销毁 store;逐题立即提交。
 */
export const usePracticeStore = defineStore('practice', () => {
  const sessionId = ref<number | null>(null)
  const mode = ref<PracticeMode | null>(null)
  const questions = ref<PracticeQuestionVO[]>([])
  const currentIndex = ref(0)
  const answeredMap = ref<Record<number, AnswerRecord>>({})
  const status = ref<PracticeStatus>('IDLE')
  const startedAt = ref(0)
  const elapsedSeconds = ref(0)
  /** 当前题在本地选中的选项(仅提交前有效) */
  const selectedKeys = ref<string[]>([])
  /** 本轮汇总(finishSession 后供 ResultView 使用) */
  const summary = ref<SessionSummaryVO | null>(null)
  /** 创建场次时后端告知的历史未完成场次 */
  const ongoingSessionId = ref<number | null>(null)

  const total = computed(() => questions.value.length)
  const currentQuestion = computed<PracticeQuestionVO | null>(
    () => questions.value[currentIndex.value] ?? null
  )
  const currentRecord = computed<AnswerRecord | null>(() => {
    const q = currentQuestion.value
    return q ? answeredMap.value[q.id] ?? null : null
  })
  const answeredCount = computed(() => Object.keys(answeredMap.value).length)
  const remainingCount = computed(() => Math.max(0, total.value - answeredCount.value))
  const isLastQuestion = computed(() => currentIndex.value >= total.value - 1)

  function persist() {
    if (typeof localStorage === 'undefined') return
    if (sessionId.value == null || mode.value == null) return
    const data: PersistedSession = {
      sessionId: sessionId.value,
      mode: mode.value,
      questions: questions.value,
      currentIndex: currentIndex.value,
      answeredMap: answeredMap.value,
      startedAt: startedAt.value,
      elapsedSeconds: elapsedSeconds.value
    }
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
    } catch {
      /* 隐私模式下可能写入失败,忽略即可 */
    }
  }

  function clearStorage() {
    if (typeof localStorage === 'undefined') return
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      /* 忽略 */
    }
  }

  /** 开始一场新练习并持久化 */
  async function startSession(
    modeArg: PracticeMode,
    options: Omit<PracticeSessionCreateDTO, 'mode'> = {}
  ): Promise<PracticeSessionVO> {
    const payload: PracticeSessionCreateDTO = { mode: PRACTICE_MODE_UPPER[modeArg], ...options }
    const vo = await createPracticeSession(payload)
    sessionId.value = vo.sessionId
    mode.value = modeArg
    questions.value = vo.questions ?? []
    currentIndex.value = 0
    answeredMap.value = {}
    selectedKeys.value = []
    summary.value = null
    ongoingSessionId.value = vo.ongoingSessionId ?? null
    startedAt.value = Date.now()
    elapsedSeconds.value = 0
    status.value = questions.value.length > 0 ? 'ANSWERING' : 'FINISHED'
    persist()
    return vo
  }

  /** 仅记录本地选中,不提交 */
  function selectOption(key: string) {
    if (status.value !== 'ANSWERING') return
    const q = currentQuestion.value
    if (!q) return
    if (q.type === 'MULTIPLE_CHOICE') {
      const set = new Set(selectedKeys.value)
      if (set.has(key)) set.delete(key)
      else set.add(key)
      selectedKeys.value = [...set].sort()
    } else {
      selectedKeys.value = [key]
    }
  }

  /** 清空本地选中(点「不知道」时用,避免残留选择被当成有效作答) */
  function clearSelection() {
    selectedKeys.value = []
  }

  /**
   * 提交当前题作答。成功写入 answeredMap(status → GRADED);
   * 失败恢复到 ANSWERING 并抛出,调用方必须停留在当前题让用户重试。
   */
  async function submitAnswer(
    confidence: Confidence,
    userAnswerOverride?: string | null,
    spentSeconds?: number
  ): Promise<AnswerResultVO> {
    const q = currentQuestion.value
    if (!q || sessionId.value == null) throw new Error('没有正在作答的题目')
    const existed = answeredMap.value[q.id]
    if (existed) return existed.result
    const userAnswer =
      userAnswerOverride !== undefined
        ? userAnswerOverride
        : normalizeAnswer(q.type, selectedKeys.value)
    status.value = 'GRADING'
    try {
      const result = await submitPracticeAnswer(sessionId.value, {
        questionId: q.id,
        userAnswer,
        confidence,
        spentSeconds
      })
      answeredMap.value = {
        ...answeredMap.value,
        [q.id]: { questionId: q.id, selected: [...selectedKeys.value], confidence, result }
      }
      status.value = 'GRADED'
      persist()
      return result
    } catch (e) {
      status.value = 'ANSWERING'
      throw e
    }
  }

  /** 主观题自评:勾选数即 selfScore */
  async function submitSelfScore(selfScore: number, totalPoints: number): Promise<AnswerResultVO> {
    const q = currentQuestion.value
    if (!q || sessionId.value == null) throw new Error('没有正在作答的题目')
    const existed = answeredMap.value[q.id]
    const result = await submitSelfScoreApi(sessionId.value, q.id, { selfScore, totalPoints })
    answeredMap.value = {
      ...answeredMap.value,
      [q.id]: {
        questionId: q.id,
        selected: existed?.selected ?? [],
        confidence: existed?.confidence ?? 'SURE',
        result
      }
    }
    status.value = 'GRADED'
    persist()
    return result
  }

  /** 下一题;已是最后一题则结束本轮并返回汇总 */
  async function nextQuestion(): Promise<SessionSummaryVO | null> {
    if (currentIndex.value < questions.value.length - 1) {
      currentIndex.value += 1
      selectedKeys.value = []
      const next = currentQuestion.value
      status.value = next && answeredMap.value[next.id] ? 'GRADED' : 'ANSWERING'
      persist()
      return null
    }
    return finishSession()
  }

  /** 结束练习:刷新今日页与科目数字,清空本地进度 */
  async function finishSession(): Promise<SessionSummaryVO> {
    if (sessionId.value == null) throw new Error('没有进行中的场次')
    const result = await finishPracticeSession(sessionId.value)
    summary.value = result
    elapsedSeconds.value =
      result.durationSeconds ?? Math.max(0, Math.round((Date.now() - startedAt.value) / 1000))
    status.value = 'FINISHED'
    clearStorage()
    // 今日页、科目数字、错题本都必须立刻反映最新状态
    void useTodayStore().refresh()
    void useSubjectStore().fetchSubjects(true)
    void useWrongStore().refresh()
    return result
  }

  /** 页面刷新后从 localStorage 恢复进度;返回是否恢复成功 */
  function restoreFromStorage(): boolean {
    if (typeof localStorage === 'undefined') return false
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return false
    try {
      const data = JSON.parse(raw) as PersistedSession
      if (!data || typeof data.sessionId !== 'number' || !Array.isArray(data.questions)) {
        clearStorage()
        return false
      }
      if (data.questions.length === 0) {
        clearStorage()
        return false
      }
      sessionId.value = data.sessionId
      mode.value = data.mode
      questions.value = data.questions
      currentIndex.value = Math.min(Math.max(0, data.currentIndex ?? 0), data.questions.length - 1)
      answeredMap.value = data.answeredMap ?? {}
      startedAt.value = data.startedAt || Date.now()
      elapsedSeconds.value = data.elapsedSeconds ?? 0
      selectedKeys.value = []
      summary.value = null
      const current = currentQuestion.value
      status.value = current && answeredMap.value[current.id] ? 'GRADED' : 'ANSWERING'
      return true
    } catch {
      clearStorage()
      return false
    }
  }

  /** 丢弃当前场次(重新开始 / 退出练习时使用) */
  function reset() {
    sessionId.value = null
    mode.value = null
    questions.value = []
    currentIndex.value = 0
    answeredMap.value = {}
    selectedKeys.value = []
    status.value = 'IDLE'
    startedAt.value = 0
    elapsedSeconds.value = 0
    summary.value = null
    ongoingSessionId.value = null
    clearStorage()
  }

  /** 计时器可选调用:更新已用时 */
  function tick() {
    if (status.value === 'IDLE' || startedAt.value === 0) return
    elapsedSeconds.value = Math.max(0, Math.round((Date.now() - startedAt.value) / 1000))
  }

  return {
    sessionId,
    mode,
    questions,
    currentIndex,
    answeredMap,
    status,
    startedAt,
    elapsedSeconds,
    selectedKeys,
    summary,
    ongoingSessionId,
    total,
    currentQuestion,
    currentRecord,
    answeredCount,
    remainingCount,
    isLastQuestion,
    startSession,
    selectOption,
    clearSelection,
    submitAnswer,
    submitSelfScore,
    nextQuestion,
    finishSession,
    restoreFromStorage,
    reset,
    tick
  }
})
