# 后端功能实现清单

> 按阶段实现。**每个阶段做完停下来等确认**,不要连续做完所有阶段。
> 每阶段末尾的「验收标准」是硬性的,不通过不算完成。

---

## 阶段 0:工程骨架

**要做的事**

1. `mvn archetype` 生成 Spring Boot 3 + Java 17 工程,`groupId = com.studyexam`,`artifactId = study-exam-backend`。
2. 引入依赖:spring-boot-starter-web、starter-data-jpa、starter-validation、starter-actuator、mysql-connector-j、flyway-core、flyway-mysql、lombok(可选)、springdoc-openapi-starter-webmvc-ui。(千问调用与微信 Webhook 均使用 Spring 内置的 `RestClient`,不引额外依赖)
3. 建立 `com.studyexam` 下的包结构(见 `01-architecture.md` 第一节)。
4. 写 `application.yml` 与 `application-dev.yml`,`ddl-auto: validate`,`flyway.enabled: true`。
5. 实现 `Result`、`PageResult`、`ErrorCode`、`BusinessException`、`GlobalExceptionHandler`。
6. 写 `WebConfig`,开发环境放开 CORS(`http://localhost:5173`)。

**验收标准**

- `mvn spring-boot:run -Dspring-boot.run.profiles=dev` 能启动,且 `ddl-auto: validate` 不报错(此时没有实体,应能正常启动)。
- `curl localhost:8080/actuator/health` 返回 `{"status":"UP"}`。
- `http://localhost:8080/swagger-ui.html` 能打开。
- 故意请求一个不存在的路径,返回的是统一格式的错误响应而不是 Whitelabel 页面。

---

## 阶段 1:科目与章节

**要做的事**

1. Flyway `V1__create_subject_and_chapter.sql`(直接抄 `02-data-model.md`)。
2. `Subject` / `Chapter` 实体、Repository、Service、Controller、DTO。
3. 实现 `02-api-spec.md` 第二节的全部接口。
4. `GET /subjects` 要带上 `questionCount`、`dueReviewCount`、`masteredCount`——这三个数字用一条聚合 SQL 查出来,不要在 Java 里循环查(会 N+1)。
5. 删除科目时若有题目,抛 `BusinessException`,提示先清空。

**验收标准**

- 能通过 Swagger UI 完整走通:建科目 → 建章节 → 查列表 → 改 → 删。
- 建四条初始数据:马原 `03709` / `#E5484D`、史纲 `03708` / `#F5A524`、习概 `15040` / `#2F6FED`、英语(专升本)`13000` / `#12A150`。
- 英语科目的 `examSlot` 填 `D2_PM`。
- 列表接口的 `questionCount` 在题库为空时返回 0 而不是 null。

---

## 阶段 2:题库核心

**要做的事**

1. Flyway `V2__create_question_and_paper.sql`。
2. `Question` 实体。`options` / `answer_points` 用 JPA 的 `@Convert` 或 `AttributeConverter` 映射 JSON 列(建议用 `hypersistence-utils` 的 `JsonType`,省事)。
3. 题目 CRUD 接口 + 全部参数校验规则(见 `03-api-spec.md` 第三节)。
4. 题目列表支持按 `subjectId` / `chapterId` / `type` / `keyword` / `sourceYear` 筛选与分页。
5. 删除题目时,级联删除其 `answer_record` 与 `review_item`(用 `ON DELETE CASCADE` 或 Service 里显式处理,选一种并保持一致)。

**验收标准**

- 用 Swagger 建一道单选题和一道简答题,都能建成功。
- 建一道单选题但 `options` 传空数组 → 返回 `code = 1001` 且提示明确指出是哪一项不合法。
- 建一道简答题但传了 `answer` 字段 → 被拒绝。
- 建一道多选题 `answer = "DBA"` → 被规范化为 `"ABD"` 或直接拒绝,行为明确且有一致性。
- 中文关键词全文检索能命中(需确认 MySQL `ngram_token_size` 已生效)。

---

## 阶段 3:批量导入 ★

**要做的事**

1. 实现 `QuestionImportService`,支持两种入口:上传 JSON 文件、直接 POST JSON body。
2. 按 `03-api-spec.md` 第五节的格式解析:先按 `subjectCode` 找科目,再按 `chapterName` 找章节,找不到章节则 `chapterId` 留空。
3. **逐条容错**:单条校验失败跳过并记录 `{index, reason}`,其余照常入库。
4. 若带 `paper` 字段,同时创建试卷与 `paper_question` 关联(按 `sectionName` 分组,组内按文件顺序编 `sort_order`)。
5. 整个导入在一个事务里;**若失败条目数超过总数的一半,整体回滚**并报错——这通常说明文件格式根本不对,不该把垃圾写进库。

**验收标准**

- 用一份 40 题的 JSON 导入,返回 `total: 40, imported: N, skipped: M, errors: [...]`。
- 故意在第 12 条漏掉 `answer`,导入仍成功,`errors` 里明确指出第 12 条及原因。
- 导入后题目列表能按新科目筛选出来,试卷详情能看到 40 道题的原始顺序与大题名。
- 重复导入同一份文件,产生的题目是重复的——**这一点接受**,不做去重(人工录入场景下重复导入更容易被发现;若日后需要,再加 `stem` 哈希去重)。

---

## 阶段 4:练习与判分

**要做的事**

1. Flyway `V3__create_practice_and_answer.sql`。
2. `SessionGenerator`:实现 `DAILY` / `RANDOM` / `CHAPTER` / `MOCK` / `REVIEW` 五种组题模式(规则见 `01-architecture.md` 第三节)。
3. `POST /practice/sessions`:创建场次 + 返回题目。**返回的题目 DTO 不得包含答案与解析。**
4. `POST /practice/sessions/{id}/answers`:客观题自动判分,写入 `answer_record`;**同一场次同一题重复提交返回已有记录**(靠唯一索引 + 捕获异常或先查后写,保证幂等)。
5. 主观题自评接口。
6. `POST /practice/sessions/{id}/finish`:汇总本轮成绩,更新 `practice_session` 与当天的 `study_log`。
7. 若已存在 `ONGOING` 场次,创建新场次时在响应里带上 `ongoingSessionId`。

**验收标准**

- 开始一次 `RANDOM` 练习,响应中**搜不到 `answer` 字段**(用 `grep` 检查原始 JSON)。
- 同一道题提交两次,数据库 `answer_record` 只有一行。
- 答错一题后,`review_item` 表出现该题记录。
- 结束练习后 `practice_session.status = FINISHED`、`score` 与 `correct_count` 正确。
- `study_log` 当天记录的 `question_count`、`correct_count`、`study_seconds` 被正确累加。

---

## 阶段 5:复习排期(SM-2)

**要做的事**

1. Flyway `V4__create_review_and_stats.sql`。
2. `Sm2Scheduler` 实现三态排期逻辑(见 `01-architecture.md` 第三节):

| 作答情况 (`confidence`) | 处理 |
|---|---|
| `WRONG` | `stage = 0`,`interval = 1`,`wrong_count++`,`correct_streak = 0`,`ease_factor -= 0.2`(下限 1.30) |
| `UNSURE` | `stage` 不变,`interval = 1`,`next_review_at = 今天 + 1 天`,`wrong_count++`,`correct_streak = 0` |
| `SURE` | `correct_streak++`,`stage++`,`interval = (stage == 1 ? 1 : round(interval * easeFactor))`;`correct_streak >= 3` 时 `status = MASTERED`(连续答对三次即移出错题本) |

> **入队规则**:只有 `WRONG` / `UNSURE` 会把一道新题写进 `review_item`;`SURE` 只推进已经在队列里的题。
> 错题本只收没答对的题,不会出现「错 0 次」的条目(旧数据由 V9 迁移清理)。

3. **间隔上限 60 天**,`ease_factor` 上下限 `1.30—3.00`。
4. `GET /review/due-summary` 与 `GET /review/items`(支持 `subjectId` + `type` 筛选)。
5. 手动标记已掌握、移出错题本两个接口。
6. `GET /review/type-counts?subjectId=&status=` 给「按科目 / 题型刷题」的下拉用。

**验收标准**

- 写单元测试覆盖 `Sm2Scheduler`,至少六个用例:首次答错、连续答对时阶段推进、答对但没把握时 `interval` 回到 1、连对满 3 次后 `status = MASTERED`、连对被打断后重新计数、`ease_factor` 不越界。

**间隔推演的基准(以初始 `interval = 1`、`ease_factor = 2.5` 为例,测试断言照这个写)**:

| 第几次作答 | `confidence` | 作答后 `stage` | 作答后 `interval` | `next_review_at` |
|---|---|---|---|---|
| 1 | `WRONG` | 0 | 1 | 明天 |
| 1 | `UNSURE` | 0 | 1 | 明天 |
| 1 | `SURE` | 1 | 1 | 明天 |
| 2 | `SURE` | 2 | `round(1 × 2.5) = 3` | 3 天后 |
| 3 | `SURE` | 3 | `round(3 × 2.5) = 8` | 连对满 3 次 → 标记 `MASTERED`,移出队列 |

「连对三次出队」看的是 `correct_streak`,不是 `stage`。`UNSURE`(主观题只答到部分要点、或未自评兜底)
会让 `correct_streak` 清零并计一次未答对,所以「没把握」的作答不会把题推进出队;
出队只把 `status` 改成 `MASTERED`,该题仍能在错题本「已掌握」里看到,之后再答错会回到 `LEARNING`。
错题本列表按 `wrong_count DESC` 排序,错得最多的排最前面;题型筛选用 `?type=`,非法值直接报错而不是静默返回全部。
只有 `WRONG` / `UNSURE` 会把新题写进 `review_item`,答对(`SURE`)只推进已在队列里的题。

- `GET /review/due-summary` 返回的数字与 `review_item` 表里 `next_review_at <= now()` 的行数一致。

---

## 阶段 6:统计

**要做的事**

1. `StatsService` 实现 `03-api-spec.md` 第六节的四个统计接口。
2. 复杂统计用 `@Query` 写原生 SQL(不要用 Java 循环算)。
3. 连续打卡天数:从今天往前遍历 `study_log`,遇到缺失日期即停止计数。
4. 近 N 天趋势:**缺失的日期要补 0**,否则前端折线图会断。

**验收标准**

- 造一批测试数据后,`/stats/overview` 的数字与手算一致。
- `/stats/trend?days=30` 恰好返回 30 条,没数据的日期 `questionCount = 0` 且日期连续。
- 正确率统计**只含客观题**,主观题不进分母。

---

## 阶段 7:定时提醒(微信通道)★ 提醒刚需

**要做的事**

1. Flyway `V5__create_setting_notify_ai.sql`。
2. `NotifySender` 接口 + 两个实现:`WeComSender`(企业微信群机器人 Webhook)、`ServerChanSender`(Server酱),按 `user_setting.notify_channel` 选择。
3. `ReminderScheduler`:

   - 每分钟跑一次 `@Scheduled(cron = "0 * * * * *")`。
   - 早间提醒:当前时间落在 `reminder_time` 的 ±1 分钟内,且 `reminder_log` 当天无 `MORNING` 记录 → 查今日到期题数,组装文案后经 `NotifySender` 发送,写 `reminder_log`。
   - 晚间提醒:同理,条件是 `evening_reminder_enabled` 且当天 `study_log` 无记录 → 发「今天还没打卡,做 5 题也算」。
   - 到期题数为 0 时不发早间提醒(避免无意义打扰)。
   - 发送失败时把失败原因写进 `reminder_log.fail_reason`,不影响其他任务。

4. 提醒文案格式(**必须带具体数字与跳转链接**):

   - 早间:`今日 12 题待复习,其中《马原》5 题、《英语》7 题` + 链接 `<app-base-url>/practice/daily`
   - 晚间:`今天还没打卡,做 5 题也算 —— 已连续 6 天` + 链接 `<app-base-url>/`

5. 接口:`GET /settings`、`PUT /settings`(密钥掩码处理)、`POST /notify/test`、`GET /notify/logs`。

**验收标准**

- `POST /notify/test` 能在微信里收到一条真实消息。
- 把 `reminder_time` 改成两分钟后,到点自动收到早间提醒,且**当天只发一条**(`reminder_log` 唯一键生效)。
- 故意把 Webhook 改错,发送失败不抛异常,`reminder_log` 记录了失败原因。
- 到期题数为 0 时到点不发。
- 当天已有 `study_log` 时,晚间提醒不发。

---

## 阶段 8:腾讯云部署

**要做的事**

1. 服务器环境:装 JDK 17 与 MySQL 8(或 Docker Compose 一键起),建库 `study_exam`(utf8mb4)。
2. 前端 `npm run build` 产物拷到 `src/main/resources/static/`。
3. 写一个 `SpaForwardController`,把非 `/api/**`、非静态资源的路径转发到 `index.html`(否则刷新页面 404)。
4. 打包 `mvn package` 得到单个 jar,上传服务器;用 systemd 服务守护(开机自启、崩溃自动拉起)。
5. 配置安全组放行应用端口(建议不用 80/443,用 8080 等高端口避免备案要求);数据库不对公网开放。
6. 设置页填写 `app-base-url` 为 `http://<服务器IP>:8080`,保证提醒里的跳转链接可达。
7. 写 `deploy.sh`:本地打包 → scp 上传 → 服务器重启服务,一条命令完成更新。

**验收标准**

- 手机在外网(不连家里 WiFi)访问服务器地址,能完整使用应用。
- 在任意路由刷新不 404。
- 重启服务器后应用自动启动,提醒任务正常触发。
- 数据库只能从服务器本机访问,公网端口不可连。

---

## 阶段 9:AI 辅导 ★

**要做的事**

1. `AiConfig`:从配置读 DashScope API Key、base-url、模型名、超时。
2. `PromptTemplates`:四种场景的 System Prompt 集中定义(解析 / 变式题 / 口诀 / 串讲),按「精简、考点、60 分」原则撰写;讲解类超过 120 字视为不合格输出。
3. `AiService`:

   - `explain(questionId, userAnswer, force)`:命中缓存直接返回;否则组装 Prompt(题干 + 选项 + 正确答案 + 解析 + 用户错选)调用千问,成功后把结果写入 `question.ai_analysis`。
   - `variant(questionId)`:生成同考点变式题,返回题目 JSON(**不自动入库**,由前端确认后再保存)。
   - `mnemonic(knowledgePoint)`:生成记忆口诀。
   - `digest(subjectId, force)`:汇总该科高频考点为「必背 N 点」,写入 `ai_digest`(同科目覆盖更新)。

4. 调用记录:每次真实调用写 `ai_call_log`(场景、模型、token 数、耗时、成败)。
5. 失败处理:超时 15 秒;异常统一映射为 `code = 6001`,返回可读原因;**绝不影响练习主流程**。
6. 接口:按 `03-api-spec.md` 第八节实现全部 `/ai/*`。

**验收标准**

- 点「问老师」首次生成讲解,再次点击直接命中缓存(`cached: true`),不产生新的 `ai_call_log`。
- 带 `userAnswer` 的请求,讲解里能指出「你选的 C 混淆了…」。
- 把 API Key 改成错的,接口返回 `code = 6001` 与可读原因,前端降级显示原解析,练习流程不受影响。
- 生成的讲解抽查 20 题,均不超过 120 字。
- `/ai/usage?days=30` 的 token 数与 `ai_call_log` 汇总一致。

---

## 实现顺序的依赖关系

```
阶段0 骨架
  └─ 阶段1 科目章节
       └─ 阶段2 题库核心
            ├─ 阶段3 批量导入   ← 有了它才能真正开始录题
            └─ 阶段4 练习判分
                 └─ 阶段5 复习排期
                      └─ 阶段6 统计
                           └─ 阶段7 定时提醒(微信通道)
                                └─ 阶段8 腾讯云部署
                                     └─ 阶段9 AI 辅导
```

**给用户的实际建议**:阶段 1—3 做完就能开始录真题了,不必等全部完成。阶段 7 提醒和阶段 8 部署做完,每天的学习节奏就立起来了;AI 辅导(阶段 9)放在最后,因为它依赖稳定的题库和练习数据。
