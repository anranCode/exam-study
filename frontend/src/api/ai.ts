/*
 * api/ai.ts —— AI 与 PDF 试卷识别(阶段 3.5 识别入口 / 阶段 9 AI 老师)
 * 约定见 docs/study-exam/frontend/03-state-and-api.md 第三节:
 * 一个后端接口对应一个导出函数,不在函数里塞业务逻辑。
 */
import { http } from './request'
import type { AiHealthVO, ParsePdfResultVO } from './types'

const BASE = '/v1/ai'

/** 识别一份 PDF 要逐页调视觉模型,几分钟很正常,不能走 15 秒的默认超时 */
const PARSE_TIMEOUT = 20 * 60 * 1000

export interface ParsePdfOptions {
  subjectCode?: string
  paperName?: string
  year?: number | null
  /** 卷面没有答案时是否让模型代答(代答的题会带 DRAFT) */
  generateAnswers?: boolean
}

/**
 * POST /ai/parse-pdf —— 上传试卷 PDF,逐页识别成题目 JSON。
 * 结果**不写库**,结构与 QuestionImportRequest 一致,可直接进预览再导入。
 */
export function parseExamPdf(file: File, options: ParsePdfOptions = {}): Promise<ParsePdfResultVO> {
  const form = new FormData()
  form.append('file', file)

  const params: Record<string, unknown> = {
    // 课程代码必填:后端会先校验科目存在,识别结果最终要导进这门课
    subjectCode: options.subjectCode?.trim() ?? '',
    generateAnswers: options.generateAnswers ?? true
  }
  if (options.paperName?.trim()) params.paperName = options.paperName.trim()
  if (options.year != null) params.year = options.year

  return http.post<ParsePdfResultVO>(`${BASE}/parse-pdf`, form, {
    params,
    timeout: PARSE_TIMEOUT,
    // 必须显式声明 multipart:实例默认头是 application/json,
    // axios 见到它会把 FormData 序列化成 JSON 字符串,文件就丢了。
    // 真正的 boundary 由浏览器补上。
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** GET /ai/health —— 配置自检:模型名、是否配好密钥(不返回密钥本身) */
export function getAiHealth(): Promise<AiHealthVO> {
  return http.get<AiHealthVO>(`${BASE}/health`)
}

/** GET /ai/diagnose —— 连通性自检,用来区分「密钥无效 / 入口不对 / 模型名不存在」 */
export function diagnoseAi(): Promise<Record<string, unknown>> {
  return http.get<Record<string, unknown>>(`${BASE}/diagnose`)
}
