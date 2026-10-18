/** 日期与倒计时相关工具 */

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

/** 补零 */
function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

/** 本地日期 → 'yyyy-MM-dd' */
export function toISODate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 今天的 'yyyy-MM-dd' */
export function todayISO(): string {
  return toISODate(new Date())
}

/** 'yyyy-MM-dd' → 本地 Date(避免时区偏移) */
export function parseISODate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!match) return null
  const [, y, m, d] = match
  return new Date(Number(y), Number(m) - 1, Number(d))
}

/**
 * 距目标日期还有多少天:今天 = 0,明天 = 1,已过为负。
 * 传入非法值时返回 null。
 */
export function daysUntil(value: string | null | undefined): number | null {
  if (!value) return null
  const target = parseISODate(value)
  if (!target) return null
  const today = parseISODate(todayISO())
  if (!today) return null
  const dayMs = 24 * 60 * 60 * 1000
  return Math.round((target.getTime() - today.getTime()) / dayMs)
}

/** 'yyyy-MM-dd' → '2026年10月14日 周三' */
export function formatDisplayDate(value: string): string {
  const date = parseISODate(value)
  if (!date) return value
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 周${WEEKDAYS[date.getDay()]}`
}

/** 'yyyy-MM-dd' → '10月14日' */
export function formatShortDate(value: string): string {
  const date = parseISODate(value)
  if (!date) return value
  return `${date.getMonth() + 1}月${date.getDate()}日`
}