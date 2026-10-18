import { computed, ref, watch } from 'vue'
import { usePracticeStore } from '@/stores/practice'
import { isObjective } from '@/utils/constants'
import type { AnswerRecord, Confidence, PracticeQuestionVO, SessionSummaryVO } from '@/api/types'

/**
 * 答题流程:选中 → 提交 → 展示解析 → 下一题。
 * 状态机本体在 usePracticeStore;这里只负责「当前题」的 UI 流程与主观题本地状态
 * (多行作答、要点勾选、提交中的 loading、失败重试)。
 */
export function useAnswerFlow() {
  const store = usePracticeStore()

  const question = computed<PracticeQuestionVO | null>(() => store.currentQuestion)
  const isSubjective = computed(() => (question.value ? !isObjective(question.value.type) : false))
  const record = computed<AnswerRecord | null>(() => store.currentRecord)
  const graded = computed(() => !!record.value)
  const submitting = computed(() => store.status === 'GRADING')
  const selectedKeys = computed(() => store.selectedKeys)
  /** 选择题要选中一项;填空类(完形补文)要填了非空内容才算能提交 */
  const canConfirm = computed(() => selectedKeys.value.some((k) => k.trim().length > 0))

  /** 主观题:提交文本作答 */
  const selfAnswer = ref('')
  /** 主观题:已勾选的要点下标 */
  const checkedPoints = ref<number[]>([])
  const submitError = ref<string | null>(null)
  const questionShownAt = ref(Date.now())
  /** 记录最近一次提交用的信心度,重试时保持一致(影响复习排期) */
  let lastConfidence: Confidence = 'SURE'

  /** 主观题是否已自评完成 */
  const selfScored = computed(() => record.value?.result.selfScore != null)
  const answerPoints = computed<string[]>(() => record.value?.result.answerPoints ?? [])

  watch(
    () => question.value?.id,
    () => {
      selfAnswer.value = ''
      checkedPoints.value = []
      submitError.value = null
      questionShownAt.value = Date.now()
    },
    { immediate: true }
  )

  function spentSeconds(): number {
    return Math.max(0, Math.round((Date.now() - questionShownAt.value) / 1000))
  }

  function select(key: string) {
    if (graded.value) return
    store.selectOption(key)
  }

  /** 客观题提交:confidence = 'SURE'(确认)/ 'UNSURE'(不太确定) */
  async function submit(confidence: Confidence = 'SURE') {
    if (graded.value || submitting.value) return
    lastConfidence = confidence
    submitError.value = null
    try {
      await store.submitAnswer(confidence, undefined, spentSeconds())
    } catch (e) {
      submitError.value = e instanceof Error ? e.message : '提交失败'
    }
  }

  /** 主观题:「对照答案要点」= 提交文本作答并取回要点 */
  async function revealPoints() {
    if (graded.value || submitting.value) return
    lastConfidence = 'SURE'
    submitError.value = null
    try {
      await store.submitAnswer('SURE', selfAnswer.value.trim(), spentSeconds())
    } catch (e) {
      submitError.value = e instanceof Error ? e.message : '提交失败'
    }
  }

  function togglePoint(index: number) {
    if (selfScored.value) return
    const set = new Set(checkedPoints.value)
    if (set.has(index)) set.delete(index)
    else set.add(index)
    checkedPoints.value = [...set].sort((a, b) => a - b)
  }

  /** 主观题:提交自评,勾选数即得分 */
  async function submitSelf() {
    if (submitting.value) return
    submitError.value = null
    try {
      await store.submitSelfScore(checkedPoints.value.length, answerPoints.value.length)
    } catch (e) {
      submitError.value = e instanceof Error ? e.message : '提交失败'
    }
  }

  /** 下一题;最后一题返回本轮汇总 */
  async function next(): Promise<SessionSummaryVO | null> {
    return store.nextQuestion()
  }

  /** 提交失败后重试当前题(客观题沿用上次信心度,主观题重发文本) */
  function retry() {
    if (isSubjective.value) void revealPoints()
    else void submit(lastConfidence)
  }

  return {
    store,
    question,
    isSubjective,
    record,
    graded,
    submitting,
    selectedKeys,
    canConfirm,
    selfAnswer,
    checkedPoints,
    answerPoints,
    selfScored,
    submitError,
    select,
    submit,
    revealPoints,
    togglePoint,
    submitSelf,
    next,
    retry
  }
}