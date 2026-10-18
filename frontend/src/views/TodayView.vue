<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatNumber from '@/components/StatNumber.vue'
import SubjectTag from '@/components/SubjectTag.vue'
import { useTodayStore } from '@/stores/today'
import { useSubjectStore } from '@/stores/subject'
import { PRACTICE_MODE_LOWER } from '@/utils/constants'
import { daysUntil, formatDisplayDate, formatShortDate, todayISO } from '@/utils/date'
import { formatPercent } from '@/utils/format'
import type { SubjectVO } from '@/api/types'

const router = useRouter()
const todayStore = useTodayStore()
const subjectStore = useSubjectStore()

const errorDismissed = ref(false)
const ongoingDismissed = ref(false)

const todayText = computed(() => formatDisplayDate(todayISO()))
const showSkeleton = computed(() => todayStore.loading && !todayStore.loaded)
const showError = computed(() => !!todayStore.error && !errorDismissed.value)
const showOngoing = computed(() => !!todayStore.ongoingSession && !ongoingDismissed.value)

/** 最近的一场未考考试 */
const nextExam = computed<{ subject: SubjectVO; days: number } | null>(() => {
  let nearest: { subject: SubjectVO; days: number } | null = null
  for (const subject of subjectStore.subjects) {
    if (!subject.enabled || !subject.examDate) continue
    const days = daysUntil(subject.examDate)
    if (days === null || days < 0) continue
    if (!nearest || days < nearest.days) nearest = { subject, days }
  }
  return nearest
})

const examText = computed(() => {
  const exam = nextExam.value
  if (!exam) return '暂无考试安排'
  const name = exam.subject.shortName || exam.subject.name
  if (exam.days === 0) return `今天考《${name}》,加油!`
  return `距《${name}》考试还有 ${exam.days} 天`
})

const hasQuestions = computed(() => todayStore.totalQuestions > 0)

/** 今日主任务按钮(页面最大的可点击元素) */
const mainTask = computed(() => {
  if (!hasQuestions.value) {
    return { label: '先去导入题库', to: '/questions/import', icon: 'upload' }
  }
  const n = todayStore.dueTotal + todayStore.newQuestionQuota || 10
  return { label: `开始今日 ${n} 题`, to: '/practice/daily', icon: 'target' }
})

function goMainTask() {
  // 直接开始第一题,中间不加确认弹窗
  router.push(mainTask.value.to)
}

function goSubjectReview(subjectId: number) {
  router.push({ path: '/practice/review', query: { subjectId: String(subjectId) } })
}

/** 继续上次未完成的练习:banner 已确认,带上 resume 免二次询问 */
function continueOngoing() {
  const ongoing = todayStore.ongoingSession
  if (!ongoing) return
  const mode = PRACTICE_MODE_LOWER[ongoing.mode] ?? 'daily'
  router.push({ path: `/practice/${mode}`, query: { resume: '1' } })
}

async function refresh() {
  errorDismissed.value = false
  ongoingDismissed.value = false
  await todayStore.refresh()
  if (subjectStore.subjects.length === 0) {
    try {
      await subjectStore.fetchSubjects()
    } catch {
      // 错误提示已由 request.ts 统一弹出
    }
  }
}

onMounted(refresh)
</script>

<template>
  <section class="today">
    <header class="today__head">
      <div>
        <h1 class="today__title">今日</h1>
        <p class="today__date">{{ todayText }}</p>
      </div>
      <div class="today__tools">
        <p class="today__exam">
          <AppIcon name="clock" :size="16" />
          <span>{{ examText }}</span>
        </p>
        <!-- 手机端没有侧栏,科目与设置从这个角进 -->
        <button
          type="button"
          class="today__tool"
          aria-label="科目管理"
          @click="router.push('/subjects')"
        >
          <AppIcon name="book" :size="18" />
        </button>
        <button type="button" class="today__tool" aria-label="设置" @click="router.push('/settings')">
          <AppIcon name="settings" :size="18" />
        </button>
      </div>
    </header>

    <!-- 请求失败:可关闭的横条 + 重试 -->
    <div v-if="showError" class="banner" role="alert">
      <span class="banner__text wrap-anywhere">{{ todayStore.error }}</span>
      <button type="button" class="banner__action" @click="refresh">重试</button>
      <button
        type="button"
        class="banner__close"
        aria-label="关闭提示"
        @click="errorDismissed = true"
      >
        <AppIcon name="close" :size="16" />
      </button>
    </div>

    <!-- 未完成场次提示(数据来自 GET /practice/sessions/ongoing) -->
    <div v-if="showOngoing" class="banner banner--info">
      <span class="banner__text">
        上次练习还剩 {{ todayStore.ongoingRemaining }} 题未做完,继续?
      </span>
      <button type="button" class="banner__action" @click="continueOngoing">继续</button>
      <button
        type="button"
        class="banner__close"
        aria-label="忽略提示"
        @click="ongoingDismissed = true"
      >
        忽略
      </button>
    </div>

    <!-- 骨架屏 -->
    <template v-if="showSkeleton">
      <div class="card skeleton-card">
        <div class="skeleton skeleton--line skeleton--w40" />
        <div class="skeleton skeleton--line skeleton--w60" />
      </div>
      <div class="card skeleton-card">
        <div class="skeleton skeleton--block" />
      </div>
      <div class="card skeleton-card">
        <div class="skeleton skeleton--line skeleton--w60" />
        <div class="skeleton skeleton--line skeleton--w80" />
        <div class="skeleton skeleton--line skeleton--w40" />
      </div>
    </template>

    <template v-else>
      <!-- 连续打卡卡片 -->
      <div class="card streak-card">
        <StatNumber :value="todayStore.streakDays" label="连续打卡(天)" unit="天" />
        <span
          class="streak-card__status"
          :class="todayStore.checkedIn ? 'streak-card__status--done' : 'streak-card__status--todo'"
        >
          {{ todayStore.checkedIn ? '今天已打卡' : '今天还没打卡' }}
        </span>
      </div>

      <!-- 今日主任务 -->
      <button type="button" class="main-task" @click="goMainTask">
        <AppIcon :name="mainTask.icon" :size="22" />
        <span class="main-task__label">{{ mainTask.label }}</span>
      </button>

      <!-- 次入口:按科目/题型自己挑题练 -->
      <button type="button" class="quick-action" @click="router.push('/practice/custom')">
        <AppIcon name="target" :size="16" />
        <span>自由练习 · 按科目 / 题型</span>
        <AppIcon name="chevronRight" :size="16" />
      </button>

      <!-- 分科目到期数量 -->
      <div class="card">
        <div class="card__head">
          <span class="card__title">今日到期复习</span>
          <span class="card__count mono">{{ todayStore.dueTotal }} 题</span>
        </div>

        <ul v-if="todayStore.dueBySubject.length > 0" class="due-list">
          <li v-for="item in todayStore.dueBySubject" :key="item.subjectId">
            <button type="button" class="due-item" @click="goSubjectReview(item.subjectId)">
              <SubjectTag :name="item.shortName" :color="item.color" />
              <span class="due-item__count">{{ item.dueCount }} 题到期</span>
              <AppIcon name="chevronRight" :size="16" />
            </button>
          </li>
        </ul>

        <EmptyState
          v-else-if="hasQuestions"
          text="今天的复习都做完了,明天见。"
          action-text="随机练 10 题"
          @action="router.push('/practice/random')"
        />

        <EmptyState
          v-else
          text="还没有题目。从电脑上导入一份真题开始吧。"
          action-text="查看导入格式"
          icon="upload"
          @action="router.push('/questions/import')"
        />
      </div>

      <!-- 最近一次练习 -->
      <div class="card">
        <div class="card__head">
          <span class="card__title">最近一次练习</span>
        </div>
        <button
          v-if="todayStore.lastSession"
          type="button"
          class="last-session"
          @click="router.push(`/practice/${todayStore.lastSession.mode}/result/${todayStore.lastSession.sessionId}`)"
        >
          <div class="last-session__row">
            <span class="last-session__date">{{ formatShortDate(todayStore.lastSession.date) }}</span>
            <span class="last-session__score mono">
              {{ todayStore.lastSession.score }} / {{ todayStore.lastSession.totalScore }}
            </span>
          </div>
          <div class="last-session__row down">
            <span class="last-session__acc">
              正确率 {{ formatPercent(todayStore.lastSession.accuracy) }}
            </span>
            <AppIcon name="chevronRight" :size="16" />
          </div>
        </button>
        <EmptyState v-else text="还没有练习记录,做完一轮就会显示在这里。" />
      </div>
    </template>
  </section>
</template>

<style scoped>
.today {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.today__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.today__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
  margin-bottom: var(--space-xs);
}

.today__date {
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.today__exam {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 6px var(--space-sm);
  border-radius: var(--radius-full);
  background-color: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-body);
}

.today__tools {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-wrap: wrap;
}

.today__tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--tap-target-min);
  min-height: var(--tap-target-min);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background-color: transparent;
  color: var(--text-secondary);
}

.today__tool:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.card {
  padding: var(--card-padding);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
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

.banner {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  background-color: var(--color-danger-light);
  color: var(--color-danger);
}

.banner--info {
  background-color: var(--color-primary-light);
  color: var(--color-primary);
}

.banner__text {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-size-body);
}

.banner__action {
  flex-shrink: 0;
  min-height: var(--tap-target-min);
  padding: 0 var(--space-md);
  border: 1px solid currentColor;
  border-radius: var(--radius-full);
  background-color: transparent;
  color: inherit;
  font-size: var(--font-size-body);
}

.banner__close {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--tap-target-min);
  min-height: var(--tap-target-min);
  border: none;
  background-color: transparent;
  color: inherit;
}

.streak-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.streak-card__status {
  padding: 6px var(--space-md);
  border-radius: var(--radius-full);
  font-size: var(--font-size-body);
}

.streak-card__status--done {
  background-color: var(--color-success-light);
  color: var(--color-success);
}

.streak-card__status--todo {
  background-color: var(--color-warning-light);
  color: var(--color-warning);
}

.main-task {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  /* 页面最大的可点击元素 */
  min-height: 64px;
  padding: var(--space-md) var(--space-lg);
  border: none;
  border-radius: var(--radius-md);
  background-color: var(--color-primary);
  color: var(--text-on-primary);
  font-size: var(--font-size-page-title);
  font-weight: 600;
}

.main-task:hover {
  background-color: var(--color-primary-hover);
}

.main-task__label {
  overflow-wrap: anywhere;
}

/* 次入口:比主任务低一档,但仍是整行可点 */
.quick-action {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  min-height: var(--tap-target-min);
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  color: var(--text-primary);
  font-size: var(--font-size-body);
  text-align: start;
}

.quick-action:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.quick-action span {
  flex: 1 1 auto;
  min-width: 0;
}

.due-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.due-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  min-height: var(--tap-target-min);
  padding: var(--space-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background-color: transparent;
  color: var(--text-primary);
  text-align: left;
}

.due-item:hover {
  background-color: var(--bg-page);
}

.due-item__count {
  flex: 1 1 auto;
  min-width: 0;
  text-align: right;
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.last-session {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  width: 100%;
  min-height: var(--tap-target-min);
  padding: var(--space-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background-color: transparent;
  color: var(--text-primary);
  text-align: left;
}

.last-session:hover {
  background-color: var(--bg-page);
}

.last-session__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.last-session__date {
  font-size: var(--font-size-body);
}

.last-session__score {
  font-size: var(--font-size-body);
  color: var(--color-primary);
}

.last-session__acc {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.skeleton-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.skeleton {
  border-radius: var(--radius-sm);
  background: linear-gradient(
    90deg,
    var(--border-color) 25%,
    var(--bg-page) 37%,
    var(--border-color) 63%
  );
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

.skeleton--line {
  height: 16px;
}

.skeleton--block {
  height: 56px;
  border-radius: var(--radius-md);
}

.skeleton--w40 {
  width: 40%;
}

.skeleton--w60 {
  width: 60%;
}

.skeleton--w80 {
  width: 80%;
}

@keyframes skeleton-loading {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>