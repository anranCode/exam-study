/*
 * types.ts —— 业务类型(后端 OpenAPI 生成前的手写来源)
 *
 * 后端启动后执行 `npm run gen:api`,再把这里的类型替换为
 * `export type SubjectVO = components['schemas']['SubjectVO']` 之类的别名。
 */

/** 考试场次 */
export type ExamSlot = 'D1_AM' | 'D1_PM' | 'D2_AM' | 'D2_PM'

/** 练习模式 */
export type PracticeMode = 'daily' | 'random' | 'chapter' | 'mock' | 'review' | 'wrong'

/** 作答信心度:不确定 / 答错 / 确定 */
export type Confidence = 'SURE' | 'UNSURE' | 'WRONG'

/** 练习状态机 */
export type PracticeStatus = 'IDLE' | 'ANSWERING' | 'GRADING' | 'GRADED' | 'FINISHED'

/** 题型 */
export type QuestionType =
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'READING_JUDGE'
  | 'READING_CHOICE'
  | 'SECTION_MATCH'
  | 'SENTENCE_FILL'
  | 'WORD_FILL'
  | 'CLOZE_TRANSFORM'
  | 'SHORT_ANSWER'
  | 'ESSAY'
  | 'MATERIAL_ANALYSIS'
  | 'WRITING'

/** 分页响应体 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  pages: number
}

/* ---------------- 科目 ---------------- */

export interface SubjectVO {
  id: number
  code: string
  name: string
  shortName: string
  color: string
  examDate: string | null // 'yyyy-MM-dd'
  examSlot: ExamSlot | null
  totalScore: number
  passScore: number
  sortOrder: number
  enabled: boolean
  remark: string | null
  questionCount: number
  dueReviewCount: number
  masteredCount: number
}

export interface SubjectUpsertDTO {
  code: string
  name: string
  shortName: string
  color: string
  examDate?: string | null
  examSlot?: string | null
  totalScore?: number
  passScore?: number
  sortOrder?: number
  enabled?: boolean
  remark?: string | null
}

/* ---------------- 章节 ---------------- */

export interface ChapterVO {
  id: number
  subjectId: number
  parentId: number | null
  name: string
  sortOrder: number
  questionCount: number
  /** 后端返回扁平列表,树结构由前端 store 组装 */
  children?: ChapterVO[]
}

export interface ChapterUpsertDTO {
  subjectId: number
  name: string
  parentId?: number | null
  sortOrder?: number
}

/* ---------------- 复习 / 统计 ---------------- */

export interface DueSubject {
  subjectId: number
  shortName: string
  color: string
  dueCount: number
}

export interface DueSummaryVO {
  date: string
  totalDue: number
  bySubject: DueSubject[]
  newQuestionQuota: number
  checkedInToday: boolean
}

export interface StatsOverviewVO {
  totalQuestions: number
  masteredCount: number
  avgAccuracy: number | null
  streakDays: number
  todayAnswered: number
  todayCorrect: number
  /** 累计答对题数(只含客观题) */
  totalCorrect: number
  /** 累计作答的客观题次数 */
  totalAnswered: number
}

/** GET /stats/subjects —— 单个科目的正确率与掌握度 */
export interface SubjectStatsVO {
  subjectId: number
  code: string
  name: string
  shortName: string
  color: string
  questionCount: number
  answeredCount: number
  correctCount: number
  accuracy: number | null
  reviewCount: number
  masteredCount: number
  masteryRate: number | null
}

/** GET /stats/trend —— 趋势图上的一个点(没作答的日期也会返回 0) */
export interface TrendPointVO {
  date: string
  answered: number
  correct: number
  accuracy: number | null
}

/** GET /stats/chapters —— 章节掌握度 */
export interface ChapterStatsVO {
  chapterId: number | null
  chapterName: string
  questionCount: number
  answeredCount: number
  correctCount: number
  accuracy: number | null
  masteredCount: number
  masteryRate: number | null
}


/* ---------------- 练习与作答(阶段 4) ---------------- */

export interface QuestionOption {
  key: string
  text: string
}

export interface QuestionVO {
  id: number
  subjectId: number
  chapterId: number | null
  type: QuestionType
  stem: string
  score: number
  options?: QuestionOption[]
}

/** 练习模式:API 层使用大写枚举名 */
export type PracticeModeUpper = 'DAILY' | 'RANDOM' | 'CHAPTER' | 'MOCK' | 'REVIEW' | 'WRONG'

export interface PracticeSessionCreateDTO {
  mode: PracticeModeUpper
  subjectId?: number | null
  chapterId?: number | null
  count?: number | null
  paperId?: number | null
  /** 指定题目:非空时后端忽略组题模式,就按这几道出题(错题本「立即复习」某一道走这里) */
  questionIds?: number[] | null
  /** 题型筛选:只抽这几种题型,空表示不限(RANDOM / CHAPTER / REVIEW / WRONG 生效) */
  types?: string[] | null
}

/** 练习中的题目:★ 绝不含 answer / answerPoints / analysis */
export interface PracticeQuestionVO {
  id: number
  subjectId: number
  subjectShortName: string | null
  subjectColor: string | null
  chapterId: number | null
  chapterName: string | null
  type: string
  typeLabel: string
  stem: string
  options: QuestionOption[] | null
  score: number
  isReview: boolean
  sectionName: string | null
}

export interface PracticeSessionVO {
  sessionId: number
  mode: string
  totalCount: number
  totalScore: number | null
  /** 创建时若已有 ONGOING 场次,这里是那个场次 id(已被置为 ABANDONED,可用于「继续上次」) */
  ongoingSessionId: number | null
  questions: PracticeQuestionVO[]
}

export interface AnswerSubmitDTO {
  questionId: number
  userAnswer?: string | null
  confidence: Confidence
  spentSeconds?: number
}

export interface AnswerProgressVO {
  answered: number
  correct: number
  total: number
}

export interface AnswerResultVO {
  questionId: number
  isCorrect: boolean | null
  correctAnswer: string | null
  answerPoints: string[] | null
  analysis: string | null
  nextReviewAt: string | null
  isReview: boolean
  selfScore: number | null
  totalPoints: number | null
  /** 该题累计答错次数(错题本按它倒序) */
  wrongCount: number
  /** 当前连续答对次数;满 3 次自动移出错题本 */
  correctStreak: number
  /** 本次作答是否让该题移出了错题本 */
  mastered: boolean
  progress: AnswerProgressVO
}

export interface SelfScoreDTO {
  selfScore: number
  totalPoints: number
}

export interface WrongQuestionVO {
  questionId: number
  stem: string
  type: string
  typeLabel: string
  subjectShortName: string | null
  userAnswer: string | null
  correctAnswer: string | null
  isReview: boolean
}

export interface SessionSummaryVO {
  sessionId: number
  mode: string
  totalCount: number
  answeredCount: number
  objectiveAnsweredCount: number
  correctCount: number
  score: number | null
  totalScore: number | null
  /** 0..1,只按客观题算,无客观题为 null */
  accuracy: number | null
  durationSeconds: number
  reviewCreated: number
  reviewUpdated: number
  wrongQuestions: WrongQuestionVO[]
}

export interface PracticeSessionListItemVO {
  id: number
  mode: string
  subjectId: number | null
  subjectShortName: string | null
  status: 'ONGOING' | 'FINISHED' | 'ABANDONED'
  totalCount: number
  answeredCount: number
  correctCount: number
  score: number | null
  totalScore: number | null
  accuracy: number | null
  startedAt: string
  finishedAt: string | null
  durationSeconds: number | null
}

export interface SessionAnswerVO {
  questionId: number
  stem: string
  type: string
  typeLabel: string
  subjectShortName: string | null
  chapterName: string | null
  userAnswer: string | null
  isCorrect: boolean | null
  confidence: string
  selfScore: number | null
  totalPoints: number | null
  correctAnswer: string | null
  answerPoints: string[] | null
  analysis: string | null
  answeredAt: string
}

export interface PracticeSessionDetailVO {
  id: number
  mode: string
  status: string
  subjectShortName: string | null
  totalCount: number
  answeredCount: number
  correctCount: number
  score: number | null
  totalScore: number | null
  accuracy: number | null
  startedAt: string
  finishedAt: string | null
  durationSeconds: number | null
  answers: SessionAnswerVO[]
}

export interface OngoingSessionVO {
  sessionId: number
  mode: string
  answeredCount: number
  totalCount: number
  startedAt: string
}

/** answeredMap 的值:一题的判定结果(判定状态的唯一来源) */
export interface AnswerRecord {
  questionId: number
  selected: string[]
  confidence: Confidence
  result: AnswerResultVO
}

/** 最近一次练习(今日页展示用) */
export interface LastSessionVO {
  sessionId: number
  mode: PracticeMode
  date: string
  score: number
  totalScore: number
  accuracy: number | null
}

/* ---------------- 题目(阶段 2 后端已上线) ---------------- */

export interface QuestionDetailVO {
  id: number
  subjectId: number
  subjectShortName: string | null
  subjectColor: string | null
  chapterId: number | null
  chapterName: string | null
  type: QuestionType | string
  typeLabel: string
  stem: string
  options: QuestionOption[] | null
  answer: string | null
  answerPoints: string[] | null
  analysis: string | null
  difficulty: number | null
  source: string | null
  sourceYear: number | null
  score: number
  status: 'DRAFT' | 'PUBLISHED'
  aiAnalysis: string | null
  hasAiAnalysis: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface QuestionUpsertDTO {
  subjectId: number
  chapterId: number | null
  type: string
  stem: string
  options: QuestionOption[] | null
  answer: string | null
  answerPoints: string[] | null
  analysis: string | null
  difficulty: number | null
  source: string | null
  sourceYear: number | null
  score: number
  status: 'DRAFT' | 'PUBLISHED'
}

export interface QuestionQuery {
  subjectId?: number
  chapterId?: number
  type?: string
  keyword?: string
  sourceYear?: number
  status?: string
  page?: number
  size?: number
  sort?: string
}

/** AI 生成的解析(预览或已保存) */
export interface AiAnalysisVO {
  analysis: string
}

export interface AiAnalysisRequest {
  type: string
  stem: string
  answerPoints: string[]
}

/* ---------------- 批量导入(阶段 3) ---------------- */

export interface ImportItemDTO {
  sectionName?: string | null
  type: string
  chapterName?: string | null
  stem: string
  options?: QuestionOption[] | null
  answer?: string | null
  answerPoints?: string[] | null
  analysis?: string | null
  sourceYear?: number | null
  score?: number | null
  difficulty?: number | null
  /** DRAFT = 答案待校对。PDF 识别里由模型代答的题目都是 DRAFT */
  status?: 'DRAFT' | 'PUBLISHED' | null
}

export interface ImportPaperDTO {
  name: string
  type?: string | null
  year?: number | null
  totalScore?: number | null
  durationMin?: number | null
  remark?: string | null
}

export interface QuestionImportRequest {
  subjectCode: string
  paper?: ImportPaperDTO | null
  items: ImportItemDTO[]
}

export interface ImportError {
  /** 1-based,对应文件里的第 N 条 */
  index: number
  reason: string
}

export interface ImportResult {
  total: number
  imported: number
  /** 跳过数 = errors + duplicates */
  skipped: number
  unchaptered: number
  paperId: number | null
  /** 校验失败、被跳过的条目 */
  errors: ImportError[]
  /** 题干与已有题目(或本批前面某条)重复、被跳过的条目 */
  duplicates: ImportError[]
}

/* ---------------- PDF 试卷识别 ---------------- */

/** POST /ai/parse-pdf 的返回:结构与 QuestionImportRequest 一致,可原样喂给导入接口 */
export interface ParsePdfResultVO {
  subjectCode: string
  paper: ImportPaperDTO | null
  items: ImportItemDTO[]
  totalPages: number
  parsedPages: number
  calls: number
  tokensIn: number
  tokensOut: number
  durationMs: number
  /** 识别过程中的提醒:缺页、题干被截断、某大题条数偏少等 */
  warnings: string[]
}

/** GET /ai/health 的返回:AI 配置自检(不含密钥本身) */
export interface AiHealthVO {
  configured: boolean
  keyPresent: boolean
  keyLength: number
  baseUrl: string
  chatPath: string
  model: string
  visionModel: string
  visionTimeoutSeconds: number
}

/* ---------------- 错题本(阶段 5)---------------- */

/** 队列状态 */
export type ReviewQueueStatus = 'LEARNING' | 'MASTERED'

/** 掌握阶段:未掌握 / 复习中 / 已掌握。判定规则在后端,前端只负责显示 */
export type MasteryStage = 'NOT_STARTED' | 'LEARNING' | 'MASTERED'

/** 错题本条目。**不含答案与解析** —— 错题本是回去重做的入口,不是答案册 */
export interface ReviewItemVO {
  questionId: number
  subjectId: number
  subjectShortName: string | null
  subjectColor: string | null
  chapterId: number | null
  chapterName: string | null
  type: string
  typeLabel: string
  stem: string
  wrongCount: number
  /** 连续答对次数;满 3 次自动移出错题本 */
  correctStreak: number
  stage: number
  intervalDays: number
  easeFactor: number
  masteryStage: MasteryStage
  masteryLabel: string
  status: ReviewQueueStatus
  lastReviewAt: string | null
  nextReviewAt: string | null
  due: boolean
}

export interface ReviewItemQuery {
  subjectId?: number
  /** 题型筛选,不传表示全部 */
  type?: QuestionType | string
  status?: ReviewQueueStatus
  /** 只看已到期的 */
  due?: boolean
  page?: number
  size?: number
}

/** 错题本题型分布(按题型复习的下拉用) */
export interface ReviewTypeCountVO {
  type: string
  typeLabel: string
  count: number
}

/** 某题型「可练习」的题数(只数已发布的),自由练习的题型下拉用 */
export interface TypeCountVO {
  type: string
  typeLabel: string
  count: number
}

/* ---------------- 设置与提醒(阶段 7)---------------- */

export type NotifyChannel = 'WECOM' | 'SERVERCHAN'

/** 自由练习默认题源:题库 / 错题 */
export type PracticeSource = 'LIBRARY' | 'WRONG'

/** 自由练习的默认条件:两个题源各存一份科目 / 题型 / 题量 */
export interface PracticeDefaultsVO {
  source: PracticeSource
  librarySubjectId: number | null
  /** 空数组 = 全部题型 */
  libraryTypes: string[]
  libraryCount: number
  wrongSubjectId: number | null
  wrongTypes: string[]
  wrongCount: number
}

/** GET /settings —— 密钥类字段只返回掩码 */
export interface SettingVO {
  dailyQuestionCount: number
  notifyChannel: NotifyChannel
  /** 掩码,如 …a1b2c3;未配置为 null */
  notifyWebhook: string | null
  notifyKey: string | null
  webhookConfigured: boolean
  keyConfigured: boolean
  reminderEnabled: boolean
  /** HH:mm */
  reminderTime: string
  eveningReminderEnabled: boolean
  /** HH:mm */
  eveningReminderTime: string
  darkMode: 'AUTO' | 'LIGHT' | 'DARK'
  practiceDefaults: PracticeDefaultsVO
}

/** PUT /settings —— 只传要改的字段;密钥传回掩码表示不改,传空串表示清空 */
export interface SettingUpsertDTO {
  dailyQuestionCount?: number
  notifyChannel?: NotifyChannel
  notifyWebhook?: string | null
  notifyKey?: string | null
  reminderEnabled?: boolean
  reminderTime?: string
  eveningReminderEnabled?: boolean
  eveningReminderTime?: string
  darkMode?: 'AUTO' | 'LIGHT' | 'DARK'
  /** 整体提交;subjectId 传 null / types 传空表示「全部」 */
  practiceDefaults?: {
    source?: PracticeSource
    librarySubjectId: number | null
    libraryTypes: string[]
    libraryCount?: number
    wrongSubjectId: number | null
    wrongTypes: string[]
    wrongCount?: number
  }
}

/** POST /notify/test —— 失败也返回 200,message 就是失败原因 */
export interface NotifyTestResultVO {
  success: boolean
  message: string
}

/** GET /notify/logs */
export interface NotifyLogVO {
  id: number
  date: string
  type: 'MORNING' | 'EVENING'
  typeLabel: string
  sentAt: string
  success: boolean
  failReason: string | null
}

/* ---------------- 导入记录 ---------------- */

export type ImportLogSource = 'PDF' | 'JSON'
/** IMPORTED 全部入库 / PARTIAL 部分入库 / DUPLICATED 全部重复 / FAILED 未入库 */
export type ImportLogStatus = 'IMPORTED' | 'PARTIAL' | 'DUPLICATED' | 'FAILED'

/** 没入库的一道题:index 为 null 表示整份被拒,没有具体条目 */
export interface ImportIssue {
  index: number | null
  stem: string | null
  reason: string
}

/** GET /import/logs */
export interface ImportLogVO {
  id: number
  source: ImportLogSource
  subjectCode: string
  paperName: string | null
  status: ImportLogStatus
  total: number
  imported: number
  skipped: number
  duplicated: number
  issues: ImportIssue[]
  warnings: string[]
  createdAt: string
}

/** POST /import/logs */
export interface ImportLogCreate {
  source: ImportLogSource
  subjectCode: string
  paperName: string | null
  total: number
  imported: number
  skipped: number
  duplicated: number
  issues: ImportIssue[]
  warnings: string[]
}
