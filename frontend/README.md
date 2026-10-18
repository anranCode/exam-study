# 自考学习中心 · 前端

个人自用的自考刷题与复习工具前端。Vite + Vue 3 + TypeScript + Pinia + Vue Router + Element Plus。

## 环境要求

- Node.js ≥ 20(本项目在 Node v22 上开发)
- 后端运行在 `http://localhost:8080`

## 安装

```bash
npm install
```

## 开发

```bash
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`,并把 `/api` 代理到 `http://localhost:8080`
(见 `vite.config.ts`)。接口基地址由 `.env.development` 中的 `VITE_API_BASE` 控制,默认 `/api`。

## 类型检查

```bash
npm run type-check      # 等价于 npx vue-tsc --noEmit
```

## 构建

```bash
npm run build           # 先 vue-tsc --noEmit,再 vite build
npm run preview         # 本地预览构建产物
```

构建产物在 `dist/`,可拷到后端 `src/main/resources/static/` 由后端托管。

## 重新生成接口类型

后端启动后,根据 OpenAPI 文档重新生成 `src/api/generated.ts`:

```bash
npm run gen:api
# 等价于:openapi-typescript http://localhost:8080/v3/api-docs -o src/api/generated.ts
```

> `src/api/generated.ts` 是自动生成文件,**请勿手改**。
> 后端接口变更后的标准动作:改后端 → 重启后端 → `npm run gen:api` → `npm run type-check` → 按报错改前端。

## 目录结构

```
src/
├── api/            # 所有接口封装(request.ts 为唯一 axios 出口,generated.ts 自动生成)
├── stores/         # Pinia:subject / today / practice / stats / setting
├── router/         # 路由表与实例
├── layouts/        # MobileLayout(底部 Tab)/ DesktopLayout(左侧栏)
├── views/          # 页级组件,一个路由一个
├── components/     # StatNumber / SubjectTag / ProgressBar / EmptyState 等
├── composables/    # useBreakpoint 等
├── utils/          # constants / date / format
└── styles/         # variables.css(全部设计变量)/ element-override.css / global.css
```

## 约定

- 颜色只从 `styles/variables.css` 的 CSS 变量取,组件里不写死颜色。
- 组件不直接调 `api/`,一律通过 store 的 action。
- 所有可点击元素最小 44×44px;界面文案统一中文。
- 不使用 `alert()` / `confirm()`,删除确认统一用 `ElMessageBox`。

## 当前进度

已完成「阶段 0:工程骨架」与「阶段 1:科目与今日页(骨架版)」:

- 科目与章节管理(`/subjects`)已接通后端,可增删改。
- 今日页(`/`)已接通 `/review/due-summary`、`/stats/overview`,含骨架屏与空状态。

其余页面(刷题、题库、导入、错题、统计、串讲、设置)为统一占位页,将在后续阶段实现。