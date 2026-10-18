<script setup lang="ts">
/**
 * 题库管理(阶段 2 前端)。电脑端为主:筛选栏 + 表格 + 分页。
 * 验收:题干超两行省略并显示 tooltip;删除二次确认;空状态文案按规范;
 * 筛选状态属于页面局部状态,用 ref,不进 store(见 03-state-and-api.md)。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteQuestion, getQuestions } from '@/api/question'
import type { QuestionDetailVO, QuestionQuery } from '@/api/types'
import { useSubjectStore } from '@/stores/subject'
import { useBreakpoint } from '@/composables/useBreakpoint'
import {
  QUESTION_TYPE_LABEL,
  QUESTION_TYPE_OPTIONS
} from '@/utils/constants'
import SubjectTag from '@/components/SubjectTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppIcon from '@/components/AppIcon.vue'

const router = useRouter()
const route = useRoute()
const subjectStore = useSubjectStore()
const { width } = useBreakpoint()

/**
 * 桌面端(>1024)保持 9 列表格;手机和平板窄屏换成卡片列表 ——
 * 表格在 375px 上能滚但只能看到题干,卡片能把题型/科目/状态一次看全。
 */
const compact = computed(() => width.value <= 1024)
/** 手机端筛选默认收起来,只留「科目」和搜索 */
const filtersOpen = ref(false)
/** 折叠时用角标提示还有几个筛选在生效 */
const extraFilterCount = computed(
  () =>
    [filters.chapterId != null, filters.type !== '', filters.status !== '', filters.sourceYear != null]
      .filter(Boolean).length
)

const rows = ref<QuestionDetailVO[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)

const filters = reactive<{
  subjectId: number | null
  chapterId: number | null
  type: string
  status: string
  sourceYear: number | null
  keyword: string
}>({
  subjectId: null,
  chapterId: null,
  type: '',
  status: '',
  sourceYear: null,
  keyword: ''
})

/** 章节下拉随科目联动;没选科目时禁用 */
const chapterOptions = computed(() =>
  filters.subjectId == null ? [] : (subjectStore.chaptersBySubject[filters.subjectId] ?? [])
)

const yearOptions = computed(() => {
  const years = new Set<number>()
  for (const r of rows.value) if (r.sourceYear) years.add(r.sourceYear)
  return [...years].sort((a, b) => b - a)
})

const filtersTouched = computed(
  () =>
    filters.subjectId != null ||
    filters.chapterId != null ||
    filters.type !== '' ||
    filters.status !== '' ||
    filters.sourceYear != null ||
    filters.keyword.trim() !== ''
)

async function load() {
  loading.value = true
  try {
    const query: QuestionQuery = {
      subjectId: filters.subjectId ?? undefined,
      chapterId: filters.chapterId ?? undefined,
      type: filters.type || undefined,
      status: filters.status || undefined,
      sourceYear: filters.sourceYear ?? undefined,
      keyword: filters.keyword.trim() || undefined,
      page: page.value,
      size: size.value,
      sort: 'updatedAt,desc'
    }
    const result = await getQuestions(query)
    rows.value = result.list
    total.value = result.total
  } catch {
    // request.ts 已统一弹错误提示,这里保持当前列表不变
  } finally {
    loading.value = false
  }
}

/**
 * 筛选条件同步到地址栏。
 *
 * 这不是为了分享链接 —— 而是「编辑一道题再返回」时要能回到同一屏:
 * 跳到编辑页时把当前地址带过去,保存后原样回来,列表重新挂载时按地址栏恢复筛选。
 * 用 replace 而不是 push,免得每个筛选动作都往历史里塞一条。
 */
function syncQuery() {
  const query: Record<string, string> = {}
  if (filters.subjectId != null) query.subjectId = String(filters.subjectId)
  if (filters.chapterId != null) query.chapterId = String(filters.chapterId)
  if (filters.type) query.type = filters.type
  if (filters.status) query.status = filters.status
  if (filters.sourceYear != null) query.sourceYear = String(filters.sourceYear)
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (page.value > 1) query.page = String(page.value)
  void router.replace({ path: '/questions', query })
}

/** 进入页面时从地址栏恢复筛选(编辑返回、刷新都走这里) */
function readQuery() {
  const q = route.query
  const num = (v: unknown): number | null => {
    const n = Number(v)
    return v == null || v === '' || Number.isNaN(n) ? null : n
  }
  const str = (v: unknown): string => (typeof v === 'string' ? v : '')
  filters.subjectId = num(q.subjectId)
  filters.chapterId = num(q.chapterId)
  filters.type = str(q.type)
  filters.status = str(q.status)
  filters.sourceYear = num(q.sourceYear)
  filters.keyword = str(q.keyword)
  page.value = num(q.page) ?? 1
  // 地址栏里带了折叠区的筛选,就把筛选面板展开,别让用户以为筛选没生效
  if (filters.chapterId != null || filters.type || filters.status || filters.sourceYear != null) {
    filtersOpen.value = true
  }
}

/** 只清本地状态,不碰地址栏(clearFilters 会再同步) */
function resetLocalFilters() {
  filters.subjectId = null
  filters.chapterId = null
  filters.type = ''
  filters.status = ''
  filters.sourceYear = null
  filters.keyword = ''
}

function applyFilters() {
  page.value = 1
  syncQuery()
  void load()
}

function onPageChange() {
  syncQuery()
  void load()
}

function clearFilters() {
  resetLocalFilters()
  applyFilters()
}

/** 点「编辑」:把当前列表地址(含筛选)带上,改完能原样回来 */
function openEdit(row: QuestionDetailVO) {
  router.push({ path: `/questions/${row.id}/edit`, query: { back: route.fullPath } })
}

function onSubjectChange() {
  filters.chapterId = null
  if (filters.subjectId != null) void subjectStore.fetchChapters(filters.subjectId)
  applyFilters()
}

async function removeQuestion(row: QuestionDetailVO) {
  const stemPreview = row.stem.length > 30 ? `${row.stem.slice(0, 30)}…` : row.stem
  try {
    await ElMessageBox.confirm(`删除后无法恢复:「${stemPreview}」`, '确认删除这道题?', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return // 用户取消
  }
  await deleteQuestion(row.id)
  ElMessage.success('已删除')
  await Promise.all([load(), subjectStore.fetchSubjects(true)])
}

/**
 * 导出为 JSON:按导入格式导出当前筛选结果,导出的文件可以直接再导入(往返一致)。
 * 逐页拉取(每页 200),最多 2000 条——自用场景下这已远超一份试卷的体量。
 */
async function exportJson() {
  loading.value = true
  try {
    const collected: QuestionDetailVO[] = []
    let p = 1
    for (;;) {
      const result = await getQuestions({
        subjectId: filters.subjectId ?? undefined,
        chapterId: filters.chapterId ?? undefined,
        type: filters.type || undefined,
        status: filters.status || undefined,
        sourceYear: filters.sourceYear ?? undefined,
        keyword: filters.keyword.trim() || undefined,
        page: p,
        size: 200
      })
      collected.push(...result.list)
      if (p >= result.pages || p >= 10) break
      p++
    }
    if (collected.length === 0) {
      ElMessage.warning('当前筛选条件下没有题目可导出')
      return
    }
    const bySubject = new Map<number, QuestionDetailVO[]>()
    for (const q of collected) {
      const list = bySubject.get(q.subjectId) ?? []
      list.push(q)
      bySubject.set(q.subjectId, list)
    }
    // 单科目导出成可直接导入的结构;跨科目导出为通用备份
    const payload =
      bySubject.size === 1 && filters.subjectId != null
        ? {
            subjectCode: subjectStore.subjectMap.get(filters.subjectId)?.code ?? '',
            items: collected.map(toImportItem)
          }
        : { exportedAt: new Date().toISOString(), questions: collected }
    download(`${filters.subjectId ? '题库导出' : '题库备份'}.json`, JSON.stringify(payload, null, 2))
    ElMessage.success(`已导出 ${collected.length} 题`)
  } finally {
    loading.value = false
  }
}

function toImportItem(q: QuestionDetailVO) {
  return {
    sectionName: null,
    type: q.type,
    chapterName: q.chapterName,
    stem: q.stem,
    options: q.options,
    answer: q.answer,
    answerPoints: q.answerPoints,
    analysis: q.analysis,
    sourceYear: q.sourceYear,
    score: q.score,
    difficulty: q.difficulty
  }
}

function download(filename: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  // 先按地址栏恢复筛选(从编辑页返回、或刷新页面时都要用上)
  readQuery()
  await subjectStore.fetchSubjects()
  // 带科目进来时,章节下拉需要这份章节数据才能显示当前选择
  if (filters.subjectId != null) {
    await subjectStore.fetchChapters(filters.subjectId).catch(() => undefined)
  }
  await load()
})

watch(
  () => route.query,
  (q) => {
    // 从侧栏点「题库」导航过来时地址栏是干净的 —— 回到默认筛选,别让页面和地址栏对不上
    if (Object.keys(q).length === 0 && filtersTouched.value) {
      resetLocalFilters()
      page.value = 1
      void load()
    }
  }
)
</script>

<template>
  <section class="question-list">
    <header class="question-list__head">
      <h1 class="page-title">题库管理</h1>
      <div class="question-list__toolbar">
        <el-button type="primary" @click="router.push('/questions/new')">
          <AppIcon name="plus" :size="14" /> 新建题目
        </el-button>
        <el-button @click="router.push('/questions/import')">
          <AppIcon name="upload" :size="14" /> 批量导入
        </el-button>
        <el-button @click="exportJson">
          <AppIcon name="download" :size="14" /> 导出为 JSON
        </el-button>
      </div>
    </header>

    <div class="question-list__filters card">
      <el-select
        v-model="filters.subjectId"
        placeholder="全部科目"
        clearable
        class="filter-item"
        @change="onSubjectChange"
      >
        <el-option
          v-for="s in subjectStore.subjects"
          :key="s.id"
          :label="s.shortName"
          :value="s.id"
        />
      </el-select>
      <!-- 手机端:其余筛选默认收起,只留科目和搜索 -->
      <el-button
        v-if="compact"
        class="filter-toggle"
        :type="extraFilterCount ? 'primary' : 'default'"
        plain
        @click="filtersOpen = !filtersOpen"
      >
        筛选<template v-if="extraFilterCount">·{{ extraFilterCount }}</template>
      </el-button>
      <template v-if="!compact || filtersOpen">
      <el-select
        v-model="filters.chapterId"
        placeholder="全部章节"
        clearable
        class="filter-item"
        :disabled="filters.subjectId == null"
        :no-data-text="filters.subjectId == null ? '先选科目' : '暂无数据'"
        @change="applyFilters"
      >
        <el-option v-for="c in chapterOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filters.type" placeholder="全部题型" clearable class="filter-item" @change="applyFilters">
        <el-option v-for="t in QUESTION_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-item filter-item--narrow" @change="applyFilters">
        <el-option label="已入库" value="PUBLISHED" />
        <el-option label="待校对" value="DRAFT" />
      </el-select>
      <el-select v-model="filters.sourceYear" placeholder="年份" clearable class="filter-item filter-item--narrow" @change="applyFilters">
        <el-option v-for="y in yearOptions" :key="y" :label="String(y)" :value="y" />
      </el-select>
      </template>
      <el-input
        v-model="filters.keyword"
        placeholder="搜索题干关键词,回车检索"
        clearable
        class="filter-item filter-item--search"
        @keyup.enter="applyFilters"
        @clear="applyFilters"
      />
      <el-button type="primary" plain @click="applyFilters">搜索</el-button>
      <el-button v-if="filtersTouched" text @click="clearFilters">清空筛选</el-button>
    </div>

    <!-- 加载骨架 -->
    <div v-if="loading" class="card question-list__skeleton">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 空状态:题库全空 与 搜索无结果 文案不同 -->
    <div v-else-if="rows.length === 0 && !filtersTouched" class="card">
      <EmptyState
        text="还没有题目。从电脑上导入一份真题开始吧"
        action-text="查看导入格式"
        @action="router.push('/questions/import')"
      />
    </div>
    <div v-else-if="rows.length === 0" class="card">
      <EmptyState text="没有找到匹配的题目,换个关键词试试" action-text="清空筛选" @action="clearFilters" />
    </div>

    <template v-else>
      <!-- 手机 / 平板窄屏:9 列表格只能横向滚,改用卡片把关键信息一次看全 -->
      <div v-if="compact" class="question-cards">
        <article v-for="row in rows" :key="row.id" class="card question-cards__item">
          <p class="question-cards__stem wrap-anywhere">{{ row.stem }}</p>
          <div class="question-cards__meta">
            <span class="type-chip">{{ QUESTION_TYPE_LABEL[row.type] ?? row.typeLabel }}</span>
            <SubjectTag
              :name="row.subjectShortName ?? '—'"
              :color="row.subjectColor ?? undefined"
              size="sm"
            />
            <span
              :class="['status-pill', row.status === 'DRAFT' ? 'status-pill--draft' : 'status-pill--ok']"
            >
              {{ row.status === 'DRAFT' ? '待校对' : '已入库' }}
            </span>
          </div>
          <p class="question-cards__sub">
            {{ row.chapterName ?? '未归章' }} · {{ row.score }} 分
            <template v-if="row.sourceYear"> · {{ row.sourceYear }} 年</template>
          </p>
          <div class="question-cards__actions">
            <el-button @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" plain @click="removeQuestion(row)">删除</el-button>
          </div>
        </article>
      </div>

      <div v-else class="card question-list__table">
        <el-table :data="rows" style="width: 100%">
          <el-table-column prop="id" label="#" width="64" />
          <el-table-column label="题干" min-width="280">
            <template #default="{ row }">
              <el-tooltip :content="row.stem" placement="top" :show-after="400" popper-class="stem-tooltip">
                <div class="stem-clamp">{{ row.stem }}</div>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="题型" width="120">
            <template #default="{ row }">{{ QUESTION_TYPE_LABEL[row.type] ?? row.typeLabel }}</template>
          </el-table-column>
          <el-table-column label="科目" width="110">
            <template #default="{ row }">
              <SubjectTag :name="row.subjectShortName ?? '—'" :color="row.subjectColor ?? undefined" size="sm" />
            </template>
          </el-table-column>
          <el-table-column label="章节" min-width="130">
            <template #default="{ row }">
              <span class="chapter-cell wrap-anywhere">{{ row.chapterName ?? '未归章' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="source" label="来源" width="130" show-overflow-tooltip />
          <el-table-column prop="score" label="分值" width="76" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span :class="['status-pill', row.status === 'DRAFT' ? 'status-pill--draft' : 'status-pill--ok']">
                {{ row.status === 'DRAFT' ? '待校对' : '已入库' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" @click="openEdit(row as QuestionDetailVO)">
                编辑
              </el-button>
              <el-button text type="danger" @click="removeQuestion(row as QuestionDetailVO)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <footer class="question-list__foot">
        <span class="question-list__count">共 {{ total }} 题</span>
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          @current-change="onPageChange"
        />
      </footer>
    </template>
  </section>
</template>

<style scoped>
/* 本页自成一套 card/标题样式,与 SubjectListView 的 panel 风格一致 */
.card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
}
.question-list__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}
.question-list__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}
.question-list__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md);
  margin-bottom: var(--space-md);
}
.filter-item { width: 150px; }
.filter-item--narrow { width: 110px; }
.filter-item--search { width: 230px; flex: 1 1 180px; }

.question-list__skeleton,
.question-list__table {
  padding: var(--space-md);
}

/* ── 手机 / 平板:卡片列表(桌面端不渲染这块) ── */
.question-cards {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.question-cards__item {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding: var(--space-md);
}

.question-cards__stem {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-stem);
  line-height: var(--line-height-stem);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.question-cards__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.type-chip {
  padding: 0 var(--space-xs);
  border-radius: var(--radius-sm);
  background-color: var(--bg-page);
  color: var(--text-secondary);
  font-size: var(--font-size-aux);
  line-height: 20px;
}

.question-cards__sub {
  margin: 0;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.question-cards__actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-xs);
}

/* 手机端筛选折叠按钮 */
.filter-toggle {
  min-width: 88px;
}

/* 操作列:两个文字按钮不能换行;间距收紧一点,给列宽留余量 */
.question-list__table :deep(.el-table__cell .el-button) {
  white-space: nowrap;
}

.question-list__table :deep(.el-table__cell .el-button + .el-button) {
  margin-left: 8px;
}

/* 题干两行截断:超过两行省略,完整内容由 tooltip 给出 */
.stem-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  cursor: default;
}
.stem-cell,
.chapter-cell {
  color: var(--text-secondary);
}

.status-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  line-height: 1.6;
}
.status-pill--ok {
  background: var(--color-success-light);
  color: var(--color-success);
}
.status-pill--draft {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.question-list__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  flex-wrap: wrap;
}
.question-list__count {
  color: var(--text-secondary);
  font-size: 14px;
}
</style>

<style>
/* tooltip 里的长题干也要可读(不能撑破屏幕) */
.stem-tooltip {
  max-width: 420px;
  white-space: pre-wrap;
}
</style>
