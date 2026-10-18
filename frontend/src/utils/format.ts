/** 格式化工具 */

/** 0.8333 → '83%';null / undefined → '—' */
export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  const ratio = value > 1 ? value : value * 100
  return `${Math.round(ratio)}%`
}

/** 秒 → '3 分 12 秒' / '12 秒' */
export function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return '0 秒'
  const m = Math.floor(seconds / 60)
  const s = Math.round(seconds % 60)
  return m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`
}

/** 数字安全显示:null / NaN → '—' */
export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return String(value)
}