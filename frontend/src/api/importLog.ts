/*
 * api/importLog.ts —— 导入记录(导入失败明细留档)
 * 约定同其它 api 文件:一个后端接口一个函数,不塞业务逻辑。
 */
import { http } from './request'
import type { ImportLogCreate, ImportLogVO, PageResult } from './types'

const BASE = '/v1/import/logs'

/** POST /import/logs —— 导入结束后记录本次结果(含没入库的题目与识别提醒) */
export function createImportLog(body: ImportLogCreate): Promise<ImportLogVO> {
  return http.post<ImportLogVO>(BASE, body)
}

/** GET /import/logs —— 按时间倒序分页 */
export function getImportLogs(page = 1, size = 20): Promise<PageResult<ImportLogVO>> {
  return http.get<PageResult<ImportLogVO>>(BASE, { params: { page, size } })
}

/** DELETE /import/logs/{id} —— 补录完成后清理 */
export function deleteImportLog(id: number): Promise<void> {
  return http.delete<void>(`${BASE}/${id}`)
}
