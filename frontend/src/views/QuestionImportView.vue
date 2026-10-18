<script setup lang="ts">
/**
 * 批量导入(阶段 3)★ 录题入口,易用性优先级最高。
 *
 * 两步式:①上传/粘贴 → ②预览确认(不合格行标红,跳过继续 / 返回修改)→ ③结果卡片。
 * 右侧「导入格式说明」抽屉,内容对应 docs/study-exam/backend/03-api-spec.md 第五节。
 *
 * 这里的客户端校验只是**预览辅助**,让明显的坏行提前可见;
 * 最终判定以后端为准(规则完全一致,后端会逐条容错)。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { importQuestions } from '@/api/question'
import { parseExamPdf } from '@/api/ai'
import { createImportLog } from '@/api/importLog'
import { ApiError } from '@/api/request'
import type {
  ImportIssue,
  ImportLogCreate,
  ImportResult,
  ParsePdfResultVO,
  QuestionImportRequest
} from '@/api/types'
import { useSubjectStore } from '@/stores/subject'
import { QUESTION_TYPE_LABEL, isObjective, OPTIONLESS_TYPES } from '@/utils/constants'
import AppIcon from '@/components/AppIcon.vue'
import ImportLogDrawer from '@/components/ImportLogDrawer.vue'

type Step = 'source' | 'preview' | 'done'
/** 第 1 步的两种来源:自己准备的 JSON,或让模型从试卷 PDF 里认出来 */
type SourceMode = 'json' | 'pdf'

const router = useRouter()
const subjectStore = useSubjectStore()

const step = ref<Step>('source')
const rawText = ref('')
const fileName = ref('')
const parseError = ref('')
const formatDrawerOpen = ref(false)
/** 导入记录抽屉:离开结果页后仍能回看哪些题没进去 */
const logDrawerOpen = ref(false)

/* ---------------- 待导入单元:JSON 是 1 份,PDF 是 N 份(每个文件一份试卷)---------------- */

interface ImportUnit {
  key: string
  /** 展示用:试卷名(取不到就用文件名) */
  label: string
  request: QuestionImportRequest
  /** PDF 才有:识别统计与提醒 */
  parse?: ParsePdfResultVO
}

const units = ref<ImportUnit[]>([])
/** 导入结果与 units 一一对应;整体被后端拒绝的单位记在 error 上 */
interface ImportOutcome {
  key: string
  label: string
  result?: ImportResult
  error?: string
  /** 与条目序号对齐的题干,结果页列出失败条目时用来「这是哪道题」 */
  stems: string[]
}
const outcomes = ref<ImportOutcome[]>([])
const importing = ref(false)

/* ---------------- PDF 识别 ---------------- */
const sourceMode = ref<SourceMode>('json')

interface PdfJob {
  key: string
  file: File
  fileName: string
  subjectCode: string
  paperName: string
  year: number | undefined
  status: 'pending' | 'parsing' | 'done' | 'failed'
  elapsed: number
  error: string
  parse?: ParsePdfResultVO
}

const pdfJobs = ref<PdfJob[]>([])
const pdfGenerateAnswers = ref(true)
const pdfError = ref('')
const recognizing = ref(false)
/** 预览里展开的单元(进预览时默认全展开) */
const openedUnits = ref<string[]>([])
let elapsedTimer: number | undefined
let jobSeq = 0

function jobStatusText(job: PdfJob): string {
  switch (job.status) {
    case 'pending':
      return '待识别'
    case 'parsing':
      return `识别中 ${job.elapsed}s`
    case 'done':
      return '已识别'
    default:
      return '失败'
  }
}

/** 预览行的派生数据:题目级本地校验结果 */
interface PreviewRow {
  index: number
  sectionName: string
  typeLabel: string
  stemExcerpt: string
  answerExcerpt: string
  chapterName: string
  chapterKnown: boolean | null
  /** 会阻断导入的问题(红) */
  problems: string[]
  /** 不阻断导入的提醒(黄),如「答案待补」 */
  notes: string[]
  /** 答案还缺着:提交时要把它规范化为「待校对」,否则后端会拒 */
  needsDraft: boolean
  raw: QuestionImportRequest['items'][number]
}

const EXAMPLE = `{
  "subjectCode": "03709",
  "paper": {
    "name": "2024年10月真题",
    "type": "REAL",
    "year": 2024,
    "totalScore": 100,
    "durationMin": 150
  },
  "items": [
    {
      "sectionName": "一、单项选择题",
      "type": "SINGLE_CHOICE",
      "chapterName": "第一章 物质世界及其发展规律",
      "stem": "马克思主义哲学的基本问题是（  ）",
      "options": [
        { "key": "A", "text": "物质和意识的关系问题" },
        { "key": "B", "text": "思维和存在的关系问题" }
      ],
      "answer": "B",
      "analysis": "恩格斯指出……",
      "score": 2.0,
      "difficulty": 1
    },
    {
      "sectionName": "二、简答题",
      "type": "SHORT_ANSWER",
      "stem": "简述生产力与生产关系的辩证关系。",
      "answerPoints": ["生产力决定生产关系", "生产关系反作用于生产力"],
      "score": 6.0,
      "difficulty": 2
    }
  ]
}`

function subjectFor(code: string) {
  const c = (code ?? '').trim()
  if (!c) return null
  return subjectStore.subjects.find((s) => s.code === c) ?? null
}

/** 该科目已加载的章节名集合;科目不存在或章节还没拉到时为 null(没法判断) */
function chapterNamesFor(code: string): Set<string> | null {
  const s = subjectFor(code)
  if (!s) return null
  const chapters = subjectStore.chaptersBySubject[s.id]
  if (!chapters) return null
  return new Set(chapters.map((c) => c.name))
}

/** 一份导入单元(一个 PDF / 一份 JSON)的本地校验结果 */
function buildRows(req: QuestionImportRequest): PreviewRow[] {
  const chapterNames = chapterNamesFor(req.subjectCode)
  return req.items.map((item, i) => {
    const problems: string[] = []
    const notes: string[] = []
    const type = (item.type ?? '').trim().toUpperCase()
    if (!type) problems.push('缺少 type')
    else if (!(type in QUESTION_TYPE_LABEL)) problems.push(`未知题型 ${type}`)
    if (!item.stem || !item.stem.trim()) problems.push('题干为空')
    if (type && type in QUESTION_TYPE_LABEL) {
      if (isObjective(type)) {
        // 完形补文按词根填空,卷面上本来就没有选项,与后端 QuestionValidator 一致地放行
        const optionsRequired = !OPTIONLESS_TYPES.includes(type as never)
        if (optionsRequired && (!item.options || item.options.length === 0)) {
          problems.push('客观题 options 不能为空')
        }
        // 卷面没印答案、模型也答不出时不算错:题目照样入库,存成「待校对」,答案后补
        if (!item.answer) notes.push('答案待补,将按「待校对」入库')
        if (item.answerPoints?.length) problems.push('客观题不能带 answerPoints')
      } else {
        if (!item.answerPoints || item.answerPoints.length === 0) {
          notes.push('参考答案要点待补,将按「待校对」入库')
        }
        if (item.answer) problems.push('主观题不能带 answer')
      }
      if (item.score != null && Number(item.score) <= 0) problems.push('分值必须大于 0')
    }
    const chapterName = item.chapterName?.trim() ?? ''
    const chapterKnown =
      chapterName && chapterNames ? chapterNames.has(chapterName) : null
    return {
      index: i + 1,
      sectionName: item.sectionName ?? '—',
      typeLabel: QUESTION_TYPE_LABEL[type] ?? type ?? '—',
      stemExcerpt: (item.stem ?? '').replace(/\s+/g, ' ').slice(0, 60),
      answerExcerpt: summarizeAnswer(item),
      chapterName: chapterName || '未填',
      chapterKnown,
      problems,
      notes,
      needsDraft: needsDraft(item),
      raw: item
    }
  })
}

/** 预览里一份单元的派生数据 */
interface UnitPreview {
  key: string
  label: string
  subjectCode: string
  subjectName: string | null
  subjectExists: boolean
  paperName: string
  paperYear: number | null
  parse?: ParsePdfResultVO
  rows: PreviewRow[]
  badRows: PreviewRow[]
  draftRows: PreviewRow[]
  missingAnswerRows: PreviewRow[]
  unknownChapterRows: PreviewRow[]
}

const previews = computed<UnitPreview[]>(() =>
  units.value.map((u) => {
    const rows = buildRows(u.request)
    const subject = subjectFor(u.request.subjectCode)
    return {
      key: u.key,
      label: u.label,
      subjectCode: u.request.subjectCode,
      subjectName: subject ? subject.shortName : null,
      subjectExists: !!subject,
      paperName: u.request.paper?.name ?? u.label,
      paperYear: u.request.paper?.year ?? null,
      parse: u.parse,
      rows,
      badRows: rows.filter((r) => r.problems.length > 0),
      draftRows: rows.filter((r) => r.raw.status === 'DRAFT' || r.needsDraft),
      missingAnswerRows: rows.filter((r) => r.needsDraft),
      unknownChapterRows: rows.filter((r) => r.chapterKnown === false)
    }
  })
)

const totalBad = computed(() => previews.value.reduce((a, p) => a + p.badRows.length, 0))
const totalItems = computed(() => previews.value.reduce((a, p) => a + p.rows.length, 0))
const anyUnknownSubject = computed(() => previews.value.some((p) => !p.subjectExists))
/** 后端「失败超过一半就整份回滚」,这里提前标出来,免得用户以为整批都失败 */
const hopelessUnits = computed(() =>
  previews.value.filter((p) => p.rows.length > 0 && p.badRows.length * 2 > p.rows.length)
)

/**
 * 答案还缺着的题:客观题没 answer,或主观题没 answerPoints。
 * 这类题按「待校对」入库——后端同样只允许 DRAFT 缺答案,「已发布」仍然必须有答案。
 */
function needsDraft(item: QuestionImportRequest['items'][number]): boolean {
  const type = (item.type ?? '').trim().toUpperCase()
  if (!type || !(type in QUESTION_TYPE_LABEL)) return false
  return isObjective(type) ? !item.answer : !item.answerPoints?.length
}

function summarizeAnswer(item: QuestionImportRequest['items'][number]): string {
  if (item.answer) return item.answer
  if (item.answerPoints?.length) return `${item.answerPoints.length} 个要点`
  return '—'
}

/** 结果页汇总:多份时把所有单元的结果加起来 */
const resultTotals = computed(() =>
  outcomes.value.reduce(
    (acc, o) => {
      if (o.result) {
        acc.imported += o.result.imported
        acc.skipped += o.result.skipped
        acc.duplicates += o.result.duplicates.length
        acc.total += o.result.total
      }
      return acc
    },
    { imported: 0, skipped: 0, duplicates: 0, total: 0 }
  )
)

/** 单份的重复明细最多列 10 条:整份试卷重导时可能有几十条 */
function topDuplicates(o: ImportOutcome) {
  return o.result?.duplicates.slice(0, 10) ?? []
}

/** 把预览里出现的科目章节都拉一遍,才能判断 chapterName 能不能匹配上 */
async function ensureChapters() {
  const ids = new Set<number>()
  for (const u of units.value) {
    const s = subjectFor(u.request.subjectCode)
    if (s && !subjectStore.chaptersBySubject[s.id]) ids.add(s.id)
  }
  await Promise.all([...ids].map((id) => subjectStore.fetchChapters(id).catch(() => undefined)))
}

/** 第 1 步 → 第 2 步(JSON 来源):本地解析,明显错误不发请求 */
async function parseAndPreview() {
  parseError.value = ''
  const text = rawText.value.trim()
  if (!text) {
    parseError.value = '先选择文件或粘贴 JSON 文本'
    return
  }
  let doc: unknown
  try {
    doc = JSON.parse(text)
  } catch (e) {
    parseError.value = `文件不是合法的 JSON 格式:${(e as Error).message}`
    return
  }
  if (!doc || typeof doc !== 'object' || Array.isArray(doc)) {
    parseError.value = '顶层必须是对象:{ subjectCode, paper?, items }'
    return
  }
  const obj = doc as Record<string, unknown>
  if (!Array.isArray(obj.items) || obj.items.length === 0) {
    parseError.value = 'items 必须是非空数组(题目条目列表)'
    return
  }
  if (typeof obj.subjectCode !== 'string' || !obj.subjectCode.trim()) {
    parseError.value = '缺少 subjectCode(课程代码,如 03709)'
    return
  }
  const req = doc as QuestionImportRequest
  units.value = [{ key: 'json', label: req.paper?.name || 'JSON 导入', request: req }]
  await ensureChapters()
  openedUnits.value = units.value.map((u) => u.key)
  step.value = 'preview'
}

/** 类型化的 input change:模板里直接写 $event.target.files 过不了 vue-tsc */
function onFileChange(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) onFilePick(file)
  input.value = '' // 允许再次选中同一文件
}

function onFilePick(file: File) {
  fileName.value = file.name
  if (!/\.json$/i.test(file.name)) {
    parseError.value = '请选择 .json 文件'
    return false
  }
  const reader = new FileReader()
  reader.onload = () => {
    rawText.value = String(reader.result ?? '')
    parseError.value = ''
  }
  reader.onerror = () => {
    parseError.value = '文件读取失败'
  }
  reader.readAsText(file, 'utf-8')
  return false // 不自动上传,统一走预览
}

/* ---------------- PDF 识别 ---------------- */

function onPdfChange(ev: Event) {
  const input = ev.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length) addPdfFiles(files)
  input.value = '' // 允许再次选中同一批文件
}

/**
 * 追加文件到待识别列表。每份 PDF 自己一套「课程代码 / 试卷名 / 年份」——
 * 一批里混不同科目是常态,所以不能共用一个配置。
 * 试卷名默认取文件名,年份从文件名里猜,课程代码沿用上一份的选择(连着加同一科目的卷子会省事)。
 */
function addPdfFiles(files: File[]) {
  pdfError.value = ''
  const lastCode =
    [...pdfJobs.value].reverse().find((j) => j.subjectCode.trim())?.subjectCode ?? ''
  let rejected = 0
  let skipped = 0
  for (const file of files) {
    if (!/\.pdf$/i.test(file.name)) {
      rejected++
      continue
    }
    if (pdfJobs.value.some((j) => j.fileName === file.name && j.file.size === file.size)) {
      skipped++ // 同一个文件别加两次
      continue
    }
    const yearInName = file.name.match(/(?:19|20)\d{2}/)
    pdfJobs.value.push({
      key: `pdf-${++jobSeq}`,
      file,
      fileName: file.name,
      subjectCode: lastCode,
      paperName: file.name.replace(/\.pdf$/i, ''),
      year: yearInName ? Number(yearInName[0]) : undefined,
      status: 'pending',
      elapsed: 0,
      error: ''
    })
  }
  if (rejected) pdfError.value = `已忽略 ${rejected} 个非 .pdf 文件`
  else if (skipped) pdfError.value = `已跳过 ${skipped} 个重复选择的文件`
}

function removePdfJob(key: string) {
  if (recognizing.value) return
  pdfJobs.value = pdfJobs.value.filter((j) => j.key !== key)
}

function clearPdfJobs() {
  if (recognizing.value) return
  pdfJobs.value = []
  pdfError.value = ''
}

/** 还能识别:有待识别的文件(且都选了课程代码),或者已经全部识别完、允许「重新识别全部」 */
const canRecognize = computed(() => {
  if (recognizing.value || pdfJobs.value.length === 0) return false
  const needWork = pdfJobs.value.filter((j) => j.status !== 'done')
  if (needWork.length === 0) return true
  return needWork.every((j) => j.subjectCode.trim().length > 0)
})

const pendingCount = computed(() => pdfJobs.value.filter((j) => j.status !== 'done').length)

/** 识别一份试卷要一两分钟,给个秒表,免得用户以为卡死了 */
function startElapsedTimer() {
  if (elapsedTimer !== undefined) return
  elapsedTimer = window.setInterval(() => {
    const job = pdfJobs.value.find((j) => j.status === 'parsing')
    if (job) job.elapsed += 1
  }, 1000)
}

function stopElapsedTimer() {
  if (elapsedTimer !== undefined) {
    window.clearInterval(elapsedTimer)
    elapsedTimer = undefined
  }
}

async function recognizeAll() {
  if (recognizing.value) return
  let queue = pdfJobs.value.filter((j) => j.status === 'pending' || j.status === 'failed')
  if (!queue.length) {
    // 全部识别过:当作「重新识别全部」。
    // 这一步会清掉已有结果并再调一遍模型(按页烧 token),必须让用户明确点一次;
    // 只想看刚才的结果,来源页上有「回到预览」。
    try {
      await ElMessageBox.confirm(
        '会清掉已经识别好的结果,并重新调用模型识别一遍(按页计费)。只想核对已有结果的话,点「回到预览」。',
        '重新识别全部?',
        { confirmButtonText: '重新识别', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
    rearmPdfJobs()
    queue = [...pdfJobs.value]
  }
  if (!queue.length) return
  pdfError.value = ''
  recognizing.value = true
  startElapsedTimer()
  // 串行识别:视觉调用本来就慢,并发只会互相拖慢、还容易撞上游限流;
  // 串行也方便一份失败时把原因记在那一条上,不影响其余的
  try {
    for (const job of queue) {
      job.status = 'parsing'
      job.elapsed = 0
      job.error = ''
      try {
        job.parse = await parseExamPdf(job.file, {
          subjectCode: job.subjectCode,
          paperName: job.paperName,
          year: job.year ?? null,
          generateAnswers: pdfGenerateAnswers.value
        })
        job.status = 'done'
      } catch (e) {
        job.status = 'failed'
        job.error = e instanceof ApiError ? e.message : `识别失败:${(e as Error).message}`
      }
    }
  } finally {
    stopElapsedTimer()
    recognizing.value = false
  }

  const done = pdfJobs.value.filter((j) => j.status === 'done' && j.parse)
  // 整份没认出来的也留档:这时题目一条都没出现,不记下来就彻底忘了
  await saveRecognitionFailures()
  if (!done.length) {
    pdfError.value = '没有一份识别成功,看看上面每一条的失败原因'
    return
  }
  units.value = done.map((j) => ({
    key: j.key,
    label: j.paperName || j.fileName,
    request: {
      subjectCode: j.parse!.subjectCode,
      paper: j.parse!.paper,
      items: j.parse!.items
    },
    parse: j.parse!
  }))
  await ensureChapters()
  openedUnits.value = units.value.map((u) => u.key)
  step.value = 'preview'
  const warnings = done.reduce((a, j) => a + (j.parse?.warnings.length ?? 0), 0)
  if (warnings) ElMessage.warning(`识别完成,但有 ${warnings} 条提醒要看一下`)
}

/** 全部识别过了还想再来一遍:把每条复位成待识别 */
function rearmPdfJobs() {
  for (const j of pdfJobs.value) {
    j.status = 'pending'
    j.elapsed = 0
    j.error = ''
    j.parse = undefined
  }
}

/* ---------------- 导入记录:把「没传上去的题」留档 ---------------- */

/** 一次导入里没入库的条目。重复的不算——题库里本来就有,不需要人工补录。 */
function buildIssues(unit: ImportUnit, result: ImportResult): ImportIssue[] {
  const stems = unit.request.items.map((i) => i.stem ?? '')
  return result.errors.map((e) => ({
    index: e.index,
    stem: stems[e.index - 1] || null,
    reason: e.reason
  }))
}

/**
 * 存一条导入记录。失败不打断导入:
 * 记录是事后补录用的,不该因为记录接口出错就让已经完成的导入看起来像失败了。
 * (request.ts 自己会弹错误提示,这里不再重复弹。)
 */
async function saveImportLog(body: ImportLogCreate) {
  try {
    await createImportLog(body)
  } catch {
    /* 忽略 */
  }
}

async function saveOutcomeLog(unit: ImportUnit, outcome: ImportOutcome) {
  const result = outcome.result
  await saveImportLog({
    source: unit.parse ? 'PDF' : 'JSON',
    subjectCode: unit.request.subjectCode,
    paperName: unit.label,
    total: result?.total ?? unit.request.items.length,
    imported: result?.imported ?? 0,
    skipped: result?.skipped ?? unit.request.items.length,
    duplicated: result?.duplicates.length ?? 0,
    issues: result
      ? buildIssues(unit, result)
      : [{ index: null, stem: null, reason: outcome.error || '整份未入库' }],
    warnings: unit.parse?.warnings ?? []
  })
}

/** 整份识别失败也要留档:这时题目一条都没出现,只看导入结果是发现不了的 */
async function saveRecognitionFailures() {
  for (const j of pdfJobs.value.filter((job) => job.status === 'failed')) {
    await saveImportLog({
      source: 'PDF',
      subjectCode: j.subjectCode,
      paperName: j.paperName || j.fileName,
      total: 0,
      imported: 0,
      skipped: 0,
      duplicated: 0,
      issues: [{ index: null, stem: null, reason: j.error || '识别失败' }],
      warnings: []
    })
  }
}

/** 本地就判定「会被整份拒绝」的那几份,同样留档 —— 它们一条都没进库,最需要补录 */
async function saveRejectedLogs(list: UnitPreview[]) {
  for (const p of list) {
    await saveImportLog({
      source: p.parse ? 'PDF' : 'JSON',
      subjectCode: p.subjectCode,
      paperName: p.paperName,
      total: p.rows.length,
      imported: 0,
      skipped: p.rows.length,
      duplicated: 0,
      issues: p.badRows.map((r) => ({
        index: r.index,
        stem: r.raw.stem ?? null,
        reason: r.problems.join(';')
      })),
      warnings: p.parse?.warnings ?? []
    })
  }
}

async function doImportAll() {
  if (!units.value.length || importing.value) return

  if (anyUnknownSubject.value) {
    const missing = [...new Set(previews.value.filter((p) => !p.subjectExists).map((p) => p.subjectCode))]
    try {
      await ElMessageBox.confirm(
        `课程代码 ${missing.join('、')} 在系统里不存在,对应的那几份会被整体拒绝。先去科目管理建课?`,
        '科目不存在',
        { confirmButtonText: '去建科目', cancelButtonText: '留在本页', type: 'warning' }
      )
      router.push('/subjects')
    } catch {
      /* 留在本页 */
    }
    return
  }

  // 后端「失败超过一半就整份回滚」,提前拦下,免得用户以为整批都失败
  if (hopelessUnits.value.length) {
    ElMessage.error(
      `${hopelessUnits.value.map((p) => p.label).join('、')} 的不合格条目已超过一半,会被整份拒绝——请先返回修改`
    )
    // 整份没提交也是一次「没传上去」,记下来才有得补
    await saveRejectedLogs(hopelessUnits.value)
    return
  }

  if (totalBad.value > 0) {
    try {
      await ElMessageBox.confirm(
        `共 ${units.value.length} 份、${totalItems.value} 条题目,其中 ${totalBad.value} 条不合格将被跳过。确认继续?`,
        '跳过不合格条目',
        { confirmButtonText: '跳过并导入', cancelButtonText: '返回修改', type: 'warning' }
      )
    } catch {
      return
    }
  }

  importing.value = true
  outcomes.value = []
  try {
    // 逐个单元导入:每份 PDF 建自己那份试卷;一份失败不影响其余的
    for (const unit of units.value) {
      // 缺答案的题一律按「待校对」提交:后端只允许 DRAFT 缺答案,不规范化的话整条会被拒
      for (const item of unit.request.items) {
        if (needsDraft(item)) item.status = 'DRAFT'
      }
      const stems = unit.request.items.map((i) => i.stem ?? '')
      let outcome: ImportOutcome
      try {
        const r = await importQuestions(unit.request)
        outcome = { key: unit.key, label: unit.label, result: r, stems }
      } catch (e) {
        outcome = {
          key: unit.key,
          label: unit.label,
          error: e instanceof ApiError ? e.message : String((e as Error)?.message ?? e),
          stems
        }
      }
      outcomes.value.push(outcome)
      // 每份导完就落一条记录:中途关掉页面也不会丢已经导完那几份的失败明细
      await saveOutcomeLog(unit, outcome)
    }
    step.value = 'done'
    await subjectStore.fetchSubjects(true)
  } finally {
    importing.value = false
  }
}

function backToSource() {
  step.value = 'source'
  outcomes.value = []
}

/**
 * 「返回修改」后再回到预览。
 *
 * units 在 backToSource 里并没有清掉——识别结果一直留在内存里,只是来源页原先没有回看的入口,
 * 逼得用户只能点「重新识别全部」再烧一次 token。这里直接切回去,不碰任何数据。
 */
function backToPreview() {
  if (!units.value.length) return
  step.value = 'preview'
  openedUnits.value = units.value.map((u) => u.key)
}

function resetAll() {
  step.value = 'source'
  units.value = []
  outcomes.value = []
  rawText.value = ''
  fileName.value = ''
  parseError.value = ''
  pdfJobs.value = []
  pdfError.value = ''
  stopElapsedTimer()
}

async function copyExample() {
  try {
    await navigator.clipboard.writeText(EXAMPLE)
    ElMessage.success('示例已复制')
  } catch {
    ElMessage.warning('复制失败,请手动选中复制')
  }
}

onMounted(() => void subjectStore.fetchSubjects())
onUnmounted(stopElapsedTimer)
</script>

<template>
  <section class="import-page">
    <header class="import-page__head">
      <h1 class="import-page__title">批量导入题库</h1>
      <div class="import-page__steps">
        <span :class="['step-dot', { active: step === 'source', done: step !== 'source' }]">1 来源</span>
        <span class="step-arrow">→</span>
        <span :class="['step-dot', { active: step === 'preview', done: step === 'done' }]">2 预览</span>
        <span class="step-arrow">→</span>
        <span :class="['step-dot', { active: step === 'done' }]">3 结果</span>
      </div>
      <el-button text @click="logDrawerOpen = true">
        <AppIcon name="list" :size="14" /> 导入记录
      </el-button>
      <el-button text @click="formatDrawerOpen = true">
        <AppIcon name="book" :size="14" /> 导入格式说明
      </el-button>
    </header>

    <!-- ① 来源 -->
    <div v-if="step === 'source'" class="card">
      <el-radio-group v-model="sourceMode" class="source-mode">
        <el-radio-button value="json">JSON 文件 / 粘贴</el-radio-button>
        <el-radio-button value="pdf">上传试卷 PDF 识别</el-radio-button>
      </el-radio-group>

      <div v-if="sourceMode === 'json'" class="source-tabs">
        <label class="source-drop">
          <input type="file" accept=".json,application/json" @change="onFileChange" />
          <span class="source-drop__text">
            选择 <strong>.json</strong> 文件{{ fileName ? ` · ${fileName}` : '' }}
          </span>
        </label>
        <span class="source-divider">或直接粘贴 JSON 文本</span>
        <el-input
          v-model="rawText"
          type="textarea"
          :rows="14"
          resize="vertical"
          placeholder='{ "subjectCode": "03709", "items": [ … ] }'
          class="mono source-area"
        />
        <p v-if="parseError" class="source-error">{{ parseError }}</p>
        <div class="source-actions">
          <el-button type="primary" :disabled="!rawText.trim()" @click="parseAndPreview">
            解析预览
          </el-button>
        </div>
      </div>

      <div v-else class="source-tabs">
        <label class="source-drop" :class="{ 'is-busy': recognizing }">
          <input
            type="file"
            accept=".pdf,application/pdf"
            multiple
            :disabled="recognizing"
            @change="onPdfChange"
          />
          <span class="source-drop__text">
            选择试卷 <strong>.pdf</strong> 文件(可一次选多份,扫描件也可以)
          </span>
        </label>

        <!-- 待识别清单:每份 PDF 一份试卷,各自选课程代码 -->
        <div v-if="pdfJobs.length" class="pdf-jobs">
          <div class="pdf-jobs__head">
            <span>共 {{ pdfJobs.length }} 份</span>
            <el-button v-if="!recognizing" text size="small" @click="clearPdfJobs">清空</el-button>
          </div>
          <ul class="pdf-jobs__list">
            <li v-for="job in pdfJobs" :key="job.key" class="pdf-job">
              <div class="pdf-job__top">
                <span class="pdf-job__name wrap-anywhere">{{ job.fileName }}</span>
                <span class="pdf-job__status" :class="`pdf-job__status--${job.status}`">
                  {{ jobStatusText(job) }}
                </span>
                <button
                  v-if="!recognizing"
                  type="button"
                  class="pdf-job__remove"
                  aria-label="移除这份"
                  @click="removePdfJob(job.key)"
                >
                  <AppIcon name="close" :size="14" />
                </button>
              </div>

              <div class="pdf-job__form">
                <el-select
                  v-model="job.subjectCode"
                  filterable
                  allow-create
                  placeholder="课程代码"
                  :disabled="recognizing"
                  size="small"
                >
                  <el-option
                    v-for="s in subjectStore.subjects"
                    :key="s.id"
                    :value="s.code"
                    :label="`${s.code} · ${s.name}`"
                  />
                </el-select>
                <el-input
                  v-model="job.paperName"
                  placeholder="试卷名"
                  :disabled="recognizing"
                  size="small"
                />
                <el-input-number
                  v-model="job.year"
                  :min="1990"
                  :max="2100"
                  :controls="false"
                  placeholder="年份"
                  :disabled="recognizing"
                  size="small"
                />
              </div>

              <p v-if="job.status === 'failed'" class="pdf-job__error">{{ job.error }}</p>
              <p v-else-if="job.status === 'done'" class="pdf-job__ok">
                识别出 {{ job.parse?.items.length ?? 0 }} 题<template v-if="job.parse?.warnings.length">,{{ job.parse!.warnings.length }} 条提醒</template>
              </p>
            </li>
          </ul>
        </div>

        <el-checkbox v-model="pdfGenerateAnswers">
          卷面没有答案时,让模型代答(代答的题会标成「待校对」)
        </el-checkbox>

        <p class="pdf-hint">
          逐份识别,一份 7 页的试卷约 2—3 分钟,多份依次排队,期间别关页面。
          全部识别完再一起预览,核对后一次导入(每个 PDF 建自己那份试卷)。
        </p>
        <p v-if="pdfError" class="source-error">{{ pdfError }}</p>

        <div class="source-actions">
          <span v-if="recognizing" class="pdf-progress">
            已完成 {{ pdfJobs.length - pendingCount }} / {{ pdfJobs.length }} 份
          </span>
          <el-button v-if="units.length && !recognizing" @click="backToPreview">
            回到预览
          </el-button>
          <el-button
            type="primary"
            :disabled="!canRecognize"
            :loading="recognizing"
            @click="recognizeAll"
          >
            {{
              recognizing
                ? '识别中'
                : pendingCount > 0
                  ? `开始识别 ${pendingCount} 份`
                  : '重新识别全部'
            }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- ② 预览:一份 PDF / 一份 JSON 各占一个可折叠面板 -->
    <template v-else-if="step === 'preview' && previews.length">
      <div class="preview-meta card">
        <span>共 <strong>{{ previews.length }}</strong> 份 · {{ totalItems }} 条题目</span>
        <span :class="totalBad ? 'meta-warn' : 'meta-ok'">
          {{ totalBad ? `不合格 ${totalBad} 条` : '全部通过本地校验' }}
        </span>
        <span v-if="anyUnknownSubject" class="meta-warn">
          有课程代码在系统里不存在,对应的那几份会被拒绝
        </span>
        <span v-if="hopelessUnits.length" class="meta-warn">
          {{ hopelessUnits.length }} 份不合格过半,会被整份拒绝
        </span>
      </div>

      <el-collapse v-model="openedUnits" class="preview-units">
        <el-collapse-item v-for="p in previews" :key="p.key" :name="p.key">
          <template #title>
            <div class="unit-title">
              <span class="unit-title__name">{{ p.paperName }}</span>
              <span class="unit-title__meta">
                <span class="mono">{{ p.subjectCode }}</span>
                <template v-if="p.subjectName">({{ p.subjectName }})</template>
                · {{ p.rows.length }} 条
                <span v-if="p.badRows.length" class="meta-warn">· 不合格 {{ p.badRows.length }}</span>
                <span v-else class="meta-ok">· 通过校验</span>
                <template v-if="p.draftRows.length">· 待校对 {{ p.draftRows.length }}</template>
                <template v-if="p.unknownChapterRows.length">· 章节未匹配 {{ p.unknownChapterRows.length }}</template>
              </span>
            </div>
          </template>

          <div v-if="p.parse" class="pdf-summary">
            <div class="pdf-summary__head">
              <AppIcon name="check" :size="16" />
              {{ p.parse.parsedPages }}/{{ p.parse.totalPages }} 页 ·
              调用模型 {{ p.parse.calls }} 次 ·
              {{ (p.parse.durationMs / 1000).toFixed(0) }} 秒 ·
              消耗 {{ p.parse.tokensIn }} + {{ p.parse.tokensOut }} tokens
            </div>
            <ul v-if="p.parse.warnings.length" class="pdf-warnings">
              <li v-for="(w, i) in p.parse.warnings" :key="i">{{ w }}</li>
            </ul>
          </div>

          <p v-if="p.missingAnswerRows.length" class="unit-note">
            其中 {{ p.missingAnswerRows.length }} 条答案待补,将按「待校对」入库——补完答案改成「已发布」才能进日常练习。
          </p>

          <div class="preview-table">
            <el-table
              :data="p.rows"
              style="width: 100%"
              :row-class-name="({ row }: any) => row.problems.length ? 'row-bad' : ''"
            >
              <el-table-column prop="index" label="#" width="56" />
              <el-table-column label="大题" width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.sectionName }}</template>
              </el-table-column>
              <el-table-column label="题型" width="120">
                <template #default="{ row }">{{ row.typeLabel }}</template>
              </el-table-column>
              <el-table-column label="题干摘要" min-width="240">
                <template #default="{ row }">
                  <el-tooltip :content="row.raw.stem" placement="top" :show-after="400" popper-class="stem-tooltip">
                    <div class="stem-clamp">{{ row.stemExcerpt }}</div>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="答案" width="110">
                <template #default="{ row }">
                  <span class="mono">{{ row.answerExcerpt }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="88">
                <template #default="{ row }">
                  <span v-if="row.raw.status === 'DRAFT'" class="draft-tag">待校对</span>
                  <span v-else class="status-dim">—</span>
                </template>
              </el-table-column>
              <el-table-column label="章节" width="120">
                <template #default="{ row }">
                  <span :class="row.chapterKnown === false ? 'meta-warn' : ''">{{ row.chapterName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="校验" min-width="200">
                <template #default="{ row }">
                  <span v-if="!row.problems.length && !row.notes.length" class="meta-ok">通过</span>
                  <ul v-else class="problem-list">
                    <li v-for="pr in row.problems" :key="pr">{{ pr }}</li>
                    <!-- 不阻断导入的提醒:答案缺着不算错,照样入库 -->
                    <li v-for="n in row.notes" :key="n" class="note-item">{{ n }}</li>
                  </ul>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-collapse-item>
      </el-collapse>

      <footer class="preview-actions">
        <el-button @click="backToSource">返回修改</el-button>
        <el-button type="primary" :loading="importing" @click="doImportAll">
          导入全部 {{ previews.length }} 份(共 {{ totalItems }} 题)
        </el-button>
      </footer>
    </template>

    <!-- ③ 结果:总账 + 每份一行小结 -->
    <template v-else-if="step === 'done' && outcomes.length">
      <div class="card result-card">
        <h2 :class="resultTotals.skipped === 0 ? 'meta-ok result-title' : 'result-title'">
          <AppIcon name="check" :size="20" />
          成功导入 {{ resultTotals.imported }} 题<template v-if="resultTotals.skipped">,跳过 {{ resultTotals.skipped }} 题</template>
        </h2>
        <p class="result-sub">
          {{ outcomes.length }} 份 · 共 {{ resultTotals.total }} 条
          <template v-if="resultTotals.duplicates">· 其中 {{ resultTotals.duplicates }} 条与已有题目重复</template>
        </p>
      </div>

      <div v-for="o in outcomes" :key="o.key" class="card unit-result">
        <div class="unit-result__head">
          <span class="unit-result__name wrap-anywhere">{{ o.label }}</span>
          <span v-if="o.error" class="meta-warn">整份未入库</span>
        </div>

        <p v-if="o.error" class="meta-warn">{{ o.error }}</p>

        <template v-else-if="o.result">
          <p class="result-sub">
            导入 {{ o.result.imported }} 题
            <template v-if="o.result.skipped">· 跳过 {{ o.result.skipped }}</template>
            <template v-if="o.result.duplicates.length">· 其中重复 {{ o.result.duplicates.length }}</template>
            <template v-if="o.result.paperId">· 已建试卷(编号 {{ o.result.paperId }})</template>
            <template v-if="o.result.unchaptered">· {{ o.result.unchaptered }} 题章节未匹配</template>
          </p>

          <div v-if="o.result.errors.length" class="result-errors">
            <h3>不合格,已跳过</h3>
            <ul>
              <li v-for="err in o.result.errors" :key="err.index">
                <span>第 <strong class="mono">{{ err.index }}</strong> 条:{{ err.reason }}</span>
                <span v-if="o.stems[err.index - 1]" class="err-stem wrap-anywhere">{{ o.stems[err.index - 1] }}</span>
              </li>
            </ul>
          </div>

          <div v-if="o.result.duplicates.length" class="result-errors result-errors--dup">
            <h3>重复,已跳过(题库里已有同一道题)</h3>
            <ul>
              <li v-for="dup in topDuplicates(o)" :key="dup.index">
                <span>第 <strong class="mono">{{ dup.index }}</strong> 条:{{ dup.reason }}</span>
                <span v-if="o.stems[dup.index - 1]" class="err-stem wrap-anywhere">{{ o.stems[dup.index - 1] }}</span>
              </li>
            </ul>
            <p v-if="o.result.duplicates.length > topDuplicates(o).length" class="result-more">
              还有 {{ o.result.duplicates.length - topDuplicates(o).length }} 条未列出
            </p>
          </div>
        </template>
      </div>

      <div class="result-actions">
        <el-button type="primary" @click="router.push('/questions')">查看题库</el-button>
        <el-button @click="logDrawerOpen = true">查看导入记录</el-button>
        <el-button @click="resetAll">再导入一份</el-button>
      </div>
    </template>

    <div v-else class="card">
      <p>异常状态,请<el-button link type="primary" @click="resetAll">重新开始</el-button>。</p>
    </div>

    <!-- 导入记录:离开结果页之后,失败明细还在这里 -->
    <ImportLogDrawer v-model="logDrawerOpen" />

    <!-- 格式说明抽屉 -->
    <el-drawer v-model="formatDrawerOpen" title="导入格式说明" size="min(92vw, 560px)">
      <div class="format-doc">
        <p>
          顶层字段:<code class="mono">subjectCode</code>(课程代码,必填)、
          <code class="mono">paper</code>(试卷信息,选填)、<code class="mono">items</code>(题目数组,必填)。
          一份文件只属于一个科目;跨科目请拆多个文件。
        </p>
        <ul>
          <li><strong>章节写名字不写 ID</strong>:按 <code class="mono">chapterName</code> 在该科目下精确匹配,找不到则留空并在结果里提示。</li>
          <li><strong>客观题</strong>必须有 <code class="mono">options</code> 和 <code class="mono">answer</code>;多选题答案写连写字母(如 ABD)。
            <strong>完形补文</strong>例外:它按括号里的词根填空,没有选项,<code class="mono">options</code> 留空数组。</li>
          <li><strong>主观题</strong>必须有 <code class="mono">answerPoints</code>(要点数组),不得有 answer。</li>
          <li><strong>分值写在题目上</strong>,缺省 2.0;同题型不同年份分值可以不同。</li>
          <li>逐条容错:坏条目跳过并给出原因;失败超过一半整份拒绝。</li>
        </ul>
        <h4>完整示例</h4>
        <pre class="format-example mono">{{ EXAMPLE }}</pre>
        <el-button @click="copyExample">
          <AppIcon name="list" :size="14" /> 复制示例
        </el-button>
        <el-button @click="rawText = EXAMPLE; formatDrawerOpen = false; step = 'source'">
          填入示例并预览
        </el-button>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}
.import-page__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-md);
}
.import-page__title {
  font-size: 20px;
  font-weight: 600;
  margin-inline-end: auto;
}
.import-page__steps {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--text-secondary);
  font-size: 13px;
}
.step-dot {
  padding: 2px 10px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border-color);
}
.step-dot.active {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.step-dot.done {
  color: var(--color-success);
  border-color: var(--color-success);
}
.step-arrow { color: var(--text-disabled); }

/* ① 来源 */
.source-tabs {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
}
.source-drop {
  display: block;
  border: 2px dashed var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  text-align: center;
  cursor: pointer;
  min-height: 44px;
}
.source-drop:hover { border-color: var(--color-primary); }
.source-drop input { display: none; }
.source-drop__text { color: var(--text-secondary); }
.source-divider {
  text-align: center;
  color: var(--text-disabled);
  font-size: 13px;
}
.source-area :deep(textarea) {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
}
.source-error {
  color: var(--color-danger);
  font-size: 14px;
  margin: 0;
}
.source-actions { display: flex; justify-content: flex-end; }

/* ① 来源 —— 两种来源的切换 + PDF 待识别清单 */
.source-mode { margin: var(--space-md) var(--space-md) 0; }
.pdf-jobs {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--space-sm);
}
.pdf-jobs__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-xs);
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}
.pdf-jobs__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.pdf-job {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding: var(--space-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}
.pdf-job__top {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.pdf-job__name {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-size-body);
  color: var(--text-primary);
}
.pdf-job__status {
  flex-shrink: 0;
  padding: 0 var(--space-sm);
  border-radius: var(--radius-full);
  background-color: var(--bg-page);
  color: var(--text-secondary);
  font-size: var(--font-size-aux);
}
.pdf-job__status--parsing {
  background-color: var(--color-primary-light);
  color: var(--color-primary);
}
.pdf-job__status--done {
  background-color: var(--color-success-light);
  color: var(--color-success);
}
.pdf-job__status--failed {
  background-color: var(--color-danger-light);
  color: var(--color-danger);
}
.pdf-job__remove {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--tap-target-min);
  min-height: var(--tap-target-min);
  border: none;
  background-color: transparent;
  color: var(--text-secondary);
}
.pdf-job__form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--space-xs);
}
.pdf-job__error {
  margin: 0;
  font-size: var(--font-size-aux);
  color: var(--color-danger);
}
.pdf-job__ok {
  margin: 0;
  font-size: var(--font-size-aux);
  color: var(--color-success);
}
.pdf-hint { margin: 0; font-size: 13px; color: var(--text-secondary); line-height: 1.6; }
.pdf-progress { margin-inline-end: auto; font-size: 13px; color: var(--text-secondary); }
.source-drop.is-busy { opacity: 0.6; cursor: not-allowed; }

/* ② 预览 */
/* 每份(每个 PDF / 一份 JSON)一个折叠面板:批量时表格摞在一起会太长 */
.preview-units { border: none; }
.preview-units :deep(.el-collapse-item) {
  margin-bottom: var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  overflow: hidden;
}
.preview-units :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 48px;
  padding: 0 var(--card-padding);
  background-color: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
}
.preview-units :deep(.el-collapse-item__wrap) { background-color: var(--bg-card); border-bottom: none; }
.preview-units :deep(.el-collapse-item__content) { padding: var(--space-md); }
.unit-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: var(--space-xs) 0;
  text-align: start;
}
.unit-title__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}
.unit-title__meta { font-size: var(--font-size-aux); color: var(--text-secondary); }
.unit-note {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-aux);
  color: var(--color-warning);
}
.pdf-summary {
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-md);
  font-size: 14px;
}
.pdf-summary__head {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-success);
}
.pdf-warnings {
  margin: var(--space-xs) 0 0;
  padding-left: 1.2em;
  color: var(--color-danger);
  font-size: 13px;
  line-height: 1.7;
}
.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-md);
  font-size: 14px;
  color: var(--text-secondary);
}
.meta-warn { color: var(--color-danger); font-style: normal; }
.meta-ok { color: var(--color-success); }
.draft-tag {
  display: inline-block;
  padding: 0 6px;
  border: 1px solid var(--color-warning);
  border-radius: var(--radius-full);
  background: var(--color-warning-light);
  color: var(--color-warning);
  font-size: 12px;
  line-height: 18px;
}
.status-dim { color: var(--text-disabled); }
.preview-table { padding: var(--space-sm); }
.preview-table :deep(.row-bad) { background: var(--color-danger-light); }
.stem-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.problem-list {
  margin: 0;
  padding-left: 1.1em;
  color: var(--color-danger);
  font-size: 13px;
}
/* 不阻断导入的提醒:黄色,与红色的问题区分开 */
.problem-list .note-item { color: var(--color-warning); }
.preview-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}

/* ③ 结果 */
.unit-result { margin-bottom: var(--space-md); }
.unit-result__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}
.unit-result__name {
  font-size: var(--font-size-card-title);
  font-weight: 600;
  color: var(--text-primary);
}
.result-card {
  padding: var(--space-lg);
  text-align: center;
}
.result-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 var(--space-sm);
}
.result-sub { color: var(--text-secondary); margin: 0 0 var(--space-md); }
.result-errors {
  text-align: start;
  background: var(--bg-page);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  margin-bottom: var(--space-md);
  font-size: 14px;
}
.result-errors h3 { margin: 0 0 var(--space-sm); font-size: 15px; }
/* 重复不是「数据坏了」,换一种颜色,别和红字的不合格混在一起 */
.result-errors--dup h3 { color: var(--color-warning); }
.result-more {
  margin: var(--space-xs) 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}
/* 失败条目的题干:光有「第 12 条」没法补录,得知道是哪道题 */
.err-stem {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  color: var(--text-secondary);
}
.result-actions { display: flex; justify-content: center; gap: var(--space-sm); }

/* 抽屉 */
.format-doc { color: var(--text-secondary); font-size: 14px; line-height: 1.7; }
.format-doc ul { padding-left: 1.2em; }
.format-example {
  background: var(--bg-page);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  font-size: 12px;
  line-height: 1.6;
  overflow: auto;
  max-height: 50vh;
  color: var(--text-primary);
}
</style>
