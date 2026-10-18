/*
 * api/question.ts —— 题库接口(阶段 2 CRUD + 阶段 3 批量导入)
 * 约定见 docs/study-exam/frontend/03-state-and-api.md 第三节:
 * 一个后端接口对应一个导出函数,不在函数里塞业务逻辑。
 */
import { http } from './request'
import instance from './request'
import type {
  ImportResult,
  PageResult,
  QuestionDetailVO,
  QuestionImportRequest,
  QuestionQuery,
  QuestionUpsertDTO,
  TypeCountVO
} from './types'

const BASE = '/v1/questions'

/** GET /questions —— 题目分页列表,全部筛选参数可选 */
export function getQuestions(query: QuestionQuery = {}): Promise<PageResult<QuestionDetailVO>> {
  const params: Record<string, unknown> = { page: 1, size: 20, ...query }
  // 清掉 undefined / 空串,避免把空筛选条件拼进 URL
  for (const key of Object.keys(params)) {
    if (params[key] === undefined || params[key] === '') delete params[key]
  }
  return http.get<PageResult<QuestionDetailVO>>(BASE, { params })
}

/** GET /questions/{id} —— 题目详情(含答案,仅题库管理使用) */
export function getQuestion(id: number): Promise<QuestionDetailVO> {
  return http.get<QuestionDetailVO>(`${BASE}/${id}`)
}

/** POST /questions —— 新建题目 */
export function createQuestion(body: QuestionUpsertDTO): Promise<QuestionDetailVO> {
  return http.post<QuestionDetailVO>(BASE, body)
}

/** PUT /questions/{id} —— 修改题目 */
export function updateQuestion(id: number, body: QuestionUpsertDTO): Promise<QuestionDetailVO> {
  return http.put<QuestionDetailVO>(`${BASE}/${id}`, body)
}

/** DELETE /questions/{id} —— 删除题目 */
export function deleteQuestion(id: number): Promise<void> {
  return http.delete<void>(`${BASE}/${id}`)
}

/** POST /questions/import —— 批量导入(JSON body) */
export function importQuestions(body: QuestionImportRequest): Promise<ImportResult> {
  return http.post<ImportResult>(`${BASE}/import`, body)
}

/**
 * POST /questions/import —— 批量导入(multipart 上传 .json 文件)
 * 不手动设 Content-Type:浏览器会自动带 boundary。
 */
export function importQuestionsFile(file: File): Promise<ImportResult> {
  const form = new FormData()
  form.append('file', file)
  return instance.post(`${BASE}/import`, form) as unknown as Promise<ImportResult>
}

/** GET /questions/type-counts —— 各题型的可练习题数(只数已发布的,给自由练习选题型用) */
export function getTypeCounts(subjectId?: number): Promise<TypeCountVO[]> {
  const params: Record<string, unknown> = {}
  if (subjectId != null) params.subjectId = subjectId
  return http.get<TypeCountVO[]>(`${BASE}/type-counts`, { params })
}
