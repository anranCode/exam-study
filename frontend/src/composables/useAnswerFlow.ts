import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { usePracticeStore } from '@/stores/practice'
import { MASTER_STREAK, isObjective, isAutoSubmit } from '@/utils/constants'
import type {
  AnswerRecord,
  AnswerResultVO,
  Confidence,
  PracticeQuestionVO,
  SessionSummaryVO
} from '@/api/types'

/**
 * 答题流程:点选项 → 立即判定 → 答对自动下一题 / 答错停在原地。
 *
 * <p>交互约定(与产品需求一致):
 * <ul>
 *   <li>单选题类:点选项即提交,不需要再点「确认」——少一次点击;</li>
 *   <li>答对:短暂展示解析后自动进入下一题(倒计时期间也能手动点「下一题」);</li>
 *   <li>答错:停留在当前题,由用户自己点「下一题」,给足看解析的时间;</li>
 *   <li>「不知道」:直接提交空答案,展示正确答案并记为错题,不占用「先看答案」的语义;</li>
 *   <li>多选 / 填空:必须点「提交答案」(或填空框回车),因为一次点击/一次按键不是完整作答;</li>
 *   <li>主观题:写答案 → 对照要点 → 勾选自评;点「不知道」则直接 0 分入错题本。</li>
 * </ul>
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
  /** 点一下选项就判题的题型(单选、判断类):不需要「提交答案」按钮 */
  const autoSubmit = computed(() => (question.value ? isAutoSubmit(question.value.type) : false))

  /** 主观题:提交文本作答 */
  const selfAnswer = ref('')
  /** 主观题:已勾选的要点下标 */
  const checkedPoints = ref<number[]>([])
  const submitError = ref<string | null>(null)
  const questionShownAt = ref(Date.now())
  /** 记录最近一次提交用的信心度,重试时保持一致(影响复习排期) */
  let lastConfidence: Confidence = 'SURE'
  /** 最近一次操作:提交失败后「重试」要照着原路径再来一遍 */
  let lastAction: 'answer' | 'giveUp' | 'reveal' = 'answer'
  /**
   * 连续答对满 MASTER_STREAK 次时后端会把题置为已掌握(移出错题本),
   * 这里给一次明确提示 —— 否则题目会从列表里「悄悄消失」。
   */
  function notifyMastered(result: AnswerResultVO) {
    if (result?.mastered) {
      ElMessage.success(`连续答对 ${MASTER_STREAK} 次,已移出错题本`)
    }
  }
  /**
   * 本次提交是否该自动进入下一题。
   *
   * <p>只由「刚提交成功」置位,而不是由「当前题已有判定结果」推导 ——
   * 后者在刷新恢复、回看已答题时也会为真,会导致页面一进来就自己跳走。
   */
  const autoNextPending = ref(false)

  /** 主观题是否已自评完成 */
  const selfScored = computed(() => record.value?.result.selfScore != null)
  const answerPoints = computed<string[]>(() => record.value?.result.answerPoints ?? [])

  watch(
    () => question.value?.id,
    () => {
      selfAnswer.value = ''
      checkedPoints.value = []
      submitError.value = null
      autoNextPending.value = false
      questionShownAt.value = Date.now()
    },
    { immediate: true }
  )

  function spentSeconds(): number {
    return Math.max(0, Math.round((Date.now() - questionShownAt.value) / 1000))
  }

  /**
   * 选中一个选项。单选 / 判断类点到即判定;多选只记录,等用户点「提交答案」。
   */
  function select(key: string) {
    if (graded.value || submitting.value) return
    store.selectOption(key)
    if (autoSubmit.value) {
      void submit('SURE')
    }
  }

  /** 客观题提交:confidence = 'SURE'(正常作答)/ 'UNSURE'(没把握) */
  async function submit(confidence: Confidence = 'SURE') {
    if (graded.value || submitting.value) return
    lastConfidence = confidence
    lastAction = 'answer'
    submitError.value = null
    autoNextPending.value = false
    try {
      const result = await store.submitAnswer(confidence, undefined, spentSeconds())
      notifyMastered(result)
      // 答对 → 自动下一题;答错 → 停在本页等用户自己点下一题
      autoNextPending.value = result.isCorrect === true
    } catch (e) {
      submitError.value = e instanceof Error ? e.message : '提交失败'
    }
  }

  /**
   * 「不知道」:提交空答案。
   *
   * <p>后端据此判错并写入错题本(wrong_count +1),响应里带回正确答案与解析,
   * 所以点完立刻能看到答案。主观题没有自动判分,这里顺带按 0 分自评一次,
   * 同样把它记成错题并展开参考答案要点。
   */
  async function giveUp() {
    if (graded.value || submitting.value) return
    lastConfidence = 'UNSURE'
    lastAction = 'giveUp'
    submitError.value = null
    autoNextPending.value = false
    store.clearSelection()
    // 主观题已经写下的文本也不该被当成有效作答,统一清空,避免「我写了字却 0 分」的误解
    selfAnswer.value = ''
    try {
      const result = await store.submitAnswer('UNSURE', null, spentSeconds())
      notifyMastered(result)
      if (isSubjective.value) {
        const scored = await store.submitSelfScore(0, answerPoints.value.length)
        notifyMastered(scored)
      }
    } catch (e) {
      submitError.value = e instanceof Error ? e.message : '提交失败'
    }
  }

  /** 主观题:「对照答案要点」= 提交文本作答并取回要点 */
  async function revealPoints() {
    if (graded.value || submitting.value) return
    lastConfidence = 'SURE'
    lastAction = 'reveal'
    submitError.value = null
    try {
      const result = await store.submitAnswer('SURE', selfAnswer.value.trim(), spentSeconds())
      notifyMastered(result)
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
      const result = await store.submitSelfScore(
        checkedPoints.value.length,
        answerPoints.value.length
      )
      notifyMastered(result)
    } catch (e) {
      submitError.value = e instanceof Error ? e.message : '提交失败'
    }
  }

  /** 下一题;最后一题返回本轮汇总 */
  async function next(): Promise<SessionSummaryVO | null> {
    autoNextPending.value = false
    return store.nextQuestion()
  }

  /** 提交失败后重试当前题:照着上一次操作重来(答过 / 不知道 / 对照要点 / 补自评) */
  function retry() {
    // 主观题的「不知道」分两步(提交空答案 → 0 分自评);若第二步失败,直接补自评
    if (isSubjective.value && graded.value && !selfScored.value) {
      void submitSelf()
      return
    }
    if (lastAction === 'giveUp') void giveUp()
    else if (isSubjective.value) void revealPoints()
    else void submit(lastConfidence)
  }

  return {
    store,
    question,
    isSubjective,
    autoSubmit,
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
    autoNextPending,
    select,
    submit,
    giveUp,
    revealPoints,
    togglePoint,
    submitSelf,
    next,
    retry
  }
}
