<script setup lang="ts">
/**
 * 刷题页(阶段 4 ★ 核心)。三段式:顶部进度 → 题干与选项(可滚动)→ 底部固定操作区。
 *
 * <p>交互逻辑:点选项即判定 —— 答对自动进入下一题(1.2 秒后,也可手动点),
 * 答错停留在当前题由用户自己点「下一题」;另有「不知道」直接看答案并记入错题本。
 * 提交失败停留在当前题并给出重试;判定后不可改选;主观题单独分支。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import QuestionCard from '@/components/QuestionCard.vue'
import { usePracticeStore } from '@/stores/practice'
import { useTodayStore } from '@/stores/today'
import { useAnswerFlow } from '@/composables/useAnswerFlow'
import { PRACTICE_MODE_LABEL } from '@/utils/constants'
import { splitAnalysis } from '@/utils/analysis'
import type { PracticeMode, PracticeSessionCreateDTO } from '@/api/types'

const route = useRoute()
const router = useRouter()
const store = usePracticeStore()
const todayStore = useTodayStore()

const {
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
} = useAnswerFlow()

const VALID_MODES: PracticeMode[] = ['daily', 'random', 'chapter', 'mock', 'review', 'wrong']

const loading = ref(false)
const startError = ref<string | null>(null)
const noQuestions = ref(false)
/** 交卷/超时只处理一次 */
const handedIn = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
/** 答对后自动翻页的定时器 */
let autoNextTimer: ReturnType<typeof setTimeout> | null = null

/** 答对后停留多久再自动翻页:够看清「答对了」,又不至于让人觉得卡住 */
const AUTO_NEXT_DELAY = 1200

const modeParam = computed(() => String(route.params.mode ?? '').toLowerCase())
/** 模拟考试:限时、组卷来自 /mock 页,作答过程中不显示对错 */
const examMode = computed(() => modeParam.value === 'mock')
const modeLabel = computed(() => PRACTICE_MODE_LABEL[modeParam.value as PracticeMode] ?? '练习')
const nextLabel = computed(() => (store.isLastQuestion ? '查看本轮结果' : '下一题'))
const progressValue = computed(() => store.currentIndex + (graded.value ? 1 : 0))
/**
 * AI 解析按「【参考答案】/【译文】/【解析】」分块展示:先给能直接背的答案,再给解析。
 * 没有板块标题的旧解析/手写解析会作为一个整段返回,不会丢内容。
 */
const analysisSections = computed(() => splitAnalysis(record.value?.result.analysis))

/** 模拟考试剩余秒数;普通练习为 null */
const remainingSeconds = computed<number | null>(() => {
  if (!examMode.value || store.durationMin == null) return null
  return Math.max(0, store.durationMin * 60 - store.elapsedSeconds)
})

const timeText = computed(() => {
  const s = remainingSeconds.value
  if (s == null) return ''
  const m = Math.floor(s / 60)
  return `${m}:${String(s % 60).padStart(2, '0')}`
})

function numOrNull(value: unknown): number | null {
  if (value == null || value === '') return null
  const n = Number(Array.isArray(value) ? value[0] : value)
  return Number.isFinite(n) ? n : null
}

/** 确认退出:本次练习已自动保存 */
async function handleExit() {
  try {
    await ElMessageBox.confirm('本次练习已自动保存,确定退出?', '退出练习', {
      confirmButtonText: '退出',
      cancelButtonText: '继续答题',
      type: 'warning'
    })
  } catch {
    return
  }
  if (window.history.state?.back) router.back()
  else router.push('/')
}

async function startNew(modeLower: PracticeMode) {
  loading.value = true
  startError.value = null
  noQuestions.value = false
  try {
    const options: Omit<PracticeSessionCreateDTO, 'mode'> = {}
    const subjectId = numOrNull(route.query.subjectId)
    const chapterId = numOrNull(route.query.chapterId)
    const paperId = numOrNull(route.query.paperId)
    if (modeLower === 'daily') {
      if (!todayStore.loaded) {
        try {
          await todayStore.refresh()
        } catch {
          /* 失败时按后端默认题量开始 */
        }
      }
      if (todayStore.newQuestionQuota > 0) options.count = todayStore.newQuestionQuota
    } else if (modeLower === 'random' || modeLower === 'wrong') {
      // 自由练习会带 subjectId / types / count 过来;不带就是原来的「随机 10 题」。
      // 错题练习(wrong)参数完全一样,区别只在后端抽题池:它从错题池随机抽,不看排期。
      options.count = numOrNull(route.query.count) ?? 10
      options.subjectId = subjectId
      const types = String(route.query.types ?? '')
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean)
      if (types.length) options.types = types
    } else if (modeLower === 'chapter') {
      options.subjectId = subjectId
      options.chapterId = chapterId
    } else if (modeLower === 'review') {
      options.subjectId = subjectId
      // 错题本里「立即复习」某一道:直接点名这一题,后端会忽略组题模式照单出题
      const questionId = numOrNull(route.query.questionId)
      if (questionId != null) options.questionIds = [questionId]
      // 错题本支持「按科目 + 题型」挑着复习:不带 questionId 时这两个筛选要传给后端
      const types = String(route.query.types ?? '')
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean)
      if (types.length) options.types = types
      const count = numOrNull(route.query.count)
      if (count != null) options.count = count
    } else if (modeLower === 'mock') {
      options.paperId = paperId
      // 按题型结构组卷时必须带上科目:抽题池就是「这个科目里对应题型的已发布题」
      options.subjectId = subjectId
      // 模拟测试(新入口):按题型结构组卷,如 sections=SINGLE_CHOICE:25,SHORT_ANSWER:5
      const sections = String(route.query.sections ?? '')
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
        .map((s) => {
          const [type, count] = s.split(':')
          return { type: (type ?? '').trim(), count: Number(count) }
        })
        .filter((s) => s.type && Number.isFinite(s.count) && s.count > 0)
      if (sections.length) options.sections = sections
      const duration = numOrNull(route.query.duration)
      if (duration != null) options.durationMin = duration
    }
    const vo = await store.startSession(modeLower, options)
    noQuestions.value = !vo.questions || vo.questions.length === 0
  } catch (e) {
    startError.value = e instanceof Error ? e.message : '无法开始练习'
  } finally {
    loading.value = false
  }
}

async function boot() {
  const m = modeParam.value
  if (!VALID_MODES.includes(m as PracticeMode)) {
    ElMessage.error('未知的练习模式')
    router.replace('/')
    return
  }
  const modeLower = m as PracticeMode
  // 直接访问 /practice/mock 而没有组卷参数时,回到模拟考试设置页
  if (
    modeLower === 'mock' &&
    numOrNull(route.query.paperId) == null &&
    route.query.sections == null
  ) {
    router.replace('/mock')
    return
  }
  startError.value = null
  noQuestions.value = false

  // 「点名要开新的一场」的两种入口:错题本里立即复习某一题,以及自由练习带筛选参数进来。
  // 这两种都不能走下面的「继续上次」——否则会复用上一场的题目,筛选条件形同虚设。
  const customRandom =
    modeLower === 'random' &&
    (route.query.types != null || route.query.subjectId != null || route.query.count != null)
  // 错题复习同理:按科目 / 题型 / 题量挑着练时,必须按新筛选重新组题
  const customReview =
    modeLower === 'review' &&
    (route.query.types != null || route.query.subjectId != null || route.query.count != null)
  // 错题练习(wrong)每次都是「按当前筛选抽一批新题」,一律重新组题
  const customWrong =
    modeLower === 'wrong' &&
    (route.query.types != null || route.query.subjectId != null || route.query.count != null)
  // 从模拟考试设置页点「开始」会带 fresh=1:只有这时才开新场;
  // 刷新练习页没有 fresh,交给下面的「继续上次」逻辑恢复,别把答题进度冲掉。
  if (modeLower === 'mock' && route.query.fresh === '1') {
    store.reset()
    const query = { ...route.query }
    delete query.fresh
    void router.replace({ path: route.path, query })
    await startNew(modeLower)
    return
  }
  if (numOrNull(route.query.questionId) != null || customRandom || customReview || customWrong) {
    store.reset()
    await startNew(modeLower)
    return
  }

  // 内存里已有同模式的进行中场次 → 直接继续
  if (
    store.sessionId != null &&
    store.status !== 'FINISHED' &&
    store.mode === modeLower &&
    store.total > 0
  ) {
    return
  }

  // 从 localStorage 恢复
  if (store.status === 'IDLE' || store.sessionId == null) {
    const restored = store.restoreFromStorage()
    if (restored && store.mode === modeLower && store.remainingCount > 0) {
      // 从今日页「继续」进来时不再二次询问
      if (route.query.resume === '1') return
      try {
        await ElMessageBox.confirm(`还剩 ${store.remainingCount} 题,继续?`, '继续上次练习', {
          confirmButtonText: '继续',
          cancelButtonText: '重新开始',
          type: 'info',
          distinguishCancelAndClose: true
        })
        return
      } catch {
        store.reset()
      }
    } else if (restored) {
      store.reset()
    }
  }

  await startNew(modeLower)
}

async function onNext() {
  clearAutoNext()
  try {
    const summary = await next()
    if (summary) {
      await router.push({
        name: 'practice-result',
        params: { mode: modeParam.value, sessionId: String(summary.sessionId) }
      })
    }
  } catch {
    // 请求层已经统一弹过错误提示;这里只兜住异常,别再弹一条重复的。
    // 用户仍停在当前题,可以再点「下一题」重试结束本轮。
  }
}

function clearAutoNext() {
  if (autoNextTimer) {
    clearTimeout(autoNextTimer)
    autoNextTimer = null
  }
}

/**
 * 答对就自动进入下一题。
 *
 * <p>只认 useAnswerFlow 里的 autoNextPending(由「刚刚提交成功且答对」置位),
 * 不用「当前题已有判定结果」推导 —— 否则刷新恢复或回看已答题时会自己跳走。
 */
watch(autoNextPending, (pending) => {
  clearAutoNext()
  // 模拟考试不自动翻页:交卷前保持节奏,也让每题的选择停在自己的位置
  if (!pending || examMode.value) return
  autoNextTimer = setTimeout(() => {
    autoNextTimer = null
    void onNext()
  }, AUTO_NEXT_DELAY)
})

/** 交卷 / 超时:直接结束本场并进入小结 */
async function finishNow() {
  if (store.sessionId == null) return
  try {
    const summary = await store.finishSession()
    await router.push({
      name: 'practice-result',
      params: { mode: modeParam.value, sessionId: String(summary.sessionId) }
    })
  } catch {
    // 请求层已统一提示;停在当前页,用户可再点一次
  }
}

async function handIn() {
  if (store.sessionId == null) return
  try {
    await ElMessageBox.confirm(
      `已答 ${store.answeredCount} / ${store.total} 题,交卷后不能修改。`,
      '确认交卷?',
      { confirmButtonText: '交卷', cancelButtonText: '继续答题', type: 'warning' }
    )
  } catch {
    return
  }
  handedIn.value = true
  await finishNow()
}

// 倒计时归零自动交卷(只触发一次)
watch(remainingSeconds, (seconds) => {
  if (examMode.value && seconds === 0 && !handedIn.value) {
    handedIn.value = true
    ElMessage.warning('考试时间到,已自动交卷')
    void finishNow()
  }
})

/** 输入框回车直接提交(填空类) */
function onSubmitFill() {
  if (!canConfirm.value || submitting.value) return
  void submit('SURE')
}

watch(
  () => store.currentIndex,
  () => {
    clearAutoNext()
    if (typeof window !== 'undefined') window.scrollTo({ top: 0 })
  }
)

watch(
  // 模式 / 点名的那道题 / 自由练习的筛选条件变了,都要重开一场
  () =>
    [
      String(route.params.mode),
      String(route.query.questionId ?? ''),
      String(route.query.types ?? ''),
      String(route.query.subjectId ?? ''),
      String(route.query.count ?? '')
    ].join('|'),
  () => {
    store.reset()
    void boot()
  }
)

onMounted(() => {
  void boot()
  timer = setInterval(() => store.tick(), 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  clearAutoNext()
})
</script>

<template>
  <section class="practice">
    <!-- 顶部:退出 + 进度 -->
    <header class="practice__top">
      <button type="button" class="practice__exit tap-target" @click="handleExit">
        <AppIcon name="chevronRight" :size="18" class="practice__exit-icon" />
        <span>退出</span>
      </button>
      <span class="practice__mode">{{ modeLabel }}</span>
      <span v-if="store.total > 0" class="practice__count mono">
        {{ Math.min(store.currentIndex + 1, store.total) }} / {{ store.total }}
      </span>
      <ProgressBar
        v-if="store.total > 0"
        :value="progressValue"
        :total="store.total"
        :show-text="false"
        class="practice__progress"
      />
    </header>

    <!-- 模拟考试:倒计时 + 交卷 -->
    <div v-if="examMode && store.sessionId != null" class="practice__exam">
      <span
        class="practice__timer mono"
        :class="{ 'practice__timer--low': remainingSeconds != null && remainingSeconds <= 300 }"
      >
        剩余 {{ timeText || '—' }}
      </span>
      <button
        type="button"
        class="practice__handin tap-target"
        :disabled="handedIn"
        @click="handIn"
      >
        交卷
      </button>
    </div>

    <!-- 准备中 -->
    <div v-if="loading" class="practice__loading">
      <el-skeleton :rows="6" animated />
      <p class="practice__hint">正在准备题目…</p>
    </div>

    <!-- 开始失败 -->
    <div v-else-if="startError" class="card practice__state">
      <p class="practice__state-text">{{ startError }}</p>
      <div class="practice__state-actions">
        <button type="button" class="btn btn--primary" @click="boot">重试</button>
        <button type="button" class="btn btn--ghost" @click="router.push('/')">回到今日</button>
      </div>
    </div>

    <!-- 没有可练的题 -->
    <div v-else-if="noQuestions" class="card practice__state">
      <EmptyState
        text="这里暂时没有可以练习的题目。"
        icon="target"
        action-text="回到今日"
        @action="router.push('/')"
      />
    </div>

    <template v-else-if="question">
      <div class="practice__body">
        <QuestionCard
          :question="question"
          :selected-keys="selectedKeys"
          :record="record"
          :reveal="!examMode"
          @select="select"
          @submit="onSubmitFill"
        />

        <!-- 主观题分支 -->
        <section v-if="isSubjective" class="subjective">
          <label class="subjective__label" for="self-answer">你的作答</label>
          <el-input
            id="self-answer"
            v-model="selfAnswer"
            type="textarea"
            :rows="5"
            resize="vertical"
            :disabled="graded"
            placeholder="写下你的答案要点…"
          />

          <template v-if="graded">
            <div v-if="answerPoints.length" class="points">
              <p class="points__hint">对照要点,勾选你确实答到的(勾选数即得分):</p>
              <ul class="points__list">
                <li v-for="(point, i) in answerPoints" :key="i" class="point">
                  <el-checkbox
                    :model-value="checkedPoints.includes(i)"
                    :disabled="selfScored"
                    @change="togglePoint(i)"
                  >
                    <span class="point__text wrap-anywhere">{{ point }}</span>
                  </el-checkbox>
                </li>
              </ul>
              <p v-if="selfScored" class="points__score">
                已提交自评:<span class="mono">{{ record?.result.selfScore }}</span> /
                {{ record?.result.totalPoints ?? answerPoints.length }} 分
              </p>
            </div>

            <!-- 有的主观题只给了参考答案文本、没有要点列表,「不知道」后也得能看到答案 -->
            <section v-if="record?.result.correctAnswer" class="analysis">
              <p class="analysis__hint">参考答案</p>
              <p class="analysis__text wrap-anywhere">{{ record.result.correctAnswer }}</p>
            </section>

            <section v-if="analysisSections.length" class="analysis">
              <div
                v-for="(part, i) in analysisSections"
                :key="i"
                class="analysis__section"
              >
                <p v-if="part.title" class="analysis__hint">{{ part.title }}</p>
                <p class="analysis__text wrap-anywhere">{{ part.body }}</p>
              </div>
            </section>
          </template>
        </section>

        <!-- 提交失败:停留当前题,可重试 -->
        <div v-if="submitError" class="retry" role="alert">
          <span class="retry__text wrap-anywhere">
            {{ submitError }}——本题作答未记录,请重试
          </span>
          <button type="button" class="retry__btn" @click="retry">重试</button>
        </div>
      </div>

      <!-- 底部固定操作区 -->
      <footer class="practice__actions">
        <template v-if="isSubjective">
          <button
            v-if="!graded"
            type="button"
            class="btn btn--primary btn--block"
            :disabled="!selfAnswer.trim() || submitting"
            @click="revealPoints"
          >
            对照答案要点
          </button>
          <button
            v-else-if="!selfScored"
            type="button"
            class="btn btn--primary btn--block"
            :disabled="submitting"
            @click="submitSelf"
          >
            提交自评
          </button>
          <button v-else type="button" class="btn btn--primary btn--block" @click="onNext">
            {{ nextLabel }}
          </button>
          <!-- 主观题也能直接认输:按 0 分自评并展开参考答案,同时记入错题本;模拟考试不给这个入口 -->
          <button
            v-if="!graded && !examMode"
            type="button"
            class="btn btn--ghost"
            :disabled="submitting"
            @click="giveUp"
          >
            不知道
          </button>
        </template>

        <template v-else>
          <template v-if="!graded">
            <button
              v-if="!examMode"
              type="button"
              class="btn btn--ghost"
              :disabled="submitting"
              @click="giveUp"
            >
              不知道
            </button>
            <!-- 单选 / 判断类点选项即判定,不需要提交按钮;多选与填空仍需显式提交 -->
            <button
              v-if="!autoSubmit"
              type="button"
              class="btn btn--primary"
              :disabled="!canConfirm || submitting"
              @click="submit('SURE')"
            >
              提交答案
            </button>
            <p v-else class="practice__tip">
              {{
                examMode
                  ? '模拟考试中不显示对错,交卷后统一看结果'
                  : '点选项立即判定,答对自动进入下一题'
              }}
            </p>
          </template>
          <button v-else type="button" class="btn btn--primary btn--block" @click="onNext">
            {{ nextLabel }}
          </button>
        </template>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.practice {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  /* 保证内容较短时底部操作区也贴底 */
  min-height: calc(100dvh - 200px);
}

.practice__top {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
  background-color: var(--bg-page);
}

.practice__exit {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-height: var(--tap-target-min);
  padding: 0 var(--space-sm);
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--font-size-body);
}

.practice__exit-icon {
  transform: rotate(180deg);
}

.practice__mode {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-size-card-title);
  font-weight: var(--font-weight-card-title);
  color: var(--text-primary);
}

.practice__count {
  flex-shrink: 0;
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.practice__progress {
  flex-basis: 100%;
}

/* 模拟考试:倒计时与交卷 */
.practice__exam {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-xs) var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
}

.practice__timer {
  font-size: var(--font-size-card-title);
  font-weight: 600;
  color: var(--text-primary);
}

.practice__timer--low {
  color: var(--color-danger);
}

.practice__handin {
  min-height: var(--tap-target-min);
  padding: 0 var(--space-lg);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-full);
  background-color: transparent;
  color: var(--color-primary);
  font-size: var(--font-size-body);
}

.practice__handin:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.practice__loading {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.practice__hint {
  text-align: center;
  color: var(--text-secondary);
  font-size: var(--font-size-body);
}

.practice__tip {
  flex: 1 1 auto;
  align-self: center;
  font-size: var(--font-size-aux);
  line-height: var(--line-height-aux);
  color: var(--text-secondary);
  text-align: center;
}

.practice__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-lg);
  text-align: center;
}

.practice__state-text {
  color: var(--text-secondary);
}

.practice__state-actions {
  display: flex;
  gap: var(--space-sm);
}

.practice__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

/* 主观题 */
.subjective {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.subjective__label {
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.points {
  padding: var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
}

.points__hint {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
  margin-bottom: var(--space-sm);
}

.points__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.point {
  min-height: var(--tap-target-min);
  display: flex;
  align-items: center;
}

/*
 * 要点自评用的是 Element Plus 的 el-checkbox,它默认 white-space: nowrap,
 * 长要点会撑成两千多像素宽,再被 body 的 overflow-x: hidden 裁掉 ——
 * 表现出来就是「简答题的答案要点显示不全」,手机和电脑都一样。
 * 这里让复选框整体可换行,并让勾选框对齐第一行文字。
 */
.point :deep(.el-checkbox) {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
  height: auto;
  margin-right: 0;
  align-items: flex-start;
  white-space: normal;
}

.point :deep(.el-checkbox__label) {
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: var(--line-height-body);
}

.point :deep(.el-checkbox__input) {
  margin-top: 3px;
}

.point__text {
  font-size: var(--font-size-body);
  line-height: var(--line-height-body);
}

.points__score {
  margin-top: var(--space-sm);
  font-size: var(--font-size-body);
  color: var(--color-success);
}

.analysis {
  padding: var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-page);
}

.analysis__hint {
  margin-bottom: var(--space-xs);
  font-size: var(--font-size-aux);
  font-weight: 600;
  line-height: var(--line-height-aux);
  color: var(--color-primary);
}

.analysis__section + .analysis__section {
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px dashed var(--border-color);
}

.analysis__text {
  font-size: var(--font-size-body);
  line-height: var(--line-height-body);
  white-space: pre-wrap;
  color: var(--text-primary);
}

/* 提交失败提示 */
.retry {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  background-color: var(--color-danger-light);
  color: var(--color-danger);
}

.retry__text {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-size-body);
}

.retry__btn {
  flex-shrink: 0;
  min-height: var(--tap-target-min);
  padding: 0 var(--space-md);
  border: 1px solid currentColor;
  border-radius: var(--radius-full);
  background: transparent;
  color: inherit;
  font-size: var(--font-size-body);
}

/* 底部固定操作区:判定前后在同一位置,手指不用移动 */
.practice__actions {
  position: sticky;
  bottom: 0;
  z-index: 20;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: auto;
  padding: var(--space-sm) 0 var(--space-md);
  background-color: var(--bg-page);
  border-top: 1px solid var(--border-color);
}

@media (max-width: 767.98px) {
  .practice__actions {
    /*
     * 手机上没有底部 Tab(练习页是 single 布局),操作区就该贴屏幕底部。
     * 之前这里留了 tabbar 的高度,软键盘弹出时 sticky 元素被顶来顶去,
     * 是页面「跳一下」的原因之一。
     */
    bottom: 0;
    padding-bottom: calc(var(--space-md) + env(safe-area-inset-bottom));
  }
}

.btn {
  min-height: 52px;
  padding: 0 var(--space-lg);
  border-radius: var(--radius-md);
  font-size: var(--font-size-option);
  font-weight: 500;
}

.btn--block {
  flex: 1 1 auto;
}

.btn--primary {
  flex: 1 1 auto;
  border: none;
  background-color: var(--color-primary);
  color: var(--text-on-primary);
}

.btn--primary:hover:not(:disabled) {
  background-color: var(--color-primary-hover);
}

.btn--ghost {
  flex: 1 1 auto;
  border: 1px solid var(--border-color);
  background-color: var(--bg-card);
  color: var(--text-primary);
}

.btn--ghost:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}
</style>
