import { http } from './request'
import type { ChapterUpsertDTO, ChapterVO, SubjectUpsertDTO, SubjectVO } from './types'

const BASE = '/v1'

/** GET /subjects —— 科目列表 */
export function getSubjects(): Promise<SubjectVO[]> {
  return http.get<SubjectVO[]>(`${BASE}/subjects`)
}

/** GET /subjects/{id} —— 科目详情 */
export function getSubject(id: number): Promise<SubjectVO> {
  return http.get<SubjectVO>(`${BASE}/subjects/${id}`)
}

/** POST /subjects —— 新建科目 */
export function createSubject(body: SubjectUpsertDTO): Promise<SubjectVO> {
  return http.post<SubjectVO>(`${BASE}/subjects`, body)
}

/** PUT /subjects/{id} —— 更新科目 */
export function updateSubject(id: number, body: SubjectUpsertDTO): Promise<SubjectVO> {
  return http.put<SubjectVO>(`${BASE}/subjects/${id}`, body)
}

/** DELETE /subjects/{id} —— 删除科目(科目下还有题目时返回 code 2005) */
export function deleteSubject(id: number): Promise<null> {
  return http.delete<null>(`${BASE}/subjects/${id}`)
}

/** GET /subjects/{id}/chapters —— 某科目的章节扁平列表 */
export function getChapters(subjectId: number): Promise<ChapterVO[]> {
  return http.get<ChapterVO[]>(`${BASE}/subjects/${subjectId}/chapters`)
}

/** POST /chapters —— 新建章节 */
export function createChapter(body: ChapterUpsertDTO): Promise<ChapterVO> {
  return http.post<ChapterVO>(`${BASE}/chapters`, body)
}

/**
 * PUT /chapters/{id} —— 更新章节
 * 省略 parentId 表示不改变父级;传 null 表示清除父级(升为根章节)。
 */
export function updateChapter(id: number, body: ChapterUpsertDTO): Promise<ChapterVO> {
  return http.put<ChapterVO>(`${BASE}/chapters/${id}`, body)
}

/** DELETE /chapters/{id} —— 删除章节(含题目或子章节时返回 code 2006) */
export function deleteChapter(id: number): Promise<null> {
  return http.delete<null>(`${BASE}/chapters/${id}`)
}