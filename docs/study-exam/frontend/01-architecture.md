# 前端架构

---

## 一、工程结构

```
frontend/
├── index.html
├── vite.config.ts            # 别名、代理、PWA 插件
├── tsconfig.json
├── package.json
├── public/
│   ├── favicon.svg
│   └── icons/                # PWA 图标 192 / 512
└── src/
    ├── main.ts               # 挂载 app、注册 Pinia / Router / Element Plus
    ├── App.vue               # 只有 <RouterView /> 与全局弹层容器
    ├── api/                  # ★ 所有接口封装,唯一出口
    │   ├── request.ts        # axios 实例、拦截器、错误提示
    │   ├── generated.ts      # openapi-typescript 自动生成,禁止手改
    │   ├── subject.ts
    │   ├── question.ts
    │   ├── practice.ts
    │   ├── review.ts
    │   ├── stats.ts
    │   ├── setting.ts
    │   ├── notify.ts
    │   └── ai.ts
    ├── stores/               # Pinia
    │   ├── subject.ts
    │   ├── practice.ts       # 当前练习场次(答题状态机就在这里)
    │   ├── today.ts          # 今日任务汇总
    │   ├── stats.ts
    │   └── setting.ts
    ├── router/
    │   ├── index.ts
    │   └── routes.ts
    ├── layouts/
    │   ├── MobileLayout.vue  # 底部 Tab 导航
    │   └── DesktopLayout.vue # 左侧栏导航
    ├── views/                # 页级组件,一个路由一个
    │   ├── TodayView.vue
    │   ├── PracticeView.vue
    │   ├── QuestionListView.vue
    │   ├── QuestionEditView.vue
    │   ├── QuestionImportView.vue
    │   ├── WrongBookView.vue
    │   ├── StatsView.vue
    │   └── SettingView.vue
    ├── components/           # 可复用组件
    │   ├── OptionCard.vue        # ★ 选项卡片(五种状态)
    │   ├── QuestionCard.vue      # 题干 + 选项 + 解析
    │   ├── SubjectTag.vue
    │   ├── DueCountBadge.vue
    │   ├── ProgressBar.vue
    │   ├── EmptyState.vue
    │   └── StatNumber.vue
    ├── composables/
    │   ├── useBreakpoint.ts      # 判断手机/桌面,决定用哪个 layout
    │   ├── useAnswerFlow.ts      # 答题流程:选中 → 提交 → 展示解析 → 下一题
    │   └── useCountdown.ts       # 考试倒计时、练习计时
    ├── utils/
    │   ├── date.ts
    │   ├── format.ts
    │   └── constants.ts          # 题型枚举、模式枚举、文案常量
    ├── styles/
    │   ├── variables.css         # ★ 全部设计变量,取自 ui-design-spec
    │   ├── element-override.css  # 覆盖 Element Plus 默认主题
    │   └── global.css
    └── sw/
        └── sw.ts                 # Service Worker:仅做离线缓存(提醒走微信通道)
```

---

## 二、数据流约定

```
组件  →  store(action)  →  api/xxx.ts  →  axios  →  后端
  ↑                                                  ↓
  └────── 响应式更新 ← store(state) ←─────────────────┘
```

- **组件不直接调 api**,只调 store 的 action。例外:纯展示型的一次性查询(如统计页)可以在 `onMounted` 里调 store action,但仍不绕过 store。
- **store 不持有 DOM 相关状态**。
- **api 层只做请求与类型转换**,不做业务判断。

---

## 三、请求封装(`api/request.ts`)

必须实现:

1. **baseURL** 通过 `import.meta.env.VITE_API_BASE` 注入,开发时为 `/api`(靠 Vite proxy 转发),生产可留空(同源)。
2. **请求拦截器**:不做鉴权(单用户),但统一加上 `Content-Type` 与超时(默认 15 秒;AI 生成解析单次 120 秒)。
3. **响应拦截器**:

   - `code === 0` → 直接返回 `data`(业务代码不必每次解包)
   - `code !== 0` → 抛出一个携带 `code` 与 `message` 的 `ApiError`,并调用 `ElMessage.error(message)`
   - 网络错误 → 提示「无法连接服务器,请确认后端已启动」
4. **失败不静默**:任何请求失败都必须在界面上有反馈,不允许只 `console.error`。

```ts
// 用法:业务层直接拿 data,不用管外层包装
const subjects = await getSubjects()
```

---

## 四、答题状态机(`useAnswerFlow` + `stores/practice.ts`)

刷题页的状态流转是前端最核心的逻辑,单独抽成 composable:

```
IDLE          未开始,展示「开始今日 N 题」
  ↓
ANSWERING     已作答但未提交(选项可改选)
  ↓ 提交
GRADED        已判定:显示对错、正确答案、解析;不可改选
  ↓ 下一题
ANSWERING / FINISHED
  ↓ 最后一题提交后
SUMMARY       本轮小结:得分、正确率、用时、「再来一轮」
```

关键规则:

- 判定后**选项不可再点击**(由 `GRADED` 状态控制)。
- 答题进度**每答一题立即提交到后端**,不要攒到最后批量提交——碎片时间场景下用户随时可能退出。
- 退出时提示「本次练习已保存,下次可以继续」,不阻断退出。
- `confidence` 的取值:用户点「不确定」按钮提交 → `UNSURE`;答错 → `WRONG`;其余 → `SURE`。

---

## 五、PWA 与离线

`vite.config.ts` 里配置 `vite-plugin-pwa`:

```ts
VitePWA({
  registerType: 'autoUpdate',
  manifest: {
    name: '自考学习中心',
    short_name: '自考',
    display: 'standalone',
    theme_color: '#2F6FED',
    icons: [ /* 192 / 512 */ ]
  }
})
```

用默认的 `generateSW` 即可——**Service Worker 只负责离线缓存,不承担推送职责**。提醒全部由后端经微信通道发送(见 `docs/design/tech-stack.md`),前端不需要任何浏览器通知权限逻辑。

要点:

- 手机端访问服务器地址后,用浏览器菜单「添加到主屏幕」,以独立窗口运行。
- 离线打开时,今日页与题库列表从缓存读取;提交作答需要联网,离线时给出「当前无网络,答案会在联网后提交」的提示——**不做离线提交队列**,自用场景不值得为它增加复杂度。
- 设置页仍提供「发送测试提醒」按钮,但它调用的是 `POST /notify/test`(后端触发微信消息),与浏览器通知无关。

---

## 六、布局切换

用 `useBreakpoint` 判断:

- `window.innerWidth < 768` → `MobileLayout`(底部四 Tab)
- 否则 → `DesktopLayout`(左侧栏 + 内容区)

通过路由的 `meta.layout` 或直接在 `App.vue` 里按断点切换根布局组件。不需要为两套布局写两份页面。

---

## 七、性能与体验细节

- **路由懒加载**:除今日页外全部 `() => import(...)`。
- **骨架屏**:今日页、题库列表、统计页首次加载显示骨架屏。
- **题干与解析的排版**:题干用 `white-space: pre-wrap` 保留换行,长英文段落自动换行不断词。
- **提交答案的按钮要有 loading 态**,防连点重复提交。
- 不做全局 loading 遮罩,只在具体区域局部 loading。
