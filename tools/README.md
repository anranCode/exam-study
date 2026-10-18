# tools · 文档生成脚本

这个目录放的是**在你本机运行的脚本**,不经过 Claude 的沙盒环境。

## 为什么需要它

生成 `.docx` 需要用 Node 把内容打包成 ZIP(Word 文档本质是个 ZIP 包),而 Claude 的代码执行沙盒当时无法启动。把脚本放在你的 E 盘自己运行,可以完全绕开这个问题。

## 工作方式:单一数据源

```
软件设计文档-需求总结.md        ← 文档内容的唯一来源(以后改内容只改这里)
        │
        ▼  node generate-design-doc.js
软件设计文档-需求总结.docx      ← 自动渲染出的 Word 版
```

**以后文档要更新的标准流程**:

1. 和 Claude 讨论完新功能后,由 Claude 更新 `../软件设计文档-需求总结.md`(以及 `docs/` 下的规格文件);
2. 你重新运行一次脚本;
3. 得到最新版 Word 文档。

不需要在 Word 里手动改——手改的版本下次生成会被覆盖,而且会和 Markdown 源文件不一致。

## 运行

前置:本机已安装 Node.js 18+(终端执行 `node -v` 查看;没有的话到 https://nodejs.org 下载 LTS 版)。

```bash
cd E:\study\exam-system\app\tools
npm i docx
node generate-design-doc.js
```

输出到 `E:\study\exam-system\app\软件设计文档-需求总结.docx`。

也可以指定输入输出:

```bash
node generate-design-doc.js <输入.md> <输出.docx>
```

## 脚本说明

| 文件 | 作用 |
|---|---|
| `generate-design-doc.js` | 读取 Markdown,渲染为排版规范的 Word 文档 |

## 支持的 Markdown 语法

`#` ~ `####` 标题、段落、`**加粗**`、`` `行内代码` ``、`[链接](url)`、表格、有序/无序列表、``` 代码块、`---` 分隔线。

## 排版说明

- A4 页面,上下左右页边距 2cm;中文微软雅黑,代码与架构图 Consolas
- 表格列宽按内容长度自动估算,表头浅灰底;Word 与 WPS 下都不会错位
- 标题使用 Word 内置标题级别,可在 Word 中按 Ctrl+A 再按 F9 自动生成目录
- 页脚居中页码
- 代码块(架构图)用浅灰底等宽字体,保持对齐

## 出错了怎么办

把终端里的完整报错原文发给 Claude,一般一两轮就能修好。若是提示 `Cannot find module 'docx'`,说明依赖没装,执行 `npm i docx` 即可。