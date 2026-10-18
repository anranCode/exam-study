# REST 接口规范

> 所有接口前缀 `/api/v1`。统一响应包装为 `Result<T>`,响应示例中省略外层包装时以 `data` 内容为准。
>
> 单用户系统,**无鉴权**,不需要传 token。

---

## 一、通用约定

### 响应格式

```json
{ "code": 0, "message": "success", "data": { } }
```

分页响应:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [],
    "total": 128,
    "page": 1,
    "size": 20,
    "pages": 7
  }
}
```

### 分页与排序参数

所有列表接口统一支持:

| 参数 | 默认 | 说明 |
|---|---|---|
| `page` | 1 | 页码,从 1 开始 |
| `size` | 20 | 每页条数,最大 200 |
| `sort` | 见各接口 | 如 `createdAt,desc` |

### 时间格式

请求与响应中的时间统一 `yyyy-MM-dd HH:mm:ss`;日期用 `yyyy-MM-dd`。

---

## 二、科目 / 章节

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/subjects` | 科目列表(含题目数量、到期复习数量) |
| GET | `/subjects/{id}` | 科目详情 |
| POST | `/subjects` | 新建科目 |
| PUT | `/subjects/{id}` | 修改科目 |
| DELETE | `/subjects/{id}` | 删除科目(有题目时返回 400,需先清空) |
| GET | `/subjects/{id}/chapters` | 章节树 |
| POST | `/chapters` | 新建章节 |
| PUT | `/chapters/{id}` | 修改章节 |
| DELETE | `/chapters/{id}` | 删除章节 |

**GET `/subjects` 响应示例**:

```json
[
  {
    "id": 1,
    "code": "03709",
    "name": "马克思主义基本原理概论",
    "shortName": "马原",
    "color": "#E5484D",
    "examDate": "2026-10-24",
    "examSlot": "D1_AM",
    "questionCount": 320,
    "dueReviewCount": 12,
    "masteredCount": 205,
    "sortOrder": 1,
    "enabled": true
  }
]
```

---

## 三、题库

### 题目查询

```
GET /questions?subjectId=1&chapterId=3&type=SINGLE_CHOICE&keyword=矛盾&sourceYear=2024&status=PUBLISHED&page=1&size=20
```

全部参数可选。`keyword` 走 `stem` 的全文索引。

### 题目增删改

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/questions/{id}` | 题目详情 |
| POST | `/questions` | 新建题目 |
| PUT | `/questions/{id}` | 修改题目 |
| DELETE | `/questions/{id}` | 删除(级联清理答题记录与复习队列) |
| GET | `/questions/random?subjectId=&count=` | 随机取题(仅调试用) |

**POST `/questions` 请求体**:

```json
{
  "subjectId": 1,
  "chapterId": 3,
  "type": "SINGLE_CHOICE",
  "stem": "马克思主义哲学的基本问题是（  ）",
  "options": [
    { "key": "A", "text": "物质和意识的关系问题" },
    { "key": "B", "text": "思维和存在的关系问题" },
    { "key": "C", "text": "世界的本原问题" },
    { "key": "D", "text": "世界是否可知的问题" }
  ],
  "answer": "B",
  "analysis": "恩格斯指出,全部哲学,特别是近代哲学的重大的基本问题,是思维和存在的关系问题。",
  "difficulty": 1,
  "source": "2024年10月真题",
  "sourceYear": 2024,
  "score": 2.0,
  "status": "PUBLISHED"
}
```

校验规则(后端必须强制):

- `type` 属于题型枚举;客观题 (`SINGLE_CHOICE`/`MULTIPLE_CHOICE`/`TRUE_FALSE`/英语各客观题型) **必须**有 `options` 非空且 `answer` 非空。
- 主观题 (`SHORT_ANSWER`/`ESSAY`/`MATERIAL_ANALYSIS`/`WRITING`) **必须**有 `answer_points` 非空,且不得有 `answer`。
- `SINGLE_CHOICE` 的 `answer` 只能是 1 个字母;`MULTIPLE_CHOICE` 为 2—5 个不重复字母升序。
- `stem` 长度 1—2000。

### 批量导入 ★ 核心功能

```
POST /questions/import
Content-Type: multipart/form-data
    file: <json 文件>
    or
Content-Type: application/json
    body: { "subjectId": 1, "items": [ ... ] }
```

导入文件格式见本文第五节。

响应:

```json
{
  "total": 40,
  "imported": 38,
  "skipped": 2,
  "errors": [
    { "index": 12, "reason": "单项选择题缺少 answer 字段" },
    { "index": 27, "reason": "options 为空" }
  ]
}
```

**导入必须是「全部校验通过才写库」还是「逐条跳过」?**
采用**逐条容错**:校验失败的条目跳过并记录原因,成功的照常入库。理由:手工整理的真题难免有零星笔误,不能因为第 12 条错就让 39 条白干。导入结果必须把错误条目和原因返回给用户。

### 粘贴文本解析预览 ※ 后续阶段

```
POST /questions/parse-preview
body: { "text": "1. 马克思主义哲学的基本问题是（  ）\nA. 物质和意识的关系问题\nB. ...\n答案:B\n解析:..." , "subjectId": 1 }
```

不写库,只返回解析出的题目结构数组,供前端预览和修正。解析能力有限,只支持带题号、选项带 `A.` `B.` 前缀的规整文本;解析结果必须允许前端逐条编辑后再调 `POST /questions` 保存。

---

## 四、试卷与练习

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/papers?subjectId=1` | 试卷列表 |
| GET | `/papers/{id}` | 试卷详情(含题目列表) |
| POST | `/papers` | 新建试卷(可传题目 ID 数组) |
| DELETE | `/papers/{id}` | 删除试卷(不删除题目本身) |

### 开始练习

```
POST /practice/sessions
```

```json
{
  "mode": "DAILY",
  "subjectId": null,
  "chapterId": null,
  "count": 10,
  "paperId": null
}
```

响应:

```json
{
  "sessionId": 88,
  "mode": "DAILY",
  "totalCount": 10,
  "questions": [
    {
      "id": 501,
      "subjectId": 1,
      "subjectShortName": "马原",
      "type": "SINGLE_CHOICE",
      "stem": "……",
      "options": [ { "key": "A", "text": "…" } ],
      "score": 2.0,
      "isReview": true
    }
  ]
}
```

**安全约定:返回的题目对象里绝不能包含 `answer`、`answer_points`、`analysis`。** 答案只在提交作答后返回。这是防作弊,也是防止前端逻辑出错把答案提前暴露。

若已存在 `status = ONGOING` 的场次,`POST` 前应先询问用户是否继续;后端在响应中带上 `ongoingSessionId` 供前端判断。

### 提交单题作答

```
POST /practice/sessions/{sessionId}/answers
```

```json
{ "questionId": 501, "userAnswer": "B", "confidence": "SURE", "spentSeconds": 23 }
```

响应(客观题):

```json
{
  "questionId": 501,
  "isCorrect": true,
  "correctAnswer": "B",
  "analysis": "……",
  "nextReviewAt": "2026-10-18 07:00:00",
  "progress": { "answered": 4, "correct": 3, "total": 10 }
}
```

响应(主观题):`isCorrect` 为空,改返回 `answerPoints` 供前端展示勾选。

### 主观题自评提交

```
POST /practice/sessions/{sessionId}/answers/{questionId}/self-score
body: { "selfScore": 3, "totalPoints": 5 }
```

### 结束练习

```
POST /practice/sessions/{sessionId}/finish
```

响应返回本轮汇总:得分、正确率、用时、本轮新增/更新的复习项数量。

### 练习记录

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/practice/sessions?page=&size=` | 历史记录分页 |
| GET | `/practice/sessions/{id}` | 某次练习详情(含逐题作答) |
| DELETE | `/practice/sessions/{id}` | 删除记录(同步回滚该次产生的复习队列变更) |

---

## 五、题库导入格式规范 ★ 重要

这是「试卷录入」的落地格式。第一份试卷人工校对形成模板,后续批量生成。

```json
{
  "subjectCode": "03709",
  "paper": {
    "name": "2024年10月真题",
    "type": "REAL",
    "year": 2024,
    "totalScore": 100,
    "durationMin": 150
  },
  "items": [
    {
      "sectionName": "一、单项选择题",
      "type": "SINGLE_CHOICE",
      "chapterName": "第一章 物质世界及其发展规律",
      "stem": "马克思主义哲学的基本问题是（  ）",
      "options": [
        { "key": "A", "text": "物质和意识的关系问题" },
        { "key": "B", "text": "思维和存在的关系问题" },
        { "key": "C", "text": "世界的本原问题" },
        { "key": "D", "text": "世界是否可知的问题" }
      ],
      "answer": "B",
      "analysis": "恩格斯指出……",
      "score": 2.0,
      "difficulty": 1
    },
    {
      "sectionName": "二、简答题",
      "type": "SHORT_ANSWER",
      "chapterName": "第三章 人类社会及其发展规律",
      "stem": "简述生产力与生产关系的辩证关系。",
      "answerPoints": [
        "生产力决定生产关系,生产力状况决定生产关系的性质和变化",
        "生产关系反作用于生产力,适合时促进发展,不适合时阻碍发展",
        "生产关系一定要适合生产力状况是社会发展的基本规律"
      ],
      "score": 6.0,
      "difficulty": 2
    }
  ]
}
```

设计要点:

1. **`subjectCode` + `chapterName` 而不是 ID** —— 导入文件由人工/工具生成,写课程代码和章节名比写数据库 ID 稳定得多。后端导入时按 code 找科目、按 name 找章节;找不到章节时允许 `chapterId` 为空并在结果里提示。
2. **`sectionName` 保留大题名** —— 建试卷时用它作为 `paper_question.section_name`,回看试卷时能还原原始结构。
3. **`answerPoints` 是数组而不是长文本** —— 主观题自评要逐条勾选,必须结构化。
4. **分值写在题目上** —— 同一题型在不同年份分值可能变化(如习概简答 6—8 分),不能按题型硬编码。
5. 一份文件的 `items` **必须属于同一科目**。跨科目请拆成多个文件。

---

## 六、复习与统计

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/review/items?status=LEARNING&subjectId=&page=` | 复习队列(错题本) |
| GET | `/review/due-summary` | 各科目到期数量汇总(今日页用) |
| PUT | `/review/items/{questionId}/mastered` | 手动标记已掌握 |
| DELETE | `/review/items/{questionId}` | 移出错题本 |
| GET | `/stats/overview` | 总题量、平均正确率、连续天数、今日完成 |
| GET | `/stats/subjects` | 各科目正确率与掌握度 |
| GET | `/stats/trend?days=30` | 近 N 天答题量与正确率 |
| GET | `/stats/chapters?subjectId=1` | 章节掌握度(用于找薄弱章节) |

**GET `/review/due-summary` 响应**(今日页首屏靠它):

```json
{
  "date": "2026-10-15",
  "totalDue": 12,
  "bySubject": [
    { "subjectId": 1, "shortName": "马原", "color": "#E5484D", "dueCount": 5 },
    { "subjectId": 4, "shortName": "英语", "color": "#12A150", "dueCount": 7 }
  ],
  "newQuestionQuota": 10,
  "checkedInToday": false
}
```

---

## 七、设置与提醒

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/settings` | 读取设置(密钥类字段做掩码,不回明文) |
| PUT | `/settings` | 更新设置(每日题量、提醒通道、提醒时间、开关) |
| POST | `/notify/test` | 立即发一条测试提醒到微信(验证通道配置) |
| GET | `/notify/logs?days=7` | 提醒发送历史(排查漏发) |

**`PUT /settings` 关键字段**:

```json
{
  "dailyQuestionCount": 10,
  "notifyChannel": "WECOM",
  "notifyWebhook": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=…",
  "notifyKey": null,
  "reminderEnabled": true,
  "reminderTime": "07:00",
  "eveningReminderEnabled": true,
  "eveningReminderTime": "21:30",
  "darkMode": "AUTO"
}
```

`notifyChannel` 取值:`WECOM`(企业微信群机器人)/ `SERVERCHAN`(Server酱)。

**安全约定**:`GET /settings` 对 `notifyWebhook` / `notifyKey` 只回显掩码(如 `key=…a1b2c3`),前端展示时不回填完整值;更新时若提交的值与掩码一致则视为「未修改」。

---

## 八、AI 辅导接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/ai/explain` | 生成 / 获取某题的 AI 精简解析 |
| POST | `/ai/variant` | 针对某题生成一道同考点变式题 |
| POST | `/ai/mnemonic` | 针对知识点生成记忆口诀 |
| POST | `/ai/digest` | 生成 / 覆盖某科目的考前串讲清单 |
| GET | `/ai/digest?subjectId=1` | 读取已生成的串讲清单 |
| GET | `/ai/usage?days=30` | AI 用量统计(token 消耗,核算 TokenPlan) |

**`POST /ai/explain` 请求体**:

```json
{
  "questionId": 501,
  "userAnswer": "C",
  "force": false
}
```

`userAnswer` 可选——带上时,老师会针对你错选的那个选项讲混淆点。`force = true` 时忽略缓存重新生成。

响应:

```json
{
  "questionId": 501,
  "content": "考点:哲学基本问题。正确项 B…",
  "cached": true,
  "model": "qwen-plus",
  "generatedAt": "2026-10-15 07:12:00"
}
```

**`POST /ai/variant` 请求体** `{ "questionId": 501 }`;响应就是一道完整题目 JSON(结构同 `POST /questions` 的请求体),前端可一键存入库或直接作答。

**`POST /ai/digest` 请求体** `{ "subjectId": 1, "force": false }`;响应含 `content`(必背 N 点清单)。

硬性约定:

1. **缓存优先**:已生成且 `force != true` 时直接返回库中内容,不调用大模型。
2. **超时与降级**:模型调用超时 15 秒;失败返回 `code = 6001`(AI 服务暂不可用)与可读原因,前端降级显示原解析并提供「重试」。
3. **写日志**:每次真实调用(无论成败)写 `ai_call_log`,记录 token 用量与耗时。
4. **密钥不下发**:通义千问 API Key 只存服务端配置,任何接口都不返回。

---

## 九、健康检查

```
GET /actuator/health
```

仅暴露 `health` 端点,不暴露其他 actuator 端点。

---

## 十、接口实现检查表

每个接口写完自检:

- [ ] 路径、方法、参数名与本文完全一致
- [ ] 返回值是 `Result<T>` 包装,不是裸对象
- [ ] 返回的是 DTO,不是 JPA 实体
- [ ] 参数用 `@Valid` 校验,非法参数返回 `code = 1001`
- [ ] 练习取题接口不泄露 `answer` / `answer_points` / `analysis`
- [ ] 写操作有 `@Transactional`
- [ ] 加了 `@Operation` / `@Schema` 注解(前端要生成 TS 类型)
- [ ] 提醒相关:设置接口更新微信通道配置后,`/notify/test` 能实测发出
- [ ] AI 接口:响应含 `cached` 标记;失败返回 `code = 6001`;每次真实调用写入 `ai_call_log`
