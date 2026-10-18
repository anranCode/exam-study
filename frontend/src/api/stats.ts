import { http } from './request'
import type {
  ChapterStatsVO,
  StatsOverviewVO,
  SubjectStatsVO,
  TrendPointVO
} from './types'

const BASE = '/v1'

/** GET /stats/overview —— 总览统计 */
export function getOverview(): Promise<StatsOverviewVO> {
  return http.get<StatsOverviewVO>(`${BASE}/stats/overview`)
}

/** GET /stats/subjects —— 各科目正确率与掌握度 */
export function getSubjectStats(): Promise<SubjectStatsVO[]> {
  return http.get<SubjectStatsVO[]>(`${BASE}/stats/subjects`)
}

/** GET /stats/trend —— 近 N 天答题量与正确率(没作答的日期也会返回 0) */
export function getTrend(days = 30): Promise<TrendPointVO[]> {
  return http.get<TrendPointVO[]>(`${BASE}/stats/trend`, { params: { days } })
}

/** GET /stats/chapters —— 章节掌握度(掌握度低的排前面) */
export function getChapterStats(subjectId?: number): Promise<ChapterStatsVO[]> {
  const params: Record<string, unknown> = {}
  if (subjectId != null) params.subjectId = subjectId
  return http.get<ChapterStatsVO[]>(`${BASE}/stats/chapters`, { params })
}
