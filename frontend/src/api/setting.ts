/*
 * api/setting.ts —— 用户设置(阶段 7)
 * 一个后端接口一个导出函数,不在函数里塞业务逻辑。
 */
import { http } from './request'
import type { SettingUpsertDTO, SettingVO } from './types'

const BASE = '/v1/settings'

/** GET /settings —— 读取设置(密钥字段只返回掩码) */
export function getSettings(): Promise<SettingVO> {
  return http.get<SettingVO>(BASE)
}

/** PUT /settings —— 更新设置(只传要改的字段;密钥传回掩码表示不改) */
export function updateSettings(body: SettingUpsertDTO): Promise<SettingVO> {
  return http.put<SettingVO>(BASE, body)
}
