<script setup lang="ts">
/**
 * 数据看板(阶段 6)。
 *
 * 四块:三个大数字 → 各科目正确率 → 近 30 天趋势 → 章节掌握度。
 * 口径说明:正确率只算客观题(主观题不判分);掌握度 = 已掌握 / 该范围题量。
 * 图表用 ECharts 按需引入,只注册用到的柱状图/折线图,避免整包进主 chunk。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import EmptyState from '@/components/EmptyState.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import StatNumber from '@/components/StatNumber.vue'
import { getChapterStats, getOverview, getSubjectStats, getTrend } from '@/api/stats'
import type {
  ChapterStatsVO,
  StatsOverviewVO,
  SubjectStatsVO,
  TrendPointVO
} from '@/api/types'
import { useSubjectStore } from '@/stores/subject'
import { formatPercent } from '@/utils/format'

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const subjectStore = useSubjectStore()

const overview = ref<StatsOverviewVO | null>(null)
const subjects = ref<SubjectStatsVO[]>([])
const trend = ref<TrendPointVO[]>([])
const chapters = ref<ChapterStatsVO[]>([])

const loading = ref(true)
const error = ref('')
const loadingChapters = ref(false)
const chapterSubjectId = ref<number | null>(null)

const subjectChartEl = ref<HTMLDivElement | null>(null)
const trendChartEl = ref<HTMLDivElement | null>(null)
let subjectChart: ReturnType<typeof echarts.init> | null = null
let trendChart: ReturnType<typeof echarts.init> | null = null
let themeWatcher: MediaQueryList | null = null

/** 只想看有作答记录的科目:没作答的科目正确率是 null,画出来是空条 */
const subjectsWithAccuracy = computed(() =>
  subjects.value
    .filter((s) => s.answeredCount > 0 && s.accuracy != null)
    .slice()
    .sort((a, b) => (a.accuracy ?? 0) - (b.accuracy ?? 0))
)

const trendHasData = computed(() => trend.value.some((p) => p.answered > 0))

function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function renderSubjectChart() {
  if (!subjectChartEl.value || subjectsWithAccuracy.value.length === 0) return
  subjectChart ??= echarts.init(subjectChartEl.value)
  const text = cssVar('--text-secondary')
  const grid = cssVar('--border-color')
  const data = subjectsWithAccuracy.value

  subjectChart.setOption({
    grid: { left: 8, right: 56, top: 8, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>正确率 ${formatPercent(p.value)}`
    },
    xAxis: {
      type: 'value',
      max: 1,
      axisLabel: { color: text, formatter: (v: number) => `${Math.round(v * 100)}%` },
      splitLine: { lineStyle: { color: grid } }
    },
    yAxis: {
      type: 'category',
      data: data.map((s) => s.shortName || s.name),
      axisLabel: { color: text },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', color: text },
        data: data.map((s) => ({
          value: s.accuracy,
          itemStyle: { color: s.color || cssVar('--color-primary') },
          label: { formatter: formatPercent(s.accuracy) }
        }))
      }
    ]
  })
}

function renderTrendChart() {
  if (!trendChartEl.value || !trendHasData.value) return
  trendChart ??= echarts.init(trendChartEl.value)
  const text = cssVar('--text-secondary')
  const grid = cssVar('--border-color')
  const primary = cssVar('--color-primary')
  const success = cssVar('--color-success')

  trendChart.setOption({
    grid: { left: 8, right: 8, top: 36, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const answered = params.find((p) => p.seriesName === '答题量')?.value ?? 0
        const acc = params.find((p) => p.seriesName === '正确率')?.value
        return `${params[0]?.axisValue ?? ''}<br/>答题量 ${answered} 题<br/>正确率 ${formatPercent(acc)}`
      }
    },
    legend: { top: 0, textStyle: { color: text }, data: ['答题量', '正确率'] },
    xAxis: {
      type: 'category',
      data: trend.value.map((p) => p.date.slice(5)),
      axisLabel: { color: text },
      axisLine: { lineStyle: { color: grid } }
    },
    yAxis: [
      {
        type: 'value',
        name: '题',
        nameTextStyle: { color: text },
        axisLabel: { color: text },
        splitLine: { lineStyle: { color: grid } }
      },
      {
        type: 'value',
        min: 0,
        max: 1,
        axisLabel: { color: text, formatter: (v: number) => `${Math.round(v * 100)}%` },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '答题量',
        type: 'bar',
        barMaxWidth: 14,
        itemStyle: { color: primary, borderRadius: [3, 3, 0, 0] },
        data: trend.value.map((p) => p.answered)
      },
      {
        name: '正确率',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 5,
        connectNulls: false,
        itemStyle: { color: success },
        lineStyle: { color: success, width: 2 },
        data: trend.value.map((p) => p.accuracy)
      }
    ]
  })
}

function renderCharts() {
  renderSubjectChart()
  renderTrendChart()
}

function resizeCharts() {
  subjectChart?.resize()
  trendChart?.resize()
}

async function loadChapters() {
  loadingChapters.value = true
  try {
    chapters.value = await getChapterStats(chapterSubjectId.value ?? undefined)
  } catch {
    chapters.value = []
  } finally {
    loadingChapters.value = false
  }
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [ov, subj, tr] = await Promise.all([getOverview(), getSubjectStats(), getTrend(30)])
    overview.value = ov
    subjects.value = subj
    trend.value = tr
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
  await nextTick()
  renderCharts()
}

watch(chapterSubjectId, () => void loadChapters())

onMounted(async () => {
  await subjectStore.fetchSubjects().catch(() => undefined)
  const first = subjectStore.subjects.find((s) => s.enabled) ?? subjectStore.subjects[0]
  chapterSubjectId.value = first?.id ?? null
  await Promise.all([loadAll(), loadChapters()])

  window.addEventListener('resize', resizeCharts)
  // 系统深浅色切换时重画一次,否则图表里的文字/网格还是旧主题的颜色
  themeWatcher = window.matchMedia('(prefers-color-scheme: dark)')
  themeWatcher.addEventListener('change', renderCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  themeWatcher?.removeEventListener('change', renderCharts)
  subjectChart?.dispose()
  trendChart?.dispose()
  subjectChart = null
  trendChart = null
})
</script>

<template>
  <section class="stats">
    <header class="stats__head">
      <h1 class="stats__title">数据看板</h1>
      <p class="stats__hint">正确率只算客观题;掌握度 = 已掌握题数 / 题量。</p>
    </header>

    <div v-if="error" class="banner" role="alert">
      <span class="banner__text wrap-anywhere">{{ error }}</span>
      <button type="button" class="banner__action" @click="loadAll">重试</button>
    </div>

    <!-- 三个大数字 -->
    <div class="card stats__numbers">
      <StatNumber :value="overview?.totalCorrect ?? 0" label="累计答对(题)" size="lg" />
      <StatNumber :value="formatPercent(overview?.avgAccuracy)" label="平均正确率" size="lg" />
      <StatNumber :value="overview?.streakDays ?? 0" label="连续打卡(天)" unit="天" size="lg" />
    </div>

    <!-- 各科目正确率 -->
    <div class="card">
      <div class="card__head">
        <span class="card__title">各科目正确率</span>
        <span class="card__meta">{{ subjectsWithAccuracy.length }} 个科目有作答记录</span>
      </div>
      <div v-if="loading" class="skeleton skeleton--chart" />
      <div v-else-if="subjectsWithAccuracy.length === 0" class="empty-slot">
        <EmptyState text="还没有作答记录,做完一轮练习再来看。" action-text="去刷题" @action="$router.push('/practice/random')" />
      </div>
      <div v-show="!loading && subjectsWithAccuracy.length > 0" ref="subjectChartEl" class="chart chart--subjects" />
    </div>

    <!-- 近 30 天趋势 -->
    <div class="card">
      <div class="card__head">
        <span class="card__title">近 30 天答题量与正确率</span>
      </div>
      <div v-if="loading" class="skeleton skeleton--chart" />
      <div v-else-if="!trendHasData" class="empty-slot">
        <EmptyState text="近 30 天还没有作答记录。" action-text="去刷题" @action="$router.push('/practice/random')" />
      </div>
      <div v-show="!loading && trendHasData" ref="trendChartEl" class="chart chart--trend" />
    </div>

    <!-- 章节掌握度 -->
    <div class="card">
      <div class="card__head card__head--wrap">
        <span class="card__title">章节掌握度</span>
        <el-select
          v-model="chapterSubjectId"
          placeholder="全部科目"
          clearable
          class="chapter-subject"
        >
          <el-option
            v-for="s in subjectStore.subjects"
            :key="s.id"
            :label="s.shortName"
            :value="s.id"
          />
        </el-select>
      </div>

      <div v-if="loadingChapters" class="skeleton skeleton--list" />
      <div v-else-if="chapters.length === 0" class="empty-slot">
        <EmptyState text="这个科目下还没有章节数据。" action-text="去题库" @action="$router.push('/questions')" />
      </div>
      <ul v-else class="chapter-list">
        <li v-for="c in chapters" :key="String(c.chapterId)" class="chapter-row">
          <div class="chapter-row__top">
            <span class="chapter-row__name wrap-anywhere">{{ c.chapterName }}</span>
            <span class="chapter-row__rate mono">{{ formatPercent(c.masteryRate) }}</span>
          </div>
          <ProgressBar :value="c.masteredCount" :total="c.questionCount" />
          <p class="chapter-row__meta">
            {{ c.questionCount }} 题 · 正确率 {{ formatPercent(c.accuracy) }}
          </p>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.stats {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.stats__head {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.stats__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.stats__hint {
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

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.card__head--wrap {
  flex-wrap: wrap;
}

.card__title {
  font-size: var(--font-size-card-title);
  font-weight: var(--font-weight-card-title);
  color: var(--text-primary);
}

.card__meta {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.stats__numbers {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--space-md);
  text-align: center;
}

.chapter-subject {
  width: 160px;
}

.chart {
  width: 100%;
}

.chart--subjects {
  height: 180px;
}

.chart--trend {
  height: 240px;
}

.empty-slot {
  padding: var(--space-sm) 0;
}

/* 章节列表:横条 + 两行文字,比图表更适合长章节名 */
.chapter-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.chapter-row {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.chapter-row__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
}

.chapter-row__name {
  font-size: var(--font-size-body);
  color: var(--text-primary);
}

.chapter-row__rate {
  flex-shrink: 0;
  font-size: var(--font-size-body);
  color: var(--color-primary);
}

.chapter-row__meta {
  margin: 0;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
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

.skeleton--chart { height: 180px; border-radius: var(--radius-md); }
.skeleton--list { height: 120px; border-radius: var(--radius-md); }

@keyframes skeleton-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
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
</style>
