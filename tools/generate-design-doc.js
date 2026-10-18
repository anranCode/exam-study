/**
 * 自考学习中心 · 设计文档 Markdown → Word(.docx) 生成器
 * ------------------------------------------------------------
 * 设计思路:单一数据源。
 *   · 文档内容只维护在一份 Markdown 里(软件设计文档-需求总结.md)
 *   · 本脚本负责把它渲染成排版规范、可直接交给老师/同事的 Word 文档
 *   · 以后文档要更新,只改 Markdown,重跑一次脚本即可
 *
 * 运行方式(在本文件所在目录执行):
 *     npm i docx            # 首次需要(若已执行过 npm init -y / npm i docx 可跳过)
 *     node generate-design-doc.js
 *
 * 可选:指定输入输出路径
 *     node generate-design-doc.js <输入.md> <输出.docx>
 *
 * 默认输入:../软件设计文档-需求总结.md
 * 默认输出:../软件设计文档-需求总结.docx
 * 环境要求:Node.js 18+
 *
 * 支持的 Markdown 语法:
 *   # ~ #### 标题、段落、**加粗**、`行内代码`、[链接](url)、
 *   | 表格 |、1. 有序列表、- 无序列表、``` 代码块、--- 分隔线
 */

const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, ShadingType, LevelFormat,
  Footer, PageNumber, BorderStyle,
} = require("docx");

// ─────────────────────────── 常量 ───────────────────────────

const FONT = "微软雅黑";
const MONO = "Consolas";
const INK = "1A1D21";
const INK2 = "3D444D";
const INK3 = "6B7280";
const LINE = "D8DDE5";
const PAGE_CONTENT = 9638; // A4 去 2cm 页边距后的可写宽度(DXA)
const MIN_COL = 850;       // 表格单列最小宽度
const MAX_COL_RATIO = 0.62; // 表格单列最大占比

const mdPath = path.resolve(
  process.argv[2] || path.join(__dirname, "..", "软件设计文档-需求总结.md")
);
const outPath = path.resolve(
  process.argv[3] || mdPath.replace(/\.md$/i, ".docx")
);

const md = fs.readFileSync(mdPath, "utf8").replace(/^﻿/, "");
const lines = md.split(/\r?\n/);

// ─────────────────────────── 行内解析 ───────────────────────────

/** 把一段 markdown 文本解析成 TextRun 数组,支持 **加粗**、`代码`、[链接](url) */
function inline(text, base = {}) {
  const runs = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]*\))/g;
  let last = 0, m;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) runs.push(new TextRun({ ...base, font: FONT, text: text.slice(last, m.index) }));
    const tok = m[0];
    if (tok.startsWith("**")) {
      runs.push(new TextRun({ ...base, font: FONT, bold: true, text: tok.slice(2, -2) }));
    } else if (tok.startsWith("`")) {
      runs.push(new TextRun({ ...base, font: MONO, color: INK2, text: tok.slice(1, -1) }));
    } else {
      const label = tok.slice(1, tok.indexOf("]"));
      runs.push(new TextRun({ ...base, font: FONT, color: "2F6FED", text: label }));
    }
    last = m.index + tok.length;
  }
  if (last < text.length) runs.push(new TextRun({ ...base, font: FONT, text: text.slice(last) }));
  if (runs.length === 0) runs.push(new TextRun({ ...base, font: FONT, text: "" }));
  return runs;
}

/** 去掉 markdown 标记,用于标题等无需行内样式的场合 */
const plain = (t) => t.replace(/\*\*|`/g, "").trim();

// ─────────────────────────── 表格宽度估算 ───────────────────────────

/** 显示宽度:CJK 记 2,其余记 1 */
function dispWidth(s) {
  let w = 0;
  for (const ch of s) w += /[ᄀ-￦]/.test(ch) ? 2 : 1;
  return w;
}

function calcWidths(header, rows) {
  const cols = header.length;
  const weight = Array.from({ length: cols }, (_, i) => {
    let max = dispWidth(plain(header[i] || ""));
    for (const r of rows) max = Math.max(max, dispWidth(plain(r[i] || "")));
    return Math.max(max + 6, 8);
  });

  const totalW = weight.reduce((a, b) => a + b, 0);
  let widths = weight.map((w) => Math.round((w / totalW) * PAGE_CONTENT));

  // 下限与上限
  const maxCap = Math.round(PAGE_CONTENT * MAX_COL_RATIO);
  widths = widths.map((w) => Math.min(Math.max(w, MIN_COL), maxCap));
  // 归一化:把差额并入最后一列;若出现异常(负值),退化为等宽
  const sum = widths.reduce((a, b) => a + b, 0);
  widths[cols - 1] = widths[cols - 1] + (PAGE_CONTENT - sum);
  const rest = PAGE_CONTENT - widths.slice(0, -1).reduce((a, b) => a + b, 0);
  if (rest < 600) widths = Array(cols).fill(Math.floor(PAGE_CONTENT / cols));
  else widths[cols - 1] = rest;
  return widths;
}

// ─────────────────────────── 块解析 ───────────────────────────

const EMPTY_SEP = (row) =>
  row.every((c) => /^:?-{2,}:?$/.test(c.replace(/\s/g, "")));

const blocks = [];
let i = 0;

while (i < lines.length) {
  const line = lines[i];

  // 代码块
  if (/^```/.test(line)) {
    const code = [];
    i++;
    while (i < lines.length && !/^```/.test(lines[i])) {
      code.push(lines[i].replace(/\t/g, "  "));
      i++;
    }
    i++; // 跳过结束 fence
    blocks.push({ type: "code", text: code.join("\n") });
    continue;
  }

  // 分隔线
  if (/^-{3,}\s*$/.test(line)) {
    blocks.push({ type: "hr" });
    i++;
    continue;
  }

  // 标题
  const hm = line.match(/^(#{1,4})\s+(.*)$/);
  if (hm) {
    blocks.push({ type: "h", level: hm[1].length, text: plain(hm[2]) });
    i++;
    continue;
  }

  // 表格
  if (/^\s*\|/.test(line)) {
    const rawRows = [];
    while (i < lines.length && /^\s*\|/.test(lines[i])) {
      const cells = lines[i].trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());
      if (!EMPTY_SEP(cells)) rawRows.push(cells);
      i++;
    }
    if (rawRows.length) {
      blocks.push({ type: "table", header: rawRows[0], rows: rawRows.slice(1) });
    }
    continue;
  }

  // 有序列表
  if (/^\d+\.\s+/.test(line)) {
    const items = [];
    while (i < lines.length && /^\d+\.\s+/.test(lines[i])) {
      items.push(lines[i].replace(/^\d+\.\s+/, ""));
      i++;
    }
    blocks.push({ type: "ol", items });
    continue;
  }

  // 无序列表
  if (/^[-*]\s+/.test(line)) {
    const items = [];
    while (i < lines.length && /^[-*]\s+/.test(lines[i])) {
      items.push(lines[i].replace(/^[-*]\s+/, "").replace(/^\[[ x]\]\s*/, "☐ "));
      i++;
    }
    blocks.push({ type: "ul", items });
    continue;
  }

  // 空行
  if (!line.trim()) {
    i++;
    continue;
  }

  // 普通段落(连续非特殊行合并,行间软换行)
  const para = [line.trim()];
  i++;
  while (
    i < lines.length &&
    lines[i].trim() &&
    !/^(#{1,4}\s|```|-\s|\*\s|\d+\.\s|\s*\|)/.test(lines[i]) &&
    !/^-{3,}\s*$/.test(lines[i])
  ) {
    para.push(lines[i].trim());
    i++;
  }
  blocks.push({ type: "p", text: para.join("\n") });
}

// ─────────────────────────── 渲染为 docx 元素 ───────────────────────────

const OUT = [];
let olInstance = 0;

for (const b of blocks) {
  switch (b.type) {
    case "h": {
      if (b.level === 1) {
        OUT.push(new Paragraph({
          heading: HeadingLevel.HEADING_1,
          spacing: { before: 0, after: 240 },
          border: { bottom: { style: BorderStyle.SINGLE, size: 18, color: INK, space: 8 } },
          children: [new TextRun({ text: b.text, font: FONT, size: 40, bold: true, color: INK })],
        }));
      } else if (b.level === 2) {
        OUT.push(new Paragraph({
          heading: HeadingLevel.HEADING_2,
          spacing: { before: 400, after: 180 },
          border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: LINE, space: 6 } },
          children: [new TextRun({ text: b.text, font: FONT, size: 30, bold: true, color: INK })],
        }));
      } else if (b.level === 3) {
        OUT.push(new Paragraph({
          heading: HeadingLevel.HEADING_3,
          spacing: { before: 280, after: 120 },
          children: [new TextRun({ text: b.text, font: FONT, size: 24, bold: true, color: INK })],
        }));
      } else {
        OUT.push(new Paragraph({
          heading: HeadingLevel.HEADING_4,
          spacing: { before: 220, after: 100 },
          children: [new TextRun({ text: b.text, font: FONT, size: 22, bold: true, color: INK2 })],
        }));
      }
      break;
    }

    case "p": {
      const parts = b.text.split("\n");
      const runs = [];
      parts.forEach((p, idx) => {
        if (idx > 0) runs.push(new TextRun({ text: "", break: 1, font: FONT }));
        runs.push(...inline(p));
      });
      OUT.push(new Paragraph({
        spacing: { after: 130, line: 360 },
        children: runs,
      }));
      break;
    }

    case "ol": {
      olInstance++;
      for (const item of b.items) {
        OUT.push(new Paragraph({
          numbering: { reference: "numbers", level: 0, instance: olInstance },
          spacing: { after: 70, line: 340 },
          children: inline(item),
        }));
      }
      break;
    }

    case "ul": {
      for (const item of b.items) {
        OUT.push(new Paragraph({
          numbering: { reference: "bullets", level: 0 },
          spacing: { after: 70, line: 340 },
          children: inline(item),
        }));
      }
      break;
    }

    case "code": {
      const style = { font: MONO, size: 17, color: INK2 };
      const cl = b.text.split("\n");
      OUT.push(new Paragraph({
        spacing: { before: 130, after: 210, line: 250 },
        shading: { type: ShadingType.CLEAR, fill: "F8FAFC" },
        children: cl.map((l, idx) =>
          idx === 0
            ? new TextRun({ text: l, ...style })
            : new TextRun({ text: l, break: 1, ...style })
        ),
      }));
      break;
    }

    case "table": {
      const widths = calcWidths(b.header, b.rows);
      const cell = (content, w, opts = {}) =>
        new TableCell({
          width: { size: w, type: WidthType.DXA },
          margins: { top: 80, bottom: 80, left: 110, right: 110 },
          shading: opts.fill ? { type: ShadingType.CLEAR, fill: opts.fill } : undefined,
          children: [
            new Paragraph({
              spacing: { after: 0, line: 300 },
              children: inline(content, opts.bold ? { size: 19, bold: true } : { size: 19 }),
            }),
          ],
        });

      OUT.push(new Table({
        columnWidths: widths,
        width: { size: PAGE_CONTENT, type: WidthType.DXA },
        rows: [
          new TableRow({
            tableHeader: true,
            children: b.header.map((h, idx) => cell(h, widths[idx], { fill: "F1F5FA", bold: true })),
          }),
          ...b.rows.map((r) => new TableRow({
            children: b.header.map((_, idx) => cell(r[idx] || "", widths[idx])),
          })),
        ],
      }));
      // 表后留白
      OUT.push(new Paragraph({ spacing: { after: 120 }, children: [] }));
      break;
    }

    case "hr": {
      OUT.push(new Paragraph({
        spacing: { before: 180, after: 180 },
        border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: LINE, space: 1 } },
        children: [],
      }));
      break;
    }
  }
}

// ─────────────────────────── 组装 ───────────────────────────

const doc = new Document({
  creator: "自考学习中心",
  title: path.basename(mdPath, ".md"),
  description: "由 Markdown 自动渲染",
  styles: {
    default: {
      document: { run: { font: FONT, size: 21, color: INK }, paragraph: { spacing: { line: 360 } } },
      heading1: { run: { font: FONT, size: 40, bold: true, color: INK } },
      heading2: { run: { font: FONT, size: 30, bold: true, color: INK } },
      heading3: { run: { font: FONT, size: 24, bold: true, color: INK } },
      heading4: { run: { font: FONT, size: 22, bold: true, color: INK2 } },
    },
  },
  numbering: {
    config: [
      {
        reference: "bullets",
        levels: [{
          level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 460, hanging: 260 } } },
        }],
      },
      {
        reference: "numbers",
        levels: [{
          level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 460, hanging: 300 } } },
        }],
      },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },
        margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 },
      },
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ children: ["第 ", PageNumber.CURRENT, " 页"], font: FONT, size: 18, color: INK3 })],
        })],
      }),
    },
    children: OUT,
  }],
});

Packer.toBuffer(doc)
  .then((buf) => {
    fs.writeFileSync(outPath, buf);
    console.log("✓ 已生成:" + outPath);
    console.log("  大小:" + (buf.length / 1024).toFixed(1) + " KB");
    console.log("  来源:" + mdPath);
  })
  .catch((err) => {
    console.error("✗ 生成失败:", err && err.message ? err.message : err);
    console.error("  请把以上报错原文发给 Claude,以便修正脚本。");
    process.exit(1);
  });