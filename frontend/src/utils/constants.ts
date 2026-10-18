import type { Confidence, ExamSlot, PracticeMode, PracticeModeUpper } from '@/api/types'

/** 题型中文名 —— 新增题型时只需改这里 */
export const QUESTION_TYPE_LABEL: Record<string, string> = {
  SINGLE_CHOICE: '单项选择题',
  MULTIPLE_CHOICE: '多项选择题',
  TRUE_FALSE: '判断题',
  READING_JUDGE: '阅读判断',
  READING_CHOICE: '阅读选择',
  SECTION_MATCH: '概括段落大意和补全句子',
  SENTENCE_FILL: '填句补文',
  WORD_FILL: '填词补文',
  CLOZE_TRANSFORM: '完形补文',
  SHORT_ANSWER: '简答题',
  ESSAY: '论述题',
  MATERIAL_ANALYSIS: '材料分析题',
  WRITING: '短文写作'
}

/** 客观题:后端自动判分 */
export const OBJECTIVE_TYPES = [
  'SINGLE_CHOICE',
  'MULTIPLE_CHOICE',
  'TRUE_FALSE',
  'READING_JUDGE',
  'READING_CHOICE',
  'SECTION_MATCH',
  'SENTENCE_FILL',
  'WORD_FILL',
  'CLOZE_TRANSFORM'
] as const

/** 主观题:需要自评 */
export const SUBJECTIVE_TYPES = ['SHORT_ANSWER', 'ESSAY', 'MATERIAL_ANALYSIS', 'WRITING'] as const

/**
 * 卷面上本来就没有选项的客观题:完形补文只给括号里的词根,要求填出正确形式。
 * 必须与后端 QuestionValidator 的 optionsRequired 规则一致,否则预览会把它们全标成不合格。
 */
export const OPTIONLESS_TYPES = ['CLOZE_TRANSFORM'] as const

export const isObjective = (t: string): boolean => OBJECTIVE_TYPES.includes(t as never)

/** 题型下拉选项 */
export const QUESTION_TYPE_OPTIONS = Object.entries(QUESTION_TYPE_LABEL).map(([value, label]) => ({
  value,
  label
}))

/* ---------------- 练习模式 ---------------- */

export const PRACTICE_MODE_LABEL: Record<PracticeMode, string> = {
  daily: '每日任务',
  random: '随机练习',
  chapter: '章节练习',
  mock: '模拟考试',
  review: '错题复习'
}

export const PRACTICE_MODE_OPTIONS = (
  Object.keys(PRACTICE_MODE_LABEL) as PracticeMode[]
).map((value) => ({ value, label: PRACTICE_MODE_LABEL[value] }))

/** 路由参数(小写)→ API 枚举名(大写) */
export const PRACTICE_MODE_UPPER: Record<PracticeMode, PracticeModeUpper> = {
  daily: 'DAILY',
  random: 'RANDOM',
  chapter: 'CHAPTER',
  mock: 'MOCK',
  review: 'REVIEW'
}

/** API 枚举名(大写)→ 路由参数(小写) */
export const PRACTICE_MODE_LOWER: Record<string, PracticeMode> = {
  DAILY: 'daily',
  RANDOM: 'random',
  CHAPTER: 'chapter',
  MOCK: 'mock',
  REVIEW: 'review'
}

/** 判断题固定选项(T 对 / F 错) */
export const TRUE_FALSE_OPTIONS: { key: string; text: string }[] = [
  { key: 'T', text: '对' },
  { key: 'F', text: '错' }
]

/** 阅读判断固定选项(T 对 / F 错 / NG 未提及) */
export const READING_JUDGE_OPTIONS: { key: string; text: string }[] = [
  { key: 'T', text: '对' },
  { key: 'F', text: '错' },
  { key: 'NG', text: '未提及' }
]

/* ---------------- 选项卡片视觉状态 ---------------- */

export type OptionVisualState = 'default' | 'selected' | 'correct' | 'wrong'

/* ---------------- 信心度 ---------------- */

export const CONFIDENCE_LABEL: Record<Confidence, string> = {
  SURE: '确定',
  UNSURE: '不确定',
  WRONG: '答错'
}

/* ---------------- 考试场次 ---------------- */

export const EXAM_SLOT_LABEL: Record<ExamSlot, string> = {
  D1_AM: '第一天上午 09:00—11:30',
  D1_PM: '第一天下午 14:30—17:00',
  D2_AM: '第二天上午 09:00—11:30',
  D2_PM: '第二天下午 14:30—17:00'
}

export const EXAM_SLOT_OPTIONS = (Object.keys(EXAM_SLOT_LABEL) as ExamSlot[]).map((value) => ({
  value,
  label: EXAM_SLOT_LABEL[value]
}))

/* ---------------- 后端错误码 ---------------- */

export const ERROR_CODE = {
  PARAM_INVALID: 1001,
  SUBJECT_NOT_FOUND: 2001,
  CHAPTER_NOT_FOUND: 2002,
  QUESTION_NOT_FOUND: 2003,
  SUBJECT_HAS_QUESTIONS: 2005,
  CHAPTER_HAS_CONTENT: 2006,
  SESSION_NOT_FOUND: 3001,
  SESSION_FINISHED: 3002
} as const