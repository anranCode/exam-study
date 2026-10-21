<script setup lang="ts">
/**
 * 本轮小结。数据优先取 store 里的 summary;直接进入 / 刷新时回退到 GET /practice/sessions/{id}
 * 并优雅降级(拿不到就隐藏得分卡,而不是崩溃)。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatNumber from '@/components/StatNumber.vue'
import SubjectTag from '@/components/SubjectTag.vue'
import { usePracticeStore } from '@/stores/practice'
import { getPracticeSessionDetail } from '@/api/practice'
import { PRACTICE_MODE_LABEL, PRACTICE_MODE_LOWER } from '@/utils/constants'
import { formatDuration, formatPercent } from '@/utils/format'
import type {
  PracticeMode,
  PracticeSessionDetailVO,
  SessionSummaryVO,
  WrongQuestionVO
} from '@/api/types'

const route = useRoute()
const router = useRouter()
const practiceStore = usePracticeStore()

const summary = ref<SessionSummaryVO | null>(null)
const loading = ref(false)
const startingReview = ref(false)

const sessionId = computed(() => Number(route.params.sessionId))
const modeLower = computed<PracticeMode>(
  () => PRACTICE_MODE_LOWER[String(route.params.mode).toUpperCase()] ?? 'daily'
)
const modeLabel = computed(() => PRACTICE_MODE_LABEL[modeLower.value] ?? '练习')
const wrongQuestions = computed<WrongQuestionVO[]>(() => summary.value?.wrongQuestions ?? [])
const hasScore = computed(
  () => summary.value != null && (summary.value.score != null || summary.value.totalScore != null)
)

/** 详情接口降级成汇总结构(用于直接进入 / 刷新) */
function summaryFromDetail(d: PracticeSessionDetailVO): SessionSummaryVO {
  const answers = d.answers ?? []
  return {
    sessionId: d.id,
    mode: d.mode,
    totalCount: d.totalCount,
    answeredCount: d.answeredCount,
    objectiveAnsweredCount: answers.filter((a) => a.isCorrect != null).length,
    correctCount: d.correctCount,
    score: d.score,
    totalScore: d.totalScore,
    accuracy: d.accuracy,
    durationSeconds: d.durationSeconds ?? 0,
    reviewCreated: 0,
    reviewUpdated: 0,
    wrongQuestions: answers
      // 客观题答错 + 主观题没自评(含模拟考试里压根没作答的题)
      .filter((a) => a.isCorrect === false || (a.isCorrect === null && a.selfScore == null))
      .map((a) => ({
        questionId: a.questionId,
        stem: a.stem,
        type: a.type,
        typeLabel: a.typeLabel,
        subjectShortName: a.subjectShortName,
        userAnswer: a.userAnswer,
        correctAnswer: a.correctAnswer,
        isReview: false
      }))
  }
}

onMounted(async () => {
  if (practiceStore.summary && practiceStore.summary.sessionId === sessionId.value) {
    summary.value = practiceStore.summary
    return
  }
  loading.value = true
  try {
    const detail = await getPracticeSessionDetail(sessionId.value)
    summary.value = summaryFromDetail(detail)
  } catch {
    /* 错误提示已由 request.ts 统一弹出,这里降级为空 */
  } finally {
    loading.value = false
  }
})

function reviewWrong() {
  if (startingReview.value) return
  // 直接进入 REVIEW 模式,由 PracticeView 创建场次
  router.push('/practice/review')
}

function again() {
  // 模拟考试的组卷参数只存在于上一次的地址里,再来一轮要从设置页重新选
  if (modeLower.value === 'mock') {
    router.push('/mock')
    return
  }
  router.push(`/practice/${modeLower.value}`)
}

function stemPreview(stem: string): string {
  const flat = stem.replace(/\s+/g, ' ').trim()
  return flat.length > 60 ? `${flat.slice(0, 60)}…` : flat
}
</script>

<template>
  <section class="result">
    <header class="result__head">
      <h1 class="result__title">本轮小结</h1>
      <span class="result__mode">{{ modeLabel }}</span>
    </header>

    <div v-if="loading" class="card result__skeleton">
      <el-skeleton :rows="4" animated />
    </div>

    <!-- 拿不到数据:优雅降级 -->
    <template v-else-if="!summary">
      <div class="card">
        <EmptyState
          text="没有找到这次练习的记录。"
          icon="chart"
          action-text="回到今日"
          @action="router.push('/')"
        />
      </div>
    </template>

    <template v-else>
      <!-- 大数字 -->
      <div v-if="hasScore" class="card stats">
        <StatNumber
          :value="summary.score ?? '—'"
          :label="`得分 / 满分 ${summary.totalScore ?? '—'}`"
          size="lg"
        />
        <StatNumber :value="formatPercent(summary.accuracy)" label="正确率" size="md" />
        <StatNumber :value="formatDuration(summary.durationSeconds)" label="用时" size="md" />
      </div>

      <!-- 复习项变更 -->
      <div class="card review-meta">
        <span>本轮新增复习项 <strong class="mono">{{ summary.reviewCreated }}</strong> 项</span>
        <span>更新 <strong class="mono">{{ summary.reviewUpdated }}</strong> 项</span>
        <span class="review-meta__count">
          已答 {{ summary.answeredCount }} / {{ summary.totalCount }} 题
        </span>
      </div>

      <!-- 本轮错题 -->
      <div class="card wrong-card">
        <div class="card__head">
          <span class="card__title">本轮错题</span>
          <span class="card__count mono">{{ wrongQuestions.length }} 题</span>
        </div>
        <ul v-if="wrongQuestions.length" class="wrong-list">
          <li v-for="w in wrongQuestions" :key="w.questionId" class="wrong-item">
            <SubjectTag
              v-if="w.subjectShortName"
              :name="w.subjectShortName"
              size="sm"
              class="wrong-item__tag"
            />
            <div class="wrong-item__body">
              <span class="wrong-item__stem wrap-anywhere">{{ stemPreview(w.stem) }}</span>
              <!-- 模拟考试作答过程中看不到答案,交卷后在这里给出客观题的正确选项 -->
              <span v-if="w.correctAnswer" class="wrong-item__answer wrap-anywhere">
                正确答案:<strong class="mono">{{ w.correctAnswer }}</strong>
              </span>
            </div>
            <span v-if="!w.userAnswer" class="wrong-item__flag wrong-item__flag--todo">未作答</span>
            <span v-if="w.isReview" class="wrong-item__flag">复习</span>
          </li>
        </ul>
        <p v-else class="wrong-empty">本轮没有错题,继续保持。</p>
      </div>

      <!-- 操作 -->
      <div class="result__actions">
        <button
          v-if="wrongQuestions.length"
          type="button"
          class="btn btn--primary btn--block"
          :disabled="startingReview"
          @click="reviewWrong"
        >
          <AppIcon name="refresh" :size="18" />
          立即复习错题
        </button>
        <div class="result__actions-row">
          <button type="button" class="btn btn--ghost" @click="again">再来一轮</button>
          <button type="button" class="btn btn--ghost" @click="router.push('/')">回到今日</button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.result {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.result__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
}

.result__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.result__mode {
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.card {
  padding: var(--card-padding);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.card__title {
  font-size: var(--font-size-card-title);
  font-weight: var(--font-weight-card-title);
  color: var(--text-primary);
}

.card__count {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.result__skeleton {
  min-height: 120px;
}

/* 大数字:三个并排 */
.stats {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.review-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm) var(--space-lg);
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.review-meta__count {
  margin-inline-start: auto;
}

.wrong-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.wrong-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: 40px;
}

.wrong-item__tag {
  flex-shrink: 0;
}

.wrong-item__body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wrong-item__stem {
  font-size: var(--font-size-body);
  color: var(--text-primary);
}

.wrong-item__answer {
  font-size: var(--font-size-aux);
  color: var(--color-success);
}

.wrong-item__flag {
  flex-shrink: 0;
  padding: 0 var(--space-xs);
  border-radius: var(--radius-sm);
  background-color: var(--color-warning-light);
  color: var(--color-warning);
  font-size: var(--font-size-aux);
}

.wrong-item__flag--todo {
  background-color: var(--bg-page);
  color: var(--text-secondary);
}

.wrong-empty {
  color: var(--text-secondary);
  font-size: var(--font-size-body);
}

.result__actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.result__actions-row {
  display: flex;
  gap: var(--space-sm);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  min-height: 52px;
  padding: 0 var(--space-lg);
  border-radius: var(--radius-md);
  font-size: var(--font-size-option);
  font-weight: 500;
}

.btn--block {
  width: 100%;
}

.btn--primary {
  border: none;
  background-color: var(--color-primary);
  color: var(--text-on-primary);
}

.btn--primary:hover:not(:disabled) {
  background-color: var(--color-primary-hover);
}

.btn--ghost {
  flex: 1 1 0;
  border: 1px solid var(--border-color);
  background-color: var(--bg-card);
  color: var(--text-primary);
}

.btn--ghost:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>