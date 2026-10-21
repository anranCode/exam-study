/**
 * 把 AI 生成的解析拆成板块。
 *
 * <p>AI 现在按「先给答案、再给解析」输出,例如:
 * <pre>
 * 【参考答案】
 * ……
 * 【译文】
 * ……
 * 【解析】
 * ……
 * </pre>
 * 展示时把每个板块单独排版,答案比一大段文字好读;没有【】标题的旧解析/手写解析
 * 会原样作为一个整段返回,不影响兼容。
 */
export interface AnalysisSection {
  /** 板块标题(不含【】);普通文本为 null */
  title: string | null
  body: string
}

/** 行首的板块标题,容忍模型偶尔加的 Markdown 粗体/标题符号 */
const SECTION_HEADING = /^\s*(?:\*{1,2}|#{1,6}|>|-\s*)*【([^】]{1,16})】\s*(.*)$/

export function splitAnalysis(text: string | null | undefined): AnalysisSection[] {
  if (!text || !text.trim()) return []

  const sections: AnalysisSection[] = []
  let current: AnalysisSection = { title: null, body: '' }

  for (const line of text.split(/\r?\n/)) {
    const match = line.match(SECTION_HEADING)
    if (match) {
      pushSection(sections, current)
      // 标题后面的同一行内容(如「【参考答案】My essay…」)也要保留;顺手去掉紧跟的冒号
      const rest = (match[2] ?? '')
        .replace(/^\*+/, '')
        .replace(/^[:：]\s*/, '')
        .trim()
      current = { title: match[1].trim(), body: rest }
      continue
    }
    current.body += (current.body ? '\n' : '') + line
  }

  pushSection(sections, current)
  return sections
}

function pushSection(out: AnalysisSection[], section: AnalysisSection) {
  const body = section.body.trim()
  if (section.title || body) {
    out.push({ title: section.title, body })
  }
}
