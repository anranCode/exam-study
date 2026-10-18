/*
 * api/notify.ts —— 提醒通道(阶段 7)
 */
import { http } from './request'
import type { NotifyLogVO, NotifyTestResultVO } from './types'

const BASE = '/v1/notify'

/** POST /notify/test —— 立即发一条测试提醒;失败也返回 200,message 是失败原因 */
export function testNotify(): Promise<NotifyTestResultVO> {
  return http.post<NotifyTestResultVO>(`${BASE}/test`)
}

/** GET /notify/logs —— 近 N 天提醒发送记录(排查漏发) */
export function getNotifyLogs(days = 7): Promise<NotifyLogVO[]> {
  return http.get<NotifyLogVO[]>(`${BASE}/logs`, { params: { days } })
}
