<script setup lang="ts">
/**
 * 模拟考试(按真题结构组卷)。
 *
 * 组卷结构不写死:从该科目最近一份真题试卷里读出「题型 → 题数、每题分值、考试时长」,
 * 用户勾选要考的题型、调整题量,再从题库里对应题型的已发布题目中随机抽题。
 * 题库里题量不够的题型会标出「可抽 N 题」,由用户自己决定削减多少。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getMockTemplate } from '@/api/practice'
import type { MockTemplateVO } from '@/api/types'
import { useSubjectStore } from '@/stores/subject'

interface SectionChoice {
  type: string
  label: string
  /** 真题里的题数 */
  templateCount: number
  /** 用户这次要出的题数 */
  count: number
  score: number | null
  available: number
  enabled: boolean
}

const router = useRouter()
const subjectStore = useSubjectStore()

const subjectId = ref<number | null>(null)
const template = ref<MockTemplateVO | null>(null)
const choices = ref<SectionChoice[]>([])
const duration = ref(150)
const loading = ref(false)
const loadError = ref('')
/** 防止慢请求覆盖新结果 */
let loadSeq = 0

const selected = computed(() => choices.value.filter((c) => c.enabled && c.count > 0))
const totalCount = computed(() => selected.value.reduce((sum, c) => sum + c.count, 0))
const totalScore = computed(() =>
  selected.value.reduce((sum, c) => sum + c.count * (c.score ?? 0), 0)
)
const canStart = computed(() => totalCount.value > 0)

function formatScore(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

async function load() {
  if (subjectId.value == null) {
    template.value = null
    choices.value = []
    return
  }
  const seq = ++loadSeq
  loading.value = true
  loadError.value = ''
  try {
    const t = await getMockTemplate(subjectId.value)
    if (seq !== loadSeq) return
    template.value = t
    duration.value = t.durationMin ?? 150
    choices.value = t.sections.map((s) => ({
      type: s.type,
      label: s.typeLabel,
      templateCount: s.count,
      // 题库不够就按可抽题数作为默认值,别让用户一开始就填一个抽不满的数
      count: Math.min(s.count, Math.max(1, s.available)),
      score: s.score,
      available: s.available,
      enabled: s.available > 0
    }))
  } catch (e) {
    if (seq !== loadSeq) return
    template.value = null
    choices.value = []
    loadError.value = e instanceof Error ? e.message : '加载组卷模板失败'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function start() {
  if (!canStart.value || subjectId.value == null) return
  const sections = selected.value.map((c) => `${c.type}:${c.count}`).join(',')
  router.push({
    path: '/practice/mock',
    query: {
      subjectId: String(subjectId.value),
      sections,
      duration: String(duration.value),
      // 只有从本页点「开始」才开新场;刷新练习页时没有这个参数,会恢复进行中的考试
      fresh: '1'
    }
  })
}

watch(subjectId, () => void load())

onMounted(async () => {
  await subjectStore.fetchSubjects().catch(() => undefined)
  if (subjectId.value == null && subjectStore.subjects.length > 0) {
    subjectId.value = subjectStore.subjects[0].id
  }
})
</script>

<template>
  <section class="mock">
    <header class="mock__head">
      <h1 class="mock__title">模拟考试</h1>
      <p class="mock__hint">
        按真题的题型结构组卷,限时作答。题型和题量可以按需要调整。
      </p>
    </header>

    <div class="card mock__form">
      <div class="field">
        <span class="field__label">科目</span>
        <el-select v-model="subjectId" placeholder="选择要模拟的科目" class="field__control">
          <el-option
            v-for="s in subjectStore.subjects"
            :key="s.id"
            :value="s.id"
            :label="`${s.code} · ${s.name}`"
          />
        </el-select>
      </div>

      <div v-if="loading" class="mock__loading">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="loadError" class="banner" role="alert">
        <span class="banner__text wrap-anywhere">{{ loadError }}</span>
        <button type="button" class="banner__action" @click="load">重试</button>
      </div>

      <EmptyState
        v-else-if="template && template.paperId == null"
        text="这个科目还没有可参考的真题试卷,先导入一份真题,系统会按它的题型结构组卷。"
        action-text="去批量导入"
        icon="upload"
        @action="router.push('/questions/import')"
      />

      <template v-else-if="template">
        <div class="mock__source">
          <span>
            参考试卷:<strong>{{ template.paperName }}</strong>
            <template v-if="template.year">({{ template.year }} 年)</template>
          </span>
          <span class="mock__source-meta">
            满分 {{ template.totalScore }} · 时长 {{ template.durationMin }} 分钟
          </span>
        </div>

        <ul class="sections">
          <li
            v-for="c in choices"
            :key="c.type"
            class="section"
            :class="{ 'section--off': !c.enabled }"
          >
            <el-checkbox v-model="c.enabled" :disabled="c.available === 0" class="section__check">
              <span class="section__name">{{ c.label }}</span>
            </el-checkbox>
            <div class="section__row">
              <el-input-number
                v-model="c.count"
                :min="1"
                :max="Math.max(1, c.available)"
                size="small"
                :disabled="!c.enabled"
                class="section__count"
              />
              <span class="section__info">· 每题 {{ c.score ?? '—' }} 分</span>
              <span
                class="section__avail"
                :class="{ 'section__avail--warn': c.available < c.templateCount }"
              >
                可抽 {{ c.available }} 题<template v-if="c.available < c.templateCount">
                  (真题 {{ c.templateCount }} 题)</template>
              </span>
            </div>
          </li>
        </ul>

        <div class="field">
          <span class="field__label">考试时长(分钟)</span>
          <el-input-number v-model="duration" :min="10" :max="300" class="field__control" />
        </div>

        <p class="mock__summary">
          <template v-if="canStart">
            共 <strong class="mono">{{ totalCount }}</strong> 题,满分约
            <strong class="mono">{{ formatScore(totalScore) }}</strong> 分
          </template>
          <template v-else>至少勾选一种题型才能开始</template>
        </p>

        <div class="mock__actions">
          <el-button type="primary" size="large" :disabled="!canStart" @click="start">
            <AppIcon name="target" :size="18" /> 开始模拟考试
          </el-button>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.mock {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.mock__head {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.mock__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.mock__hint {
  font-size: var(--font-size-body);
  line-height: 1.6;
  color: var(--text-secondary);
}

.card {
  padding: var(--card-padding);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.mock__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.field__label {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.field__control {
  width: 100%;
}

.mock__source {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-xs) var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  background-color: var(--bg-page);
  font-size: var(--font-size-body);
  color: var(--text-primary);
}

.mock__source-meta {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.sections {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.section {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.section--off {
  opacity: 0.65;
}

/* el-checkbox 默认 nowrap,题型名不长,这里仍然放开,避免窄屏挤压 */
.section__check {
  white-space: normal;
}

.section__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--text-primary);
}

.section__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding-inline-start: 24px;
}

.section__count {
  width: 110px;
}

.section__info {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.section__avail {
  margin-inline-start: auto;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.section__avail--warn {
  color: var(--color-warning);
}

.mock__summary {
  margin: 0;
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.mock__actions {
  display: flex;
}

.mock__actions .el-button {
  width: 100%;
  min-height: 48px;
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
