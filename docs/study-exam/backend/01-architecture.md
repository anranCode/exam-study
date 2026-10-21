# 后端架构

---

## 一、工程结构

```
backend/
├── pom.xml
├── src/main/java/com/studyexam/
│   ├── StudyExamApplication.java
│   ├── common/                      # 通用基础设施
│   │   ├── Result.java              # 统一响应包装
│   │   ├── PageResult.java          # 分页响应
│   │   ├── BusinessException.java   # 业务异常
│   │   ├── ErrorCode.java           # 错误码枚举
│   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice
│   ├── config/
│   │   ├── WebConfig.java           # CORS、静态资源、日期格式
│   │   ├── JacksonConfig.java
│   │   ├── OpenApiConfig.java
│   │   ├── SchedulingConfig.java    # @EnableScheduling
│   │   ├── NotifyConfig.java        # 微信通道配置注入
│   │   └── AiConfig.java            # 千问 API 配置注入
│   ├── subject/                     # 科目
│   │   ├── SubjectController.java
│   │   ├── SubjectService.java
│   │   ├── SubjectRepository.java
│   │   ├── Subject.java             # 实体
│   │   └── dto/…
│   ├── chapter/                     # 章节
│   ├── question/                    # 题目(含批量导入、文本解析)
│   │   ├── QuestionController.java
│   │   ├── QuestionService.java
│   │   ├── QuestionImportService.java
│   │   ├── TextParseService.java    # 粘贴文本自动拆题
│   │   └── …
│   ├── paper/                       # 试卷
│   ├── practice/                    # 练习场次与作答
│   │   ├── PracticeController.java
│   │   ├── PracticeService.java
│   │   ├── SessionGenerator.java    # 组题策略(抽题算法)
│   │   └── GradingService.java      # 判分
│   ├── review/                      # 错题本与复习排期
│   │   ├── ReviewController.java
│   │   ├── ReviewService.java
│   │   └── Sm2Scheduler.java        # SM-2 算法实现
│   ├── stats/                       # 统计
│   ├── notify/                      # 提醒(微信通道)
│   │   ├── NotifyController.java
│   │   ├── NotifySender.java        # 接口;WeComSender / ServerChanSender 两实现
│   │   ├── ReminderScheduler.java   # @Scheduled 定时触发
│   │   └── dto/…
│   ├── ai/                          # AI 辅导
│   │   ├── AiController.java
│   │   ├── AiService.java
│   │   ├── PromptTemplates.java     # 各 AI 场景的 System Prompt
│   │   └── dto/…
│   └── setting/                     # 用户设置
└── src/main/resources/
    ├── application.yml              # 公共配置
    ├── application-dev.yml          # 开发环境(本地 MySQL)
    ├── db/migration/                # Flyway SQL 脚本
    └── static/                      # 前端构建产物拷到这里(上线用)
```

**按功能包分层,而不是按技术分层。** 不建 `controller/`、`service/`、`repository/` 这种顶层包——那样每加一个功能要改四个目录。按 `subject`、`question` 这样的业务域分包,每个包自带 Controller / Service / Repository / Entity / DTO。

---

## 二、分层职责

```
Controller  只做三件事:接参数、调 Service、包 Result
    ↓       禁止:写业务判断、直接调 Repository、返回实体
Service     业务逻辑、事务边界(@Transactional)、实体↔DTO 转换
    ↓
Repository  Spring Data JPA 接口,复杂的统计查询用 @Query 写 SQL
    ↓
MySQL       表结构由 Flyway 管理
```

### 统一响应

```java
public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> ok(T data)          { return new Result<>(0, "success", data); }
    public static <T> Result<T> ok()                { return ok(null); }
    public static <T> Result<T> fail(ErrorCode ec)  { return new Result<>(ec.getCode(), ec.getMessage(), null); }
}
```

`code == 0` 表示成功。HTTP 状态码同时保持语义(400 参数错、404 不存在、500 服务错),前端两者都可以判断。

### 错误码

| 码 | 含义 |
|---|---|
| 0 | 成功 |
| 1001 | 参数校验失败 |
| 2001 | 科目不存在 |
| 2002 | 章节不存在 |
| 2003 | 题目不存在 |
| 2004 | 试卷不存在 |
| 3001 | 练习场次不存在 |
| 3002 | 该场次已结束 |
| 4001 | 导入文件格式错误 |
| 5001 | 推送订阅无效或已失效 |
| 9999 | 未知异常 |

---

## 三、关键设计

### 1. 组题策略(SessionGenerator)

`POST /api/v1/practice/sessions` 传 `mode`,由 `SessionGenerator` 决定抽哪些题:

| mode | 抽题规则 |
|---|---|
| `DAILY` | 到期复习题(优先)+ 补足新题的差额,总数 = 用户设置的 `dailyQuestionCount` |
| `RANDOM` | 指定科目/章节范围内随机 N 题,不看复习排期 |
| `CHAPTER` | 指定章节内随机 N 题 |
| `MOCK` | 按指定 `paperId` 的题目顺序与分值完整出一套 |
| `REVIEW` | 只抽 `next_review_at <= now()` 的题 |

抽题用 MySQL 的 `ORDER BY RAND() LIMIT n`。题量在万级以内性能可接受,不做优化。

### 2. 复习排期(Sm2Scheduler)

SM-2 算法的简化实现。每题维护 `stage`(复习阶段)、`interval_days`(间隔天数)、`ease_factor`(难度系数,初始 2.5)。

```
用户作答:
  答错(WRONG)         → stage = 0, interval = 1, next_review_at = 今天 + 1 天,
                        wrong_count++, correct_streak = 0, ease_factor -= 0.2
  没把握(UNSURE)      → stage 不变, interval = 1, next_review_at = 今天 + 1 天,
                        wrong_count++, correct_streak = 0
  答对且确定(SURE)    → correct_streak++, stage += 1
                        interval = (stage == 1) ? 1 : round(interval * easeFactor)
                        若 correct_streak >= 3 → 标记 MASTERED,移出复习队列
```

**错题本只收「没答对」的题**:`WRONG` / `UNSURE` 才会把一道新题放进 `review_item`;
`SURE` 只推进**已经在队列里**的题。否则做对过的新题也会进错题本,列表里出现「错 0 次」的条目,
和「错题本」的语义不符。

「连续答对三次即出队」是产品规则(`Sm2Scheduler.MASTER_STREAK = 3`),不看 `stage`。
出队只是把 `status` 置为 `MASTERED`、留在「已掌握」筛选里,不是删行——之后再答错会回到 `LEARNING`。
`UNSURE`(主观题只答到部分要点、或未自评的兜底)同样计一次未答对,并清零 `correct_streak`。

提交答案时前端传 `confidence`(`SURE` / `UNSURE` / `WRONG`)三态,而不是只有对错。
当前交互下 `UNSURE` 只来自「不知道」之外的没把握场景(主观题部分得分),答错一律由后端改写成 `WRONG`。

间隔天数上限 60 天,避免长间隔后再也见不到。

### 3. 判分(GradingService)

- **客观题**(单选、多选、判断、阅读判断):后端比对答案,直接判定正确与否。
- **主观题**(简答、论述、材料分析、写作):后端**不判分**,只返回参考答案要点列表;前端让用户勾选「我答到了哪几点」,提交勾选结果作为 `self_score`。这是自用系统最务实的做法——没有可靠的机器判分,人力自评反而最准。

`answer_record.self_score` 存勾选到的要点数量,`answer_record.total_points` 存要点总数。

### 4. 定时提醒(ReminderScheduler + 微信通道)

```java
@Scheduled(cron = "0 * * * * *")   // 每分钟检查一次
public void dispatch() {
    // 1. 取所有 reminder_enabled = true 的用户设置
    // 2. 当前时间落在 reminder_time ± 1 分钟内?
    // 3. 查该用户今日任务:到期复习题数 + 是否已打卡
    // 4. 有到期题 → 经 NotifySender 发「今日 N 题待复习,其中《X》M 题」+ 跳转链接
    //    无到期但未打卡 → 跳过主提醒
    // 5. 写 reminder_log,防止当天重复发送
}
```

配套的晚间提醒(可选):

```java
@Scheduled(cron = "0 * * * * *")
// evening_reminder_time 到点,且当天 study_log 无记录 → 发「今天还没打卡,做 5 题也算」,每天最多一次
```

`NotifySender` 是接口,有两个实现:`WeComSender`(企业微信群机器人 Webhook)与 `ServerChanSender`(Server酱 SendKey),按 `user_setting.notify_channel` 选择。发送失败(网络错误、Webhook 失效)时**写 `reminder_log.fail_reason` 并继续**,绝不抛出异常影响其他任务。

### 5. AI 辅导(AiService)

五种场景,统一走服务端调用千问(DashScope Anthropic 兼容接口,`RestClient` 发 POST,文本超时 45 秒):

| 场景 | 方法 | 缓存策略 |
|---|---|---|
| 精简解析 | `explain(questionId, userAnswer?, force?)` | 写入 `question.ai_analysis`,命中直接返回 |
| 题目解析生成 | `analysis(type, stem, answerPoints)` / `generateAndSave(questionId)` | 写入 `question.analysis`,不缓存,可重新生成 |
| 变式题 | `variant(questionId)` | 不缓存(每次都出新题),不入库,返回给前端确认 |
| 记忆口诀 | `mnemonic(knowledgePoint)` | 不缓存,前端可复制保存 |
| 考前串讲 | `digest(subjectId, force?)` | 写入 `ai_digest`,同科目覆盖更新 |

System Prompt 固定为「自考冲刺辅导老师」人设,核心约束:先点名考点 → 大白话讲对错原因 → 一句记忆提示 → 若带 `userAnswer` 则指出混淆点;讲解全文 **≤ 120 字**,不写学术引申,以 60 分及格为目标。主观题解析生成用另一套人设:根据参考答案要点输出「考点 + ①②③ 采分点 + 口诀」,**120—220 字、简洁好记**。Prompt 模板集中在 `PromptTemplates.java`,不在 Service 里拼字符串。

调用成功或失败都写 `ai_call_log`(场景、模型、token 数、耗时);异常统一映射为 `code = 6001`,**AI 服务故障绝不影响练习主流程**。

### 6. 幂等与防重复

- 提交答案接口按 `sessionId + questionId` 唯一,重复提交返回已有记录而不新建(表上加唯一索引)。
- 提醒记录用 `reminder_log` 表按 `user_id + date + type` 唯一,保证一天只发一次同类提醒。
- AI 生成接口不需要额外幂等:缓存命中直接返回,天然幂等。

---

## 四、配置

`application.yml`:

```yaml
spring:
  application:
    name: study-exam
  profiles:
    active: dev
  jpa:
    hibernate:
      ddl-auto: validate        # 只校验,绝不改表
    open-in-view: false
  flyway:
    enabled: true
    baseline-on-migrate: true
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai

server:
  port: 8080

study-exam:
  notify:
    channel: WECOM                        # WECOM / SERVERCHAN
    wecom-webhook: ${WECOM_WEBHOOK_URL}   # 企业微信群机器人 Webhook
    serverchan-key: ${SERVERCHAN_KEY}     # Server酱 SendKey(通道为 SERVERCHAN 时用)
    app-base-url: http://<服务器IP>:8080  # 提醒里的跳转链接前缀
  ai:
    api-key: ${DASHSCOPE_API_KEY}         # 通义千问 API Key
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen-plus
    timeout-seconds: 15
```

`application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/study_exam?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: root
    password: <改成你的密码>
    driver-class-name: com.mysql.cj.jdbc.Driver
logging:
  level:
    com.studyexam: DEBUG
```

**密钥与通道获取**:

1. **千问 API Key**:在阿里云百炼(DashScope)控制台创建,写进环境变量 `DASHSCOPE_API_KEY`。
2. **企业微信群机器人**:建一个只有自己的群 → 群设置 → 群机器人 → 添加 → 复制 Webhook 地址,写进 `WECOM_WEBHOOK_URL`;或注册 Server酱账号拿 SendKey。

以上密钥都只存服务端环境变量,不要提交到版本库,也不要通过任何接口返回。

---

## 五、测试约定

- Service 层必须有单元测试,重点覆盖 `Sm2Scheduler` 和 `SessionGenerator`(这两个是纯逻辑,最好测)。
- `PromptTemplates` 用快照测试(断言生成的 Prompt 包含题干、答案与用户错选)。
- Repository 的统计 SQL 用 `@DataJpaTest` 测。
- Controller 用 `@WebMvcTest` 只测参数校验与响应结构,不测业务。
- 不追求覆盖率数字,只测「算错了会影响学习效果」的逻辑。
