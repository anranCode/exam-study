import { http } from './request'
import type { DueSummaryVO, PageResult, ReviewItemQuery, ReviewItemVO } from './types'

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

/** PUT /review/items/{questionId}/mastered —— 手动标记已掌握(出队) */
export function markReviewItemMastered(questionId: number): Promise<void> {
  return http.put<void>(`${BASE}/review/items/${questionId}/mastered`)
}

/** DELETE /review/items/{questionId} —— 移出错题本(删掉排期记录,题目本身不动) */
export function removeReviewItem(questionId: number): Promise<void> {
  return http.delete<void>(`${BASE}/review/items/${questionId}`)
}
