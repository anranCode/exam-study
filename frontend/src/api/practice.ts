/*
 * api/practice.ts —— 练习场次相关接口
 * 一个后端接口对应一个导出函数,不在函数里塞业务逻辑。
 * 契约见 docs/study-exam/frontend/03-state-and-api.md 与后端 03-api-spec.md 第四节。
 */
import { http } from './request'
import type {
  AnswerResultVO,
  AnswerSubmitDTO,
  MockTemplateVO,
  OngoingSessionVO,
  PageResult,
  PracticeSessionCreateDTO,
  PracticeSessionDetailVO,
  PracticeSessionListItemVO,
  PracticeSessionVO,
  SelfScoreDTO,
  SessionSummaryVO
} from './types'

const BASE = '/v1/practice/sessions'

/** POST /practice/sessions —— 开始一场练习 */export function createPracticeSession(body: PracticeSessionCreateDTO): Promise<PracticeSessionVO> {
  return http.post<PracticeSessionVO>(BASE, body)
}

/** POST /practice/sessions/{sessionId}/answers —— 提交单题作答(逐题立即提交) */
export function submitPracticeAnswer(
  sessionId: number,
  body: AnswerSubmitDTO
): Promise<AnswerResultVO> {
  return http.post<AnswerResultVO>(`${BASE}/${sessionId}/answers`, body)
}

/** POST /practice/sessions/{sessionId}/answers/{questionId}/self-score —— 主观题自评 */
export function submitSelfScore(
  sessionId: number,
  questionId: number,
  body: SelfScoreDTO
): Promise<AnswerResultVO> {
  return http.post<AnswerResultVO>(`${BASE}/${sessionId}/answers/${questionId}/self-score`, body)
}

/** POST /practice/sessions/{sessionId}/finish —— 结束练习,返回本轮汇总 */
export function finishPracticeSession(sessionId: number): Promise<SessionSummaryVO> {
  return http.post<SessionSummaryVO>(`${BASE}/${sessionId}/finish`)
}

/** GET /practice/sessions —— 历史记录分页 */
export function getPracticeSessions(params?: {
  page?: number
  size?: number
  sort?: string
}): Promise<PageResult<PracticeSessionListItemVO>> {
  return http.get<PageResult<PracticeSessionListItemVO>>(BASE, { params })
}

/** GET /practice/sessions/ongoing —— 当前未完成场次(没有时为 null) */
export function getOngoingSession(): Promise<OngoingSessionVO | null> {
  return http.get<OngoingSessionVO | null>(`${BASE}/ongoing`)
}

/** GET /practice/sessions/{id} —— 某次练习详情(含逐题作答) */
export function getPracticeSessionDetail(id: number): Promise<PracticeSessionDetailVO> {
  return http.get<PracticeSessionDetailVO>(`${BASE}/${id}`)
}

/** DELETE /practice/sessions/{id} —— 删除记录(需确认) */
export function deletePracticeSession(id: number): Promise<null> {
  return http.delete<null>(`${BASE}/${id}`)
}

/** GET /practice/mock/template —— 模拟考试组卷模板(题型结构来自最近一份真题) */
export function getMockTemplate(subjectId: number): Promise<MockTemplateVO> {
  return http.get<MockTemplateVO>('/v1/practice/mock/template', { params: { subjectId } })
}