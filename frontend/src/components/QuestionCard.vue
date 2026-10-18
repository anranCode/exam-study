<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import OptionCard from './OptionCard.vue'
import SubjectTag from './SubjectTag.vue'
import type { AnswerRecord, PracticeQuestionVO, QuestionOption } from '@/api/types'
import {
  QUESTION_TYPE_LABEL,
  isObjective,
  TRUE_FALSE_OPTIONS,
  READING_JUDGE_OPTIONS,
  type OptionVisualState
} from '@/utils/constants'

/**
 * 题干卡片:题型标签 + 分值 + 科目标签 → 题干(17px/1.75,pre-wrap)→ 选项 → 判定后解析。
 * 主观题不显示选项与解析(主观题分支由 PracticeView 负责)。
 */
const props = defineProps<{
  question: PracticeQuestionVO
  selectedKeys: string[]
  record: AnswerRecord | null
}>()

const emit = defineEmits<{ (e: 'select', key: string): void }>()

const typeLabel = computed(
  () =>
    props.question.typeLabel ||
    QUESTION_TYPE_LABEL[props.question.type] ||
    props.question.type
)

const objective = computed(() => isObjective(props.question.type))

/** 判断题 / 阅读判断后端可能不返回 options,这里补齐固定选项 */
const options = computed<QuestionOption[]>(() => {
  if (props.question.options && props.question.options.length > 0) return props.question.options
  if (props.question.type === 'TRUE_FALSE') return TRUE_FALSE_OPTIONS
  if (props.question.type === 'READING_JUDGE') return READING_JUDGE_OPTIONS
  return []
})

/** 没有选项的客观题(完形补文):卷面就是填空,用一个输入框作答 */
const freeAnswerMode = computed(() => objective.value && options.value.length === 0)

/** 填空作答复用 selectedKeys 的第一个值,提交时 normalizeAnswer 会取它 */
const freeAnswer = computed({
  get: () => props.selectedKeys[0] ?? '',
  set: (value: string) => emit('select', value)
})

/** 后端返回连写答案(如 ABD / T / NG),这里拆成字母集合 */
const correctKeys = computed<string[]>(() => {
  const raw = props.record?.result.correctAnswer
  if (!raw) return []
  return raw.toUpperCase().replace(/[^A-Z]/g, '').split('')
})

function stateFor(key: string): OptionVisualState {
  const rec = props.record
  if (rec) {
    if (correctKeys.value.includes(key)) return 'correct'
    if (rec.selected.includes(key)) return 'wrong'
    return 'default'
  }
  return props.selectedKeys.includes(key) ? 'selected' : 'default'
}

const graded = computed(() => !!props.record)
</script>

<template>
  <article class="question-card">
    <header class="question-card__meta">
      <span class="type-badge">{{ typeLabel }}</span>
      <span class="score mono">{{ question.score }} 分</span>
      <span v-if="question.isReview" class="review-badge">复习</span>
      <SubjectTag
        v-if="question.subjectShortName"
        :name="question.subjectShortName"
        :color="question.subjectColor ?? undefined"
        size="sm"
      />
    </header>

    <p v-if="question.sectionName" class="question-card__section">{{ question.sectionName }}</p>

    <p class="question-card__stem stem-text wrap-anywhere">{{ question.stem }}</p>

    <ul v-if="objective && options.length" class="question-card__options">
      <li v-for="opt in options" :key="opt.key">
        <OptionCard
          :option-key="opt.key"
          :text="opt.text"
          :state="stateFor(opt.key)"
          :disabled="graded"
          @select="$emit('select', opt.key)"
        />
      </li>
    </ul>

    <!-- 没有选项的客观题(完形补文):卷面本身就是填空,给输入框而不是选项卡片 -->
    <div v-if="freeAnswerMode" class="question-card__fill">
      <el-input
        v-model="freeAnswer"
        :disabled="graded"
        maxlength="100"
        placeholder="填入正确形式"
        class="question-card__fill-input"
      />
      <p v-if="!graded" class="question-card__fill-hint">
        只需填这一空的词(括号里给的是词根),不用抄整句。
      </p>
    </div>

    <!-- 解析区:仅客观题判定后展开(答错自动展开,无需额外点击) -->
    <section v-if="objective && graded" class="analysis">
      <div class="analysis__row">
        <span class="analysis__label">正确答案</span>
        <span class="analysis__correct mono">{{ record?.result.correctAnswer ?? '—' }}</span>
        <span
          v-if="record && record.result.isCorrect !== null"
          class="analysis__verdict"
          :class="record.result.isCorrect ? 'analysis__verdict--ok' : 'analysis__verdict--bad'"
        >
          <AppIcon :name="record.result.isCorrect ? 'check' : 'close'" :size="14" />
          {{ record.result.isCorrect ? '答对了' : '答错了' }}
        </span>
      </div>
      <p v-if="record?.result.analysis" class="analysis__text wrap-anywhere">
        {{ record.result.analysis }}
      </p>
      <p v-if="record?.result.nextReviewAt" class="analysis__review">
        下次复习时间:<span class="mono">{{ record.result.nextReviewAt }}</span>
      </p>
    </section>
  </article>
</template>

<style scoped>
.question-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.question-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
}

.type-badge {
  padding: 2px var(--space-sm);
  border-radius: var(--radius-sm);
  background-color: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-aux);
  line-height: var(--line-height-aux);
}

.score {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.review-badge {
  padding: 2px var(--space-sm);
  border-radius: var(--radius-sm);
  background-color: var(--color-warning-light);
  color: var(--color-warning);
  font-size: var(--font-size-aux);
  line-height: var(--line-height-aux);
}

.question-card__section {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.question-card__stem {
  color: var(--text-primary);
}

.question-card__options {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

/* 填空作答(完形补文) */
.question-card__fill {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.question-card__fill-input {
  max-width: 320px;
}

.question-card__fill-input :deep(.el-input__inner) {
  font-family: var(--font-mono);
}

.question-card__fill-hint {
  margin: 0;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.analysis {
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background-color: var(--bg-page);
  border: 1px solid var(--border-color);
}

.analysis__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.analysis__label {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.analysis__correct {
  font-size: var(--font-size-option);
  font-weight: 600;
  color: var(--color-success);
}

.analysis__verdict {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-inline-start: auto;
  font-size: var(--font-size-aux);
}

.analysis__verdict--ok {
  color: var(--color-success);
}

.analysis__verdict--bad {
  color: var(--color-danger);
}

.analysis__text {
  font-size: var(--font-size-body);
  line-height: var(--line-height-body);
  color: var(--text-primary);
  white-space: pre-wrap;
}

.analysis__review {
  margin-top: var(--space-sm);
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}
</style>