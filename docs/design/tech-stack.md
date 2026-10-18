# 技术选型

> 本文是整个项目的技术决策记录(ADR)。任何实现不得偏离本文选型;确需变更时先改本文,再改代码。

决策日期:2026-10-14

---

## 一、总览

| 层次 | 选型 | 版本基线 |
|---|---|---|
| 前端框架 | Vue 3(组合式 API + `<script setup>`) | 3.4+ |
| 前端构建 | Vite | 5+ |
| 前端语言 | TypeScript | 5+ |
| 前端路由 | Vue Router | 4+ |
| 前端状态 | Pinia | 2+ |
| 前端 UI 库 | Element Plus | 2.5+ |
| 前端 HTTP | Axios | 1+ |
| 前端 PWA | vite-plugin-pwa | 0.19+ |
| 后端语言 | Java | 17(LTS) |
| 后端框架 | Spring Boot | 3.2+ |
| 后端 Web | Spring Web MVC | 随 Boot |
| 后端持久层 | Spring Data JPA(Hibernate) | 随 Boot |
| 后端迁移 | Flyway | 9+ |
| 后端校验 | Spring Validation(Jakarta) | 随 Boot |
| 后端提醒 | 微信通道(企业微信群机器人 / Server酱 Webhook) | — |
| 大模型 | 通义千问(DashScope OpenAI 兼容接口) | qwen-plus |
| 部署 | 腾讯云轻量服务器(至 2027 年 2 月) | — |
| 数据库 | MySQL | 8.0 |
| 后端构建 | Maven | 3.9+ |
| 前端构建工具链 | npm | 10+ |

---

## 二、前端选型

### Vue 3 而不是 React

- 单文件组件把模板、逻辑、样式收在一个文件里,对一个自用项目来说维护成本最低。
- `<script setup>` 的写法接近普通函数,不需要理解 hooks 的依赖数组、闭包陷阱。
- 中文资料与 Element Plus 生态完整,遇到问题好查。

### Vite 而不是 Webpack

开发服务器冷启动基本在 1 秒内,改代码热更新几乎无感。这是每天都要用的东西,值得选快的。

### TypeScript

前后端分离时最容易出的错是「后端字段是 `chapterId`,前端写成 `chapter_id`」。TS 配合下面第三节的接口类型生成约定,能在编译期就报错,而不是运行时白屏。

### Element Plus

自用工具不需要设计出彩,需要的是**少写代码**。表格、表单、日期选择、进度条这些高频组件 Element Plus 都有。视觉风格通过 `design/ui-design-spec.md` 里的 CSS 变量做一层覆盖,避免看起来像官方 demo。

### 不引入的方案及原因

- **Tailwind CSS** —— 与 Element Plus 的样式体系会打架,自用项目不值得为此增加心智负担。
- **Nuxt** —— 不需要 SSR 和 SEO,纯 SPA 已足够。
- **uni-app** —— 虽然能一套代码出小程序,但本项目已明确不做小程序(小程序只能被动响应、无法在无人操作时主动提醒,是当初就被排除的选项)。

---

## 三、前后端接口的契约

后端以 Spring Boot 注解生成 OpenAPI(Swagger)描述,前端用 `openapi-typescript` 从描述文件生成 TS 类型。

```
后端启动 → /v3/api-docs → openapi-typescript → frontend/src/api/generated.ts
```

好处:后端改字段,前端类型重新生成就会编译报错,不会出现「接口悄悄改了前端不知道」的情况。

约定:
- 后端**必须**为每个 Controller 写 `@Operation` / `@Schema` 注解,否则生成的类型是 `any`。
- 前端**不得**手写接口返回类型;必须从 `generated.ts` 引用。

---

## 四、后端选型

### Spring Boot 3 + Java 17

- 用户主栈是 Java/Kotlin,选它可以直接复用已有知识,同时这个项目本身就是软件工程专业课的实践素材。
- Spring Boot 3 要求 Java 17 起步,是当前 LTS,长期支持。
- 相比 Node/FastAPI,代价是启动稍重、内存占用稍高,但本机自用完全无所谓。

### Spring Data JPA 而不是 MyBatis

本项目查询以「按科目/章节筛选 + 分页」为主,几乎没有复杂多表联查,用 JPA 的 `JpaRepository` 可以省掉大量模板代码。

唯一需要手写 SQL 的地方是统计报表(正确率趋势、章节掌握度),用 `@Query` 写原生 SQL 即可。

### Flyway 管理建表脚本

不用 Hibernate 的 `ddl-auto: update` 自动建表——那样无法追踪表结构变更历史,出了问题也回不去。用 Flyway:

```
backend/src/main/resources/db/migration/
├── V1__create_subject_and_chapter.sql
├── V2__create_question_and_paper.sql
├── V3__create_practice_and_answer.sql
├── V4__create_review_and_stats.sql
└── V5__create_setting_notify_ai.sql
```

### 微信通道实现「提醒刚需」

这是后端存在的最重要理由。用 `@Scheduled` 每分钟检查一遍,到点就通过微信通道发送:

```java
@Scheduled(cron = "0 * * * * *")   // 每分钟
public void dispatchReminders() { ... }
```

通道选择:企业微信群机器人 Webhook(或 Server酱 SendKey),服务端直调、即时到达微信。相比浏览器推送,免 HTTPS、免域名备案,国产手机到达率高得多。

提醒内容必须是「具体任务」而不是「该学习了」:

- ✅「今日 12 题待复习,其中《操作系统》5 题」
- ❌「今天也要加油学习哦」

### 通义千问实现「AI 老师」

AI 能力通过 DashScope 的 OpenAI 兼容接口调用(`https://dashscope.aliyuncs.com/compatible-mode/v1`),用 Spring 的 `RestClient` 发 POST 即可,不引入重型 SDK。System Prompt 固定为「自考冲刺辅导老师」人设(详见 `study-exam/backend/01-architecture.md` 第五节),讲解输出限制在 120 字以内。API Key 存服务端环境变量,任何接口不返回。四种场景(精简解析 / 变式题 / 记忆口诀 / 考前串讲)与接口约定见 `study-exam/backend/03-api-spec.md` 第八节。

---

## 五、数据库选型

### MySQL 8

| 候选 | 结论 |
|---|---|
| **MySQL 8** | ✅ 选定。与 Spring Boot 组合最成熟,资料最多,一台普通机器跑得起 |
| H2 / SQLite | ❌ 更轻,但报表 SQL 语法受限(`JSON_TABLE`、窗口函数支持弱),且日后想挪到别的机器不方便 |
| PostgreSQL | 技术上更优(JSONB 更强),但对本项目没有决定性优势,且用户更可能已有 MySQL 环境 |

### 字符集与排序规则

建库时必须显式指定,否则中文会出现乱码:

```sql
CREATE DATABASE study_exam
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

### JSON 字段的使用

题目的选项(`question.options`)、主观题的参考答案要点(`question.answer_points`)用 MySQL 的 `JSON` 类型存。原因:不同题型的选项结构差异大(单选 4 项、多选 5 项、判断题 2 项、主观题无选项),拆成独立的选项表会让查询和录入都变复杂。

代价是不能直接用 SQL 对选项做条件筛选——但本项目不需要这个能力。

---

## 六、部署形态(腾讯云)

```
┌──────────────────────────────┐
│  腾讯云轻量服务器(至2027.2)  │
│                               │
│  MySQL 8    :3306(仅本机)    │
│  Spring Boot :8080            │
│  ├─ 托管前端构建产物(dist)   │
│  ├─ 定时任务:每分钟检查提醒   │
│  └─ 调用千问 API(AI 辅导)    │
└──────────────┬───────────────┘
               │ http://<服务器IP>:8080
      手机(外网任意网络)/ 电脑浏览器
               │
       提醒走微信通道(服务端直调 Webhook)
```

- 开发期:本机前端 `npm run dev`(:5173)+ 本地后端(:8080),Vite 配 `proxy` 转发 `/api`;数据库用本机 MySQL。
- 上线期:前端 `npm run build` 产物拷进后端 `src/main/resources/static/`,打包 jar 上传服务器,以 systemd 守护(jar 内同时含前后端)。
- 手机访问:任意网络直接访问 `http://<服务器IP>:8080`,不再要求同一 WiFi。
- 端口选择:用 8080 等高端口,**避开 80/443 以规避大陆服务器的 ICP 备案要求**;数据库端口不对公网开放。
- 提醒链路不依赖浏览器安全上下文:服务端直调微信 Webhook,免 HTTPS。

**到期与迁移**:服务器 2027 年 2 月到期。数据结构全部由 Flyway 脚本定义,数据可用 `mysqldump` 导出,迁移到新服务器或迁回本机都只需重复「建库 → 跑迁移 → 导入数据」三步。

---

## 七、明确排除的方案

| 方案 | 排除原因 |
|---|---|
| 微信小程序 | 只能被动响应,无法在无人操作时主动提醒,而提醒是刚需 |
| 原生安卓 App(Kotlin) | 初期选定后又放弃:无法在电脑上录题,且多端要写两遍 |
| 浏览器 Web Push 通知 | 依赖 HTTPS 与域名备案,国产手机到达率低;改用微信通道后降为可选能力 |
| 本地长期运行后端 | 电脑关机时提醒失效,违背「提醒刚需」,故改为云服务器常驻 |
| 自建/本地部署开源大模型 | 硬件成本高、维护重;已有的千问 TokenPlan 完全够用 |
| 用户注册/登录体系 | 只有一个用户,用一张 `user` 表存一条记录,不做鉴权,开箱即用 |
| 排行榜/社交功能 | 单人使用没有意义 |
| 微服务拆分 | 单机应用拆微服务是自找麻烦 |
| Redis 缓存 | 数据量在万级以内,MySQL 直查足够快 |
