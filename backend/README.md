# 后端(study-exam-backend)

Java 17 + Spring Boot 3.2 + Spring Data JPA + Flyway + MySQL 8。

## 一、跑起来

前置:JDK 17、Maven 3.9+、本机 MySQL 8(root / 123456)。

```bash
# 1. 建库(只需一次)
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS study_exam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 启动(表结构由 Flyway 自动建,不需要手工执行任何 SQL)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

启动后:

| 地址 | 用途 |
|---|---|
| http://localhost:8080/actuator/health | 健康检查,应返回 `{"status":"UP"}` |
| http://localhost:8080/swagger-ui.html | 接口文档,可在这里直接调接口 |
| http://localhost:8080/v3/api-docs | OpenAPI 描述,前端 `npm run gen:api` 用它生成 TS 类型 |

数据库连接配置在 [`application-dev.yml`](src/main/resources/application-dev.yml),已按本机
`root / 123456` 预设。改密码只需动这一个文件。

## 二、AI 配置(通义千问 / 千问 TokenPlan)

复用已有的千问 TokenPlan,通过 DashScope 的 **OpenAI 兼容接口**调用,不引重型 SDK。
配置在 [`application.yml`](src/main/resources/application.yml) 的 `study-exam.ai` 段:

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `api-key` | `${DASHSCOPE_API_KEY:}` | **留空也能启动**,AI 接口会返回 `code = 6001` |
| `base-url` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | OpenAI 兼容入口 |
| `chat-path` | `/chat/completions` | 实际请求路径 |
| `model` | `${DASHSCOPE_MODEL:qwen-plus}` | 按 TokenPlan 覆盖的模型调整 |
| `timeout-seconds` | `15` | 单次调用超时 |
| `max-explain-chars` | `120` | 讲解类输出的字数硬约束 |

**填 API Key 的方式**(二选一,推荐第一种):

```bash
# 环境变量,当前终端有效
export DASHSCOPE_API_KEY=sk-你的key
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

或者直接把 key 写进 `application-dev.yml`,但**不要提交到版本库**。
Key 在阿里云百炼(DashScope)控制台创建:https://bailian.console.aliyun.com/ → API-KEY。

安全约定:Key 只存服务端,**任何接口都不返回**。

## 三、工程结构

按业务域分包,不建 `controller/`、`service/` 这类顶层技术包。

```
src/main/java/com/studyexam/
├── StudyExamApplication.java
├── common/      Result / PageResult / ErrorCode / BusinessException / GlobalExceptionHandler
├── config/      WebConfig(CORS) / OpenApiConfig / SchedulingConfig / AiConfig / AiProperties
├── subject/     科目(实体 / Repository / Service / Controller / DTO)
├── chapter/     章节
├── question/    题目 + 题型枚举 + 类型相关校验
├── paper/       试卷与试卷题目关联
├── review/      复习队列统计(阶段 5 落地)
├── stats/       统计
├── setting/     用户设置与「表是否已建立」探测
└── ai/          AI 辅导(Prompt 模板已就位,调用在阶段 9 落地)
```

## 四、数据库

表结构全部由 [`src/main/resources/db/migration/`](src/main/resources/db/migration/) 下的
Flyway 脚本定义,`ddl-auto: validate` 只校验、绝不改表。

| 脚本 | 内容 | 状态 |
|---|---|---|
| `V1__create_subject_and_chapter.sql` | user / subject / chapter + 四条初始科目 | 已建 |
| `V2__create_question_and_paper.sql` | question / paper / paper_question | 已建 |
| `V3__create_practice_and_answer.sql` | practice_session / answer_record | 阶段 4 |
| `V4__create_review_and_stats.sql` | review_item / study_log | 阶段 5 |
| `V5__create_setting_notify_ai.sql` | user_setting / reminder_log / ai_call_log / ai_digest | 阶段 7 |

> 表后用到的表还没建时,相关指标返回 `0` 而不是报错(见 `setting/TableAvailability`),
> 因此「今日页」这类跨阶段接口在任何阶段都能正常返回。

## 五、约定

- **统一响应**:所有接口返回 `Result<T>`,`code == 0` 表示成功;HTTP 状态码同时保持语义。
  错误码表见 `common/ErrorCode`。
- **字段名**:实体字段与 `docs/study-exam/backend/02-data-model.md` 一一对应,前端字段名不得自行改名。
- **取题安全**:练习取题接口**不得**返回 `answer` / `answer_points` / `analysis`。
- **AI 降级**:AI 调用失败一律映射为 `code = 6001`,绝不影响练习主流程。