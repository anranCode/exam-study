import { http } from './request'
import type {
  DueSummaryVO,
  PageResult,
  ReviewItemQuery,
  ReviewItemVO,
  ReviewTypeCountVO
} from './types'

const BASE = '/v1'

/** GET /review/due-summary —— 今日到期复习汇总 */
export function getDueSummary(): Promise<DueSummaryVO> {
  return http.get<DueSummaryVO>(`${BASE}/review/due-summary`)
}

/** GET /review/items —— 错题本列表,按下次复习时间升序(最该复习的排前面) */
export function getReviewItems(query: ReviewItemQuery = {}): Promise<PageResult<ReviewItemVO>> {
  const params: Record<string, unknown> = { ...query }
  // 清掉 undefined / 空串,避免把空筛选条件拼进 URL
  for (const key of Object.keys(params)) {
    if (params[key] === undefined || params[key] === '') delete params[key]
  }
  return http.get<PageResult<ReviewItemVO>>(`${BASE}/review/items`, { params })
}

/** GET /review/type-counts —— 错题本题型分布(只数未掌握的),给「按题型复习」的下拉用 */
export function getReviewTypeCounts(subjectId?: number): Promise<ReviewTypeCountVO[]> {
  const params: Record<string, unknown> = {}
  if (subjectId != null) params.subjectId = subjectId
  return http.get<ReviewTypeCountVO[]>(`${BASE}/review/type-counts`, { params })
}

/**
 * GET /review/wrong-pool/type-counts —— 错题练习池的题型分布。
 *
 * 比 type-counts 严格:只数「已发布」的题,也就是真能抽到的题。
 * 自由练习页「错题练习」的题型下拉与「可抽 N 题」都用这个数。
 */
export function getWrongPoolTypeCounts(subjectId?: number): Promise<ReviewTypeCountVO[]> {
  const params: Record<string, unknown> = {}
  if (subjectId != null) params.subjectId = subjectId
  return http.get<ReviewTypeCountVO[]>(`${BASE}/review/wrong-pool/type-counts`, { params })
}

/** PUT /review/items/{questionId}/mastered —— 手动标记已掌握(出队) */
export function markReviewItemMastered(questionId: number): Promise<void> {
  return http.put<void>(`${BASE}/review/items/${questionId}/mastered`)
}

/** DELETE /review/items/{questionId} —— 移出错题本(删掉排期记录,题目本身不动) */
export function removeReviewItem(questionId: number): Promise<void> {
  return http.delete<void>(`${BASE}/review/items/${questionId}`)
}
