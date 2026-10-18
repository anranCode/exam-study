<script setup lang="ts">
/**
 * 题目新建 / 编辑(阶段 2)。
 * 动态表单:题型切换时选项区与要点区互相显隐;正确答案一律点选(不手打字母);
 * 保存前用 el-form rules + validate() 做客户端校验,错误定位到具体字段。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createQuestion, getQuestion, updateQuestion } from '@/api/question'
import { useSubjectStore } from '@/stores/subject'
import {
  QUESTION_TYPE_LABEL,
  QUESTION_TYPE_OPTIONS,
  OPTIONLESS_TYPES,
  isObjective,
  TRUE_FALSE_OPTIONS,
  READING_JUDGE_OPTIONS
} from '@/utils/constants'
import type { QuestionOption, QuestionUpsertDTO } from '@/api/types'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const subjectStore = useSubjectStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)

interface OptionRow {
  key: string
  text: string
}

interface FormModel {
  subjectId: number | null
  chapterId: number | null
  type: string
  stem: string
  options: OptionRow[]
  answerSingle: string
  answerMultiple: string[]
  answerPoints: string[]
  analysis: string
  difficulty: number | undefined
  source: string
  sourceYear: number | null
  score: number | null
  status: 'DRAFT' | 'PUBLISHED'
}

const form = reactive<FormModel>({
  subjectId: null,
  chapterId: null,
  type: '',
  stem: '',
  options: [],
  answerSingle: '',
  answerMultiple: [],
  answerPoints: [''],
  analysis: '',
  difficulty: undefined,
  source: '',
  sourceYear: null,
  score: 2,
  status: 'PUBLISHED'
})

const isEdit = computed(() => route.name === 'question-edit')
const questionId = computed(() => Number(route.params.id))
const pageTitle = computed(() => (isEdit.value ? '编辑题目' : '新建题目'))

const objective = computed(() => isObjective(form.type))
/** 判断题 / 阅读判断使用固定的 T/F(/NG) 选项,不可增删 */
const fixedJudgeType = computed(
  () => form.type === 'TRUE_FALSE' || form.type === 'READING_JUDGE'
)
/**
 * 完形补文这类题**卷面上没有选项**:括号里给词根,填出正确形式。
 * 答案是一段文字而不是选项字母,所以选项区不显示,答案用输入框。
 */
const isOptionless = computed(() => OPTIONLESS_TYPES.includes(form.type as never))
const editableOptions = computed(
  () => objective.value && !fixedJudgeType.value && !isOptionless.value
)
const isMulti = computed(() => form.type === 'MULTIPLE_CHOICE')
const isReadingJudge = computed(() => form.type === 'READING_JUDGE')

const chapterOptions = computed(() =>
  form.subjectId == null ? [] : (subjectStore.chaptersBySubject[form.subjectId] ?? [])
)

function letterFor(index: number): string {
  return String.fromCharCode(65 + index)
}

function defaultOptions(): OptionRow[] {
  return Array.from({ length: 4 }, (_, i) => ({ key: letterFor(i), text: '' }))
}

function reletter() {
  form.options.forEach((opt, i) => {
    opt.key = letterFor(i)
  })
}

/** 单选/多选可点选的字母 */
const letterChoices = computed(() => form.options.map((_, i) => letterFor(i)))

/** 判断题 / 阅读判断的答案选项 */
const judgeChoices = computed(() =>
  (isReadingJudge.value ? READING_JUDGE_OPTIONS : TRUE_FALSE_OPTIONS).map((o) => ({
    value: o.key,
    label: `${o.key} · ${o.text}`
  }))
)

/* ---------------- 校验 ---------------- */

const validateScore = (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
  const n = Number(value)
  if (value == null || value === '' || Number.isNaN(n) || n <= 0) {
    callback(new Error('分值必须大于 0'))
    return
  }
  callback()
}

const validateOptions = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
  if (!editableOptions.value) {
    callback()
    return
  }
  if (form.options.length < 2) {
    callback(new Error('至少需要 2 个选项'))
    return
  }
  if (form.options.some((o) => !o.text.trim())) {
    callback(new Error('每个选项的内容都不能为空'))
    return
  }
  callback()
}

const validateAnswerSingle = (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
  if (!objective.value || isMulti.value) {
    callback()
    return
  }
  if (!value) {
    callback(new Error(isOptionless.value ? '请填写正确答案' : '请点选正确答案'))
    return
  }
  callback()
}

const validateAnswerMultiple = (
  _rule: unknown,
  value: unknown,
  callback: (error?: Error) => void
) => {
  if (!isMulti.value) {
    callback()
    return
  }
  const arr = Array.isArray(value) ? value : []
  if (arr.length < 2) {
    callback(new Error('多选题至少选择 2 项'))
    return
  }
  if (arr.length > 5) {
    callback(new Error('多选题最多选择 5 项'))
    return
  }
  callback()
}

const validateAnswerPoints = (
  _rule: unknown,
  _value: unknown,
  callback: (error?: Error) => void
) => {
  if (!form.type || objective.value) {
    callback()
    return
  }
  if (!form.answerPoints.some((p) => p.trim())) {
    callback(new Error('参考答案要点至少填写一条'))
    return
  }
  callback()
}

const rules: FormRules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  stem: [
    { required: true, message: '请输入题干', trigger: 'blur' },
    { max: 2000, message: '题干不超过 2000 字', trigger: 'blur' }
  ],
  score: [{ validator: validateScore, trigger: 'blur' }],
  options: [{ validator: validateOptions, trigger: 'change' }],
  answerSingle: [{ validator: validateAnswerSingle, trigger: 'change' }],
  answerMultiple: [{ validator: validateAnswerMultiple, trigger: 'change' }],
  answerPoints: [{ validator: validateAnswerPoints, trigger: 'change' }]
}

/* ---------------- 交互 ---------------- */

function onSubjectChange(subjectId: number | null) {
  form.chapterId = null
  if (subjectId != null) void subjectStore.fetchChapters(subjectId).catch(() => undefined)
}

function onTypeChange() {
  form.answerSingle = ''
  form.answerMultiple = []
  if (form.type === 'TRUE_FALSE') {
    form.options = TRUE_FALSE_OPTIONS.map((o) => ({ ...o }))
  } else if (form.type === 'READING_JUDGE') {
    form.options = READING_JUDGE_OPTIONS.map((o) => ({ ...o }))
  } else if (isOptionless.value) {
    form.options = [] // 完形补文没有选项,别把上一题的选项带过来
  } else if (isObjective(form.type)) {
    const stale = form.options.some((o) => o.key === 'T' || o.key === 'NG')
    if (stale || form.options.length < 2) form.options = defaultOptions()
    else reletter()
  } else if (form.answerPoints.length === 0) {
    form.answerPoints = ['']
  }
}

function addOption() {
  form.options.push({ key: '', text: '' })
  reletter()
}

function removeOption(index: number) {
  if (form.options.length <= 2) return
  form.options.splice(index, 1)
  reletter()
}

function addPoint() {
  form.answerPoints.push('')
}

function removePoint(index: number) {
  if (form.answerPoints.length <= 1) {
    form.answerPoints = ['']
    return
  }
  form.answerPoints.splice(index, 1)
}

/* ---------------- 加载 / 保存 ---------------- */

async function loadQuestion() {
  loading.value = true
  try {
    const q = await getQuestion(questionId.value)
    form.subjectId = q.subjectId
    form.chapterId = q.chapterId
    form.type = q.type
    form.stem = q.stem
    form.options =
      q.options && q.options.length
        ? q.options.map((o) => ({ key: o.key, text: o.text }))
        : isOptionless.value
          ? []
          : defaultOptions()
    if (q.type === 'MULTIPLE_CHOICE') {
      form.answerMultiple = (q.answer ?? '').toUpperCase().split('').filter(Boolean)
      form.answerSingle = ''
    } else {
      form.answerSingle = q.answer ?? ''
      form.answerMultiple = []
    }
    form.answerPoints = q.answerPoints && q.answerPoints.length ? [...q.answerPoints] : ['']
    form.analysis = q.analysis ?? ''
    form.difficulty = q.difficulty ?? undefined
    form.source = q.source ?? ''
    form.sourceYear = q.sourceYear ?? null
    form.score = q.score
    form.status = q.status
    await subjectStore.fetchChapters(q.subjectId).catch(() => undefined)
  } catch {
    // 错误提示已由 request.ts 统一弹出
  } finally {
    loading.value = false
  }
}

function buildOptions(): QuestionOption[] | null {
  if (!objective.value) return null
  if (form.type === 'TRUE_FALSE') return TRUE_FALSE_OPTIONS.map((o) => ({ ...o }))
  if (form.type === 'READING_JUDGE') return READING_JUDGE_OPTIONS.map((o) => ({ ...o }))
  if (isOptionless.value) return []
  return form.options.map((o, i) => ({ key: letterFor(i), text: o.text.trim() }))
}

function buildPayload(): QuestionUpsertDTO {
  const opts = buildOptions()
  let answer: string | null = null
  if (objective.value) {
    answer = isMulti.value ? [...form.answerMultiple].sort().join('') : form.answerSingle
  }
  const answerPoints =
    objective.value || form.type === ''
      ? null
      : form.answerPoints.map((p) => p.trim()).filter(Boolean)
  return {
    subjectId: form.subjectId ?? 0,
    chapterId: form.chapterId,
    type: form.type,
    stem: form.stem.trim(),
    options: opts,
    answer,
    answerPoints,
    analysis: form.analysis.trim() || null,
    difficulty: form.difficulty ?? null,
    source: form.source.trim() || null,
    sourceYear: form.sourceYear,
    score: Number(form.score),
    status: form.status
  }
}

/**
 * 从题库列表点「编辑」进来时会带 back(列表当时的地址,含筛选条件),
 * 保存/取消都回那一屏 —— 否则库里一筛、进来改一道、返回就回到「全部」了。
 */
const backTarget = computed(() => {
  const b = route.query.back
  return typeof b === 'string' && b.startsWith('/questions') ? b : '/questions'
})

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value) {
      await updateQuestion(questionId.value, buildPayload())
      ElMessage.success('修改已保存')
    } else {
      await createQuestion(buildPayload())
      ElMessage.success('题目已创建')
    }
    router.push(backTarget.value)
  } catch {
    // 错误提示已由 request.ts 统一弹出
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await subjectStore.fetchSubjects().catch(() => undefined)
  if (subjectStore.subjects.length === 0) return
  form.options = defaultOptions()
  if (isEdit.value && Number.isFinite(questionId.value)) {
    await loadQuestion()
  }
})
</script>

<template>
  <section class="edit">
    <header class="edit__head">
      <h1 class="edit__title">{{ pageTitle }}</h1>
      <el-button text @click="router.push(backTarget)">
        <AppIcon name="chevronRight" :size="16" class="edit__back-icon" /> 返回题库
      </el-button>
    </header>

    <div v-if="loading" class="card edit__loading">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else-if="subjectStore.subjects.length === 0" class="card">
      <EmptyState
        text="还没有科目,先建一门课再录题。"
        icon="book"
        action-text="去科目管理"
        @action="router.push('/subjects')"
      />
    </div>

    <div v-else class="card edit__card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <!-- 科目 / 章节 -->
        <div class="edit__grid">
          <el-form-item label="科目" prop="subjectId">
            <el-select
              v-model="form.subjectId"
              placeholder="请选择科目"
              clearable
              @change="onSubjectChange"
            >
              <el-option
                v-for="s in subjectStore.subjects"
                :key="s.id"
                :label="s.shortName"
                :value="s.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="章节(可选)">
            <el-select
              v-model="form.chapterId"
              placeholder="不归章"
              clearable
              :disabled="form.subjectId == null"
              :no-data-text="form.subjectId == null ? '先选科目' : '该科目暂无章节'"
            >
              <el-option v-for="c in chapterOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </div>

        <!-- 题型 -->
        <el-form-item label="题型" prop="type">
          <el-select v-model="form.type" placeholder="请选择题型" @change="onTypeChange">
            <el-option
              v-for="t in QUESTION_TYPE_OPTIONS"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>

        <!-- 题干 -->
        <el-form-item label="题干" prop="stem">
          <el-input
            v-model="form.stem"
            type="textarea"
            :rows="4"
            resize="vertical"
            maxlength="2000"
            show-word-limit
            placeholder="输入题干,保留换行"
          />
        </el-form-item>

        <!-- 选项区:仅客观题;完形补文卷面上没有选项,整块不显示 -->
        <el-form-item v-if="objective && !isOptionless" label="选项" prop="options">
          <!-- 判断题 / 阅读判断:固定选项 -->
          <ul v-if="fixedJudgeType" class="fixed-options">
            <li v-for="o in form.options" :key="o.key" class="fixed-options__row">
              <span class="opt-key mono">{{ o.key }}</span>
              <span class="fixed-options__text">{{ o.text }}</span>
            </li>
          </ul>

          <!-- 其他客观题:可增删 -->
          <div v-else class="options">
            <div v-for="(opt, i) in form.options" :key="i" class="option-row">
              <span class="opt-key mono">{{ letterFor(i) }}</span>
              <el-input v-model="opt.text" placeholder="选项内容" />
              <el-button
                text
                type="danger"
                :disabled="form.options.length <= 2"
                aria-label="删除该选项"
                @click="removeOption(i)"
              >
                <AppIcon name="trash" :size="16" />
              </el-button>
            </div>
            <el-button link type="primary" @click="addOption">
              <AppIcon name="plus" :size="14" /> 添加选项
            </el-button>
          </div>
        </el-form-item>

        <!-- 正确答案:单选 / 判断题 / 阅读判断 → Radio;多选 → Checkbox;完形补文 → 手填 -->
        <el-form-item
          v-if="objective && !isMulti"
          :label="isOptionless ? '正确答案(填入后的词)' : '正确答案'"
          prop="answerSingle"
        >
          <el-input
            v-if="isOptionless"
            v-model="form.answerSingle"
            placeholder="填入正确形式,如 users / changing"
            maxlength="100"
            class="mono answer-input"
          />
          <el-radio-group v-else v-model="form.answerSingle">
            <template v-if="fixedJudgeType">
              <el-radio v-for="c in judgeChoices" :key="c.value" :value="c.value">
                {{ c.label }}
              </el-radio>
            </template>
            <template v-else>
              <el-radio v-for="c in letterChoices" :key="c" :value="c">{{ c }}</el-radio>
            </template>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="isMulti" label="正确答案(多选)" prop="answerMultiple">
          <el-checkbox-group v-model="form.answerMultiple">
            <el-checkbox v-for="c in letterChoices" :key="c" :value="c">{{ c }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <!-- 参考答案要点:仅主观题 -->
        <el-form-item v-if="objective === false && form.type" label="参考答案要点" prop="answerPoints">
          <div class="points">
            <div v-for="(_, i) in form.answerPoints" :key="i" class="point-row">
              <span class="point-index mono">{{ i + 1 }}</span>
              <el-input v-model="form.answerPoints[i]" placeholder="一条要点" />
              <el-button
                text
                type="danger"
                aria-label="删除该要点"
                @click="removePoint(i)"
              >
                <AppIcon name="trash" :size="16" />
              </el-button>
            </div>
            <el-button link type="primary" @click="addPoint">
              <AppIcon name="plus" :size="14" /> 添加要点
            </el-button>
          </div>
        </el-form-item>

        <!-- 解析 -->
        <el-form-item label="解析(可选)">
          <el-input
            v-model="form.analysis"
            type="textarea"
            :rows="3"
            resize="vertical"
            placeholder="解析内容"
          />
        </el-form-item>

        <!-- 难度 / 分值 / 年份 / 状态 -->
        <div class="edit__grid">
          <el-form-item label="难度">
            <el-radio-group v-model="form.difficulty">
              <el-radio :value="1">简单</el-radio>
              <el-radio :value="2">中等</el-radio>
              <el-radio :value="3">较难</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="分值" prop="score">
            <el-input-number v-model="form.score" :precision="1" :step="1" :min="0" />
          </el-form-item>
        </div>

        <div class="edit__grid">
          <el-form-item label="来源(可选)">
            <el-input v-model="form.source" placeholder="如 2024年10月真题" />
          </el-form-item>

          <el-form-item label="年份(可选)">
            <el-input-number v-model="form.sourceYear" :min="1900" :max="2100" :step="1" />
          </el-form-item>
        </div>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="PUBLISHED">已入库</el-radio>
            <el-radio value="DRAFT">待校对</el-radio>
          </el-radio-group>
        </el-form-item>

        <p class="edit__hint">
          当前题型:{{ QUESTION_TYPE_LABEL[form.type] ?? '未选择' }}
          <template v-if="objective"> · 客观题由系统自动判分</template>
          <template v-else-if="form.type"> · 主观题需自评</template>
        </p>

        <footer class="edit__actions">
          <el-button @click="router.push(backTarget)">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </footer>
      </el-form>
    </div>
  </section>
</template>

<style scoped>
.edit {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.edit__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.edit__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.edit__back-icon {
  transform: rotate(180deg);
  vertical-align: -3px;
}

.card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.edit__loading {
  padding: var(--space-md);
}

.edit__card {
  padding: var(--space-lg) var(--space-md);
}

.edit__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 var(--space-md);
}

@media (max-width: 560px) {
  .edit__grid {
    grid-template-columns: 1fr;
  }
}

.options,
.points,
.fixed-options {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  width: 100%;
}

/* 完形补文的答案是一段文字,给个宽度上限免得铺满整行 */
.answer-input {
  max-width: 320px;
}

.option-row,
.point-row,
.fixed-options__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.opt-key,
.point-index {
  flex: 0 0 28px;
  width: 28px;
  text-align: center;
  font-weight: 600;
  color: var(--text-secondary);
}

.fixed-options__row {
  min-height: var(--tap-target-min);
  padding: 0 var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background-color: var(--bg-page);
}

.fixed-options__text {
  font-size: var(--font-size-option);
  color: var(--text-primary);
}

.edit__hint {
  margin: var(--space-sm) 0 var(--space-md);
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.edit__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--border-color);
}
</style>