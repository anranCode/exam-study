<script setup lang="ts">
/**
 * 错题本(阶段 5)★ 复习的入口。
 *
 * 结构见 docs/study-exam/frontend/02-routes-and-pages.md「WrongBookView」:
 * 顶部今日到期 + 开始复习,下面按科目分组,每条显示题干、错误次数、下次复习时间、掌握阶段,
 * 并给出三个操作:立即复习(只重做这一道)、标记已掌握、移出。
 *
 * 「问 AI 老师」「举一反三」是阶段 9 的事,这里先不放。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatNumber from '@/components/StatNumber.vue'
import SubjectTag from '@/components/SubjectTag.vue'
import { WRONG_PAGE_SIZE, groupItems, useWrongStore } from '@/stores/wrong'
import { useTodayStore } from '@/stores/today'
import { useSubjectStore } from '@/stores/subject'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { formatShortDate } from '@/utils/date'
import type { ReviewItemVO, ReviewQueueStatus } from '@/api/types'

const router = useRouter()
const store = useWrongStore()
const todayStore = useTodayStore()
const subjectStore = useSubjectStore()
const { isMobile } = useBreakpoint()

/** 正在处理的那一行,避免连点 */
const busyId = ref<number | null>(null)

/**
 * 手机端分批显示。
 *
 * 错题本一次要了 200 条(分组需要全量),但 90 多张卡一次性铺出来,
 * 滚动和首屏都会明显变慢。手机上先渲染 20 条,点「显示更多」再往下放;
 * 桌面端不加这个限制,和原来完全一致。
 */
const MOBILE_STEP = 20
const visibleCount = ref(MOBILE_STEP)
const visibleItems = computed(() =>
  isMobile.value ? store.items.slice(0, visibleCount.value) : store.items
)
const visibleGroups = computed(() => groupItems(visibleItems.value))
const hiddenCount = computed(() => store.items.length - visibleItems.value.length)

function showMore() {
  visibleCount.value += MOBILE_STEP
}

// 换了筛选就是新的一批题,从头开始显示
watch(
  () => store.filters,
  () => {
    visibleCount.value = MOBILE_STEP
  },
  { deep: true }
)

// 筛选条件存在 store 里,改动即重新拉取(错题本不跨会话保留筛选)
const subjectFilter = computed({
  get: () => store.filters.subjectId,
  set: (v: number | null) => store.setFilters({ subjectId: v })
})
const statusFilter = computed({
  get: () => store.filters.status,
  set: (v: ReviewQueueStatus | null) => store.setFilters({ status: v })
})
const dueOnly = computed({
  get: () => store.filters.dueOnly,
  set: (v: boolean) => store.setFilters({ dueOnly: v })
})

/** 是否偏离了默认视图(全部科目 / 只看未掌握 / 不限到期) */
const filtersTouched = computed(
  () =>
    store.filters.subjectId !== null ||
    store.filters.status !== 'LEARNING' ||
    store.filters.dueOnly
)

const showSkeleton = computed(() => store.loading && !store.loaded)

/** 下次复习时间:「已到期」比具体日期有用得多 */
function nextReviewText(item: ReviewItemVO): string {
  if (item.due) return '已到期'
  if (!item.nextReviewAt) return '—'
  return `${formatShortDate(item.nextReviewAt)}复习`
}

function startReview() {
  // 顶部按钮:进入错题复习模式,练所有到期的题
  router.push('/practice/review')
}

/** 单条「立即复习」:只做这一道(后端按 questionIds 照单出题) */
function redoOne(item: ReviewItemVO) {
  router.push({ path: '/practice/review', query: { questionId: String(item.questionId) } })
}

async function markMastered(item: ReviewItemVO) {
  if (busyId.value !== null) return
  try {
    await ElMessageBox.confirm(
      '标记已掌握后它会退出复习队列,不再安排复习;切到「已掌握」筛选仍能看到。',
      '标记为已掌握?',
      { confirmButtonText: '标记已掌握', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  busyId.value = item.questionId
  try {
    await store.markMastered(item.questionId)
    ElMessage.success('已标记为掌握')
  } finally {
    busyId.value = null
  }
}

async function removeItem(item: ReviewItemVO) {
  if (busyId.value !== null) return
  try {
    await ElMessageBox.confirm(
      '移出后这道题不再出现在错题本,也不会再安排复习。题目本身会留在题库里。',
      '移出错题本?',
      { confirmButtonText: '移出', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  busyId.value = item.questionId
  try {
    await store.remove(item.questionId)
    ElMessage.success('已移出错题本')
  } finally {
    busyId.value = null
  }
}

async function init() {
  await Promise.all([store.fetch(), todayStore.refresh()])
  if (subjectStore.subjects.length === 0) {
    try {
      await subjectStore.fetchSubjects()
    } catch {
      // 错误提示已由 request.ts 统一弹出
    }
  }
}

onMounted(init)
</script>

<template>
  <section class="wrong">
    <header class="wrong__head">
      <h1 class="wrong__title">错题本</h1>
      <p class="wrong__hint">做错的题会自动进来,按遗忘曲线安排复习。</p>
    </header>

    <!-- 顶部:今日到期 + 开始复习 -->
    <div class="card wrong__top">
      <StatNumber :value="todayStore.dueTotal" label="今日到期" unit="题" />
      <button
        type="button"
        class="wrong__start"
        :disabled="todayStore.dueTotal === 0"
        @click="startReview"
      >
        <AppIcon name="target" :size="18" />
        <span>开始复习</span>
      </button>
    </div>

    <!-- 筛选 -->
    <div class="wrong__filters card">
      <el-select v-model="subjectFilter" placeholder="全部科目" clearable class="filter-item">
        <el-option
          v-for="s in subjectStore.subjects"
          :key="s.id"
          :label="s.shortName"
          :value="s.id"
        />
      </el-select>
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="filter-item">
        <el-option label="未掌握" value="LEARNING" />
        <el-option label="已掌握" value="MASTERED" />
      </el-select>
      <el-checkbox v-model="dueOnly">只看已到期</el-checkbox>
      <el-button v-if="filtersTouched" text @click="store.resetFilters()">清空筛选</el-button>
    </div>

    <!-- 加载骨架 -->
    <div v-if="showSkeleton" class="card wrong__skeleton">
      <div class="skeleton skeleton--line skeleton--w60" />
      <div class="skeleton skeleton--line skeleton--w80" />
      <div class="skeleton skeleton--block" />
    </div>

    <!-- 按科目分组的错题列表 -->
    <template v-else-if="store.items.length > 0">
      <section v-for="group in visibleGroups" :key="group.subjectId" class="wrong-group">
        <div class="wrong-group__head">
          <SubjectTag :name="group.shortName ?? '未分类'" :color="group.color ?? undefined" />
          <span class="wrong-group__count mono">{{ group.items.length }} 题</span>
        </div>

        <ul class="wrong-group__list">
          <li v-for="item in group.items" :key="item.questionId" class="card wrong-item">
            <div class="wrong-item__top">
              <span class="wrong-item__type">{{ item.typeLabel }}</span>
              <span
                class="wrong-item__mastery"
                :class="`wrong-item__mastery--${item.masteryStage.toLowerCase()}`"
              >
                {{ item.masteryLabel }}
              </span>
            </div>

            <el-tooltip
              :content="item.stem"
              placement="top"
              :show-after="400"
              popper-class="stem-tooltip"
            >
              <p class="wrong-item__stem stem-text wrap-anywhere">{{ item.stem }}</p>
            </el-tooltip>

            <p class="wrong-item__meta">
              <span>错 {{ item.wrongCount }} 次</span>
              <span class="wrong-item__dot">·</span>
              <span>{{ nextReviewText(item) }}</span>
            </p>

            <div class="wrong-item__actions">
              <button
                type="button"
                class="wrong-item__action wrong-item__action--primary"
                :disabled="busyId === item.questionId"
                @click="redoOne(item)"
              >
                立即复习
              </button>
              <button
                type="button"
                class="wrong-item__action"
                :disabled="busyId === item.questionId || item.status === 'MASTERED'"
                @click="markMastered(item)"
              >
                标记已掌握
              </button>
              <button
                type="button"
                class="wrong-item__action"
                :disabled="busyId === item.questionId"
                @click="removeItem(item)"
              >
                移出
              </button>
            </div>
          </li>
        </ul>
      </section>

      <div v-if="hiddenCount > 0" class="wrong__load-more">
        <button type="button" class="wrong-item__action" @click="showMore">
          显示更多(还有 {{ hiddenCount }} 条)
        </button>
      </div>

      <p v-if="store.truncated" class="wrong__more">
        共 {{ store.total }} 条,当前显示最该复习的 {{ WRONG_PAGE_SIZE }} 条。
      </p>
    </template>

    <!-- 空态:分清「本来就没错题」和「筛选筛没了」 -->
    <div v-else class="card">
      <EmptyState
        v-if="filtersTouched"
        text="当前筛选下没有错题。"
        action-text="清空筛选"
        @action="store.resetFilters()"
      />
      <EmptyState
        v-else
        text="还没有错题,继续保持。"
        action-text="去刷题"
        icon="target"
        @action="router.push('/practice/random')"
      />
    </div>
  </section>
</template>

<style scoped>
.wrong {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.wrong__head {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.wrong__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.wrong__hint {
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.card {
  padding: var(--card-padding);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.wrong__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.wrong__start {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  min-height: var(--tap-target-min);
  padding: 0 var(--space-lg);
  border: none;
  border-radius: var(--radius-full);
  background-color: var(--color-primary);
  color: var(--text-on-primary);
  font-size: var(--font-size-card-title);
  font-weight: 600;
}

.wrong__start:hover:not(:disabled) {
  background-color: var(--color-primary-hover);
}

.wrong__start:disabled {
  background-color: var(--bg-page);
  color: var(--text-disabled);
}

.wrong__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md);
}

.filter-item {
  width: 150px;
}

/* 分组 */
.wrong-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.wrong-group__count {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.wrong-group__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.wrong-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
}

.wrong-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.wrong-item__type {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.wrong-item__mastery {
  flex-shrink: 0;
  padding: 2px var(--space-sm);
  border-radius: var(--radius-full);
  font-size: var(--font-size-aux);
}

.wrong-item__mastery--not_started {
  background-color: var(--color-danger-light);
  color: var(--color-danger);
}

.wrong-item__mastery--learning {
  background-color: var(--color-warning-light);
  color: var(--color-warning);
}

.wrong-item__mastery--mastered {
  background-color: var(--color-success-light);
  color: var(--color-success);
}

/* 题干两行截断:完整内容由 tooltip 给出,与题库页一致 */
.wrong-item__stem {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: var(--font-size-stem);
  line-height: var(--line-height-stem);
  color: var(--text-primary);
}

.wrong-item__meta {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.wrong-item__dot {
  color: var(--text-disabled);
}

.wrong-item__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.wrong-item__action {
  min-height: var(--tap-target-min);
  padding: 0 var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background-color: transparent;
  color: var(--text-primary);
  font-size: var(--font-size-body);
}

.wrong-item__action:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.wrong-item__action:disabled {
  color: var(--text-disabled);
  border-color: var(--border-color);
}

.wrong-item__action--primary {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.wrong__load-more {
  display: flex;
  justify-content: center;
}

.wrong__more {
  text-align: center;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

/* 骨架 */
.wrong__skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
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

<style>
/* tooltip 里的长题干要换行,不能撑破屏幕(与题库页同一处理) */
.stem-tooltip {
  max-width: 420px;
  white-space: pre-wrap;
}
</style>
