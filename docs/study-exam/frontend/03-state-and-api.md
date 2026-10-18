# 状态管理与接口对接

---

## 一、Pinia Store 划分

| Store | 职责 | 是否持久化 |
|---|---|---|
| `useSubjectStore` | 科目与章节列表,供全局下拉使用 | 是(会话内缓存即可) |
| `useTodayStore` | 今日任务汇总(到期数、打卡状态、连续天数) | 否 |
| `usePracticeStore` | **当前练习场次**:题目列表、当前索引、作答状态机 | 是(存 localStorage 以便刷新恢复) |
| `useWrongStore` | 错题本列表与筛选条件 | 否 |
| `useStatsStore` | 统计数据 | 否 |
| `useSettingStore` | 用户设置、提醒通道配置 | 设置是 |

**不要让所有数据都进 store。** 只把「多个页面共享」或「需要跨路由保持」的状态放进去。题库列表页的筛选条件属于页面局部状态,用 `ref` 即可。

---

## 二、各 Store 规格

### usePracticeStore(最重要)

```ts
state: {
  sessionId: number | null
  mode: 'daily' | 'random' | 'chapter' | 'mock' | 'review' | null
  questions: QuestionVO[]          // 不含答案
  currentIndex: number
  answeredMap: Record<number, AnswerRecord>  // questionId → 已作答结果
  status: 'IDLE' | 'ANSWERING' | 'GRADING' | 'GRADED' | 'FINISHED'
  startedAt: number
  elapsedSeconds: number
}
```

actions:

| action | 说明 |
|---|---|
| `startSession(payload)` | 调 `POST /practice/sessions`,填充 questions,status → `ANSWERING` |
| `selectOption(key)` | 仅记录本地选中,**不提交** |
| `submitAnswer(confidence)` | 调提交接口,写入 `answeredMap`,status → `GRADED` |
| `nextQuestion()` | 索引 +1;已是最后一题则 `finishSession()` |
| `finishSession()` | 调结束接口,status → `FINISHED`,返回汇总数据 |
| `submitSelfScore(points)` | 主观题自评提交 |
| `restoreFromStorage()` | 页面刷新后恢复进度 |

**关键规则:**

- `answeredMap` 是判定状态的唯一来源,选项卡片的状态由它推导,不另存一份。
- 切换路由不销毁 store(不使用 `$reset`),这样退出再回来能继续。
- `finishSession` 后调用 `useTodayStore().refresh()` 并 `useWrongStore().refresh()`,让今日页与错题本立刻反映最新状态。

### useTodayStore

```ts
state: { dueTotal, dueBySubject[], checkedIn, streakDays, lastSession, ongoingSessionId }
actions: { refresh() }
```

今日页 `onMounted` 调 `refresh()`。**每次结束练习后必须重新拉取**,否则数字不更新。

### useSubjectStore

缓存科目与章节。提供 `subjectMap`(id → subject)与 `chapterMap` 计算属性,让列表页渲染时不必每次查找。

---

## 三、接口封装约定

`api/` 下每个文件对应一组后端资源,函数命名与接口路径对应:

```ts
// api/question.ts
export function getQuestions(params: QuestionQuery): Promise<PageResult<QuestionVO>>
export function getQuestion(id: number): Promise<QuestionDetailVO>
export function createQuestion(body: QuestionCreateDTO): Promise<QuestionDetailVO>
export function updateQuestion(id: number, body: QuestionUpdateDTO): Promise<QuestionDetailVO>
export function deleteQuestion(id: number): Promise<void>
export function importQuestions(body: ImportRequest): Promise<ImportResult>
```

规则:

1. **一个后端接口对应一个导出函数**,不在函数里塞额外业务逻辑。
2. 所有类型从 `generated.ts` 引入并在其上做少量别名,例如 `export type QuestionVO = components['schemas']['QuestionVO']`。
3. 需要额外字段时**改后端 DTO**,不在前端拼接。
4. 上传文件用 `FormData`,不要手动设 `Content-Type`(浏览器会自动带 boundary)。

---

## 四、类型生成流程

```bash
# 后端已启动的情况下
npm run gen:api
# 等价于:
# openapi-typescript http://localhost:8080/v3/api-docs -o src/api/generated.ts
```

在 `package.json` 里配上脚本。后端接口变更后的标准动作:

```
改后端 → 重启后端 → npm run gen:api → npm run type-check → 按报错改前端
```

`generated.ts` 加注释标明「自动生成,请勿手动修改」,并加入 `.prettierignore` / `.eslintignore`。

---

## 五、错误处理

| 场景 | 处理方式 |
|---|---|
| 后端返回业务错误码 | `request.ts` 统一弹 `ElMessage.error(message)` 并抛出 `ApiError` |
| 业务层需要额外处理(如「场次已结束」) | 在 action 里 `try/catch` 捕获 `ApiError`,按 `code` 分支处理 |
| 网络不可达 | 提示「无法连接服务器,请确认后端已启动,地址:…」 |
| 401 / 403 | 不适用(无鉴权) |
| 提交答案失败 | **必须让用户重试**,不能静默跳过——漏一题会影响复习排期 |

> 特别注意:提交答案失败时**不要自动跳到下一题**。用户以为答了,实际没记录,复习队列就漏了。

---

## 六、常量定义(`utils/constants.ts`)

题型与模式的中文名、颜色、是否客观题,必须集中定义在这里,避免散落各处:

```ts
export const QUESTION_TYPE_LABEL: Record<string, string> = {
  SINGLE_CHOICE: '单项选择题',
  MULTIPLE_CHOICE: '多项选择题',
  TRUE_FALSE: '判断题',
  READING_JUDGE: '阅读判断',
  READING_CHOICE: '阅读选择',
  SECTION_MATCH: '概括段落大意和补全句子',
  SENTENCE_FILL: '填句补文',
  WORD_FILL: '填词补文',
  CLOZE_TRANSFORM: '完形补文',
  SHORT_ANSWER: '简答题',
  ESSAY: '论述题',
  MATERIAL_ANALYSIS: '材料分析题',
  WRITING: '短文写作'
}

// 客观题:后端自动判分
export const OBJECTIVE_TYPES = [
  'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE',
  'READING_JUDGE', 'READING_CHOICE', 'SECTION_MATCH',
  'SENTENCE_FILL', 'WORD_FILL', 'CLOZE_TRANSFORM'
] as const

// 主观题:需要自评
export const SUBJECTIVE_TYPES = [
  'SHORT_ANSWER', 'ESSAY', 'MATERIAL_ANALYSIS', 'WRITING'
] as const

export const isObjective = (t: string) => OBJECTIVE_TYPES.includes(t as any)
```

**新增题型时只需改这个文件**,界面标签、判分分支、筛选下拉都跟着走。
