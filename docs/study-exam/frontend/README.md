# 学习中心 · 前端 SPA

> Vue 3 + Vite + TypeScript 单页应用。同时在电脑浏览器当管理端(录题、看统计)和手机浏览器当刷题端。以 PWA 形式安装到手机主屏。

---

## 文档索引

| 文件 | 内容 | 什么时候读 |
|---|---|---|
| `01-architecture.md` | 工程结构、技术栈、构建、PWA、请求封装 | 动手写代码前 |
| `02-routes-and-pages.md` | 路由表与每个页面的职责、布局、交互 | 写页面前 |
| `03-state-and-api.md` | Pinia store 划分、接口封装、数据流约定 | 写 store 前 |
| `04-features.md` | 按阶段拆解的功能实现清单 + 验收标准 | 排开发计划时 |
| `../design/ui-design-spec.md` | 配色、字体、组件、响应式 | 写任何样式前 |

---

## 一句话职责

前端做三件事:

1. **做题** —— 手机端主场景,今日任务、限时刷题、错题复习。
2. **录题** —— 电脑端主场景,批量导入 JSON、单题增删改、粘贴文本解析。
3. **看数** —— 正确率、掌握度、连续打卡,以及提醒设置。

---

## 技术栈

| 项 | 选型 |
|---|---|
| 框架 | Vue 3(组合式 API + `<script setup>` + TS) |
| 构建 | Vite 5 |
| 语言 | TypeScript 5 |
| 路由 | Vue Router 4 |
| 状态 | Pinia 2 |
| UI 库 | Element Plus |
| HTTP | Axios |
| PWA | vite-plugin-pwa |
| 图表 | ECharts(按需引入) |
| 包管理 | npm |
| 开发端口 | `5173` |

---

## 快速启动

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173,已配 /api 代理到 :8080
npm run build      # 产物在 dist/,拷到后端 static/ 用于自用部署
npm run type-check # vue-tsc 类型检查
```

---

## 硬性约定(不可违反)

1. **所有请求走 `src/api/` 的封装**,组件里不得直接 `axios.get`。
2. **接口返回类型必须从生成的 `types/api-generated.ts` 引用**,不得手写。
3. **样式只用 `design/ui-design-spec.md` 里的 CSS 变量**,不得写死颜色值。
4. **刷题页在任何屏幕尺寸下都是单列布局**。
5. **不得在前端保存正确答案**(后端不下发就不存);判分一律以后端返回为准。
6. 组件拆到「可以只用一句话说清它做什么」的粒度;页面组件不写业务逻辑,业务逻辑放 `stores/` 或 `composables/`。
7. 全局不得使用 `alert` / `confirm`,统一用 Element Plus 的 `ElMessage` / `ElMessageBox`。
