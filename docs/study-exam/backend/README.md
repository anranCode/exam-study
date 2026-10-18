# 学习中心 · 后端

> Spring Boot 3 + MySQL 8 单体应用。负责题库存储、练习记录、复习排期计算、统计报表,以及**定时推送提醒**。

---

## 文档索引

| 文件 | 内容 | 什么时候读 |
|---|---|---|
| `01-architecture.md` | 工程结构、分层约定、依赖、配置、启动方式 | 动手写代码前 |
| `02-data-model.md` | 全部表结构、字段含义、索引、建表 SQL | 写实体类前 |
| `03-api-spec.md` | REST 接口清单、请求/响应格式、题库导入格式 | 写 Controller 前 |
| `04-features.md` | 按阶段拆解的功能实现清单 + 验收标准 | 排开发计划时 |

---

## 一句话职责

后端不处理任何界面逻辑。它的五件事:

1. **存题库** —— 题目、试卷、章节的增删改查与批量导入。
2. **记练习** —— 每次练习与每道题的作答记录。
3. **算排期** —— 错题按 SM-2 遗忘曲线算法计算下次复习日期。
4. **发提醒** —— 定时任务检查到期复习与打卡情况,通过微信通道(企微机器人 / Server酱)发送。
5. **请老师** —— 按需调用通义千问,生成精简解析、变式题、记忆口诀与考前串讲。

---

## 技术栈

| 项 | 选型 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2+ |
| Web | Spring Web MVC |
| 持久层 | Spring Data JPA(Hibernate) |
| 数据库迁移 | Flyway |
| 数据库 | MySQL 8.0(utf8mb4) |
| 参数校验 | Spring Validation(Jakarta) |
| 定时任务 | Spring `@Scheduled` |
| 提醒 | 微信通道(企微机器人 / Server酱) |
| 大模型 | 通义千问(DashScope 兼容接口) |
| 接口文档 | springdoc-openapi(Swagger UI) |
| 构建 | Maven |
| 端口 | `8080` |

---

## 快速启动

```bash
# 1. 建库
mysql -u root -p -e "CREATE DATABASE study_exam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 配置数据库账号(改 src/main/resources/application-dev.yml)
#    spring.datasource.username / password

# 3. 启动(Flyway 会自动建表)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 验证
curl http://localhost:8080/actuator/health      # {"status":"UP"}
open http://localhost:8080/swagger-ui.html      # 接口文档
```

---

## 硬性约定(不可违反)

1. **所有接口统一前缀 `/api/v1`**,响应体统一包一层 `Result<T>`。
2. **不得开启 `spring.jpa.hibernate.ddl-auto=update`**。建表只能通过 Flyway 脚本。
3. **不得在 Controller 里写业务逻辑**,Controller 只做参数接收与响应封装。
4. **不得返回 JPA 实体给前端**,一律转成 DTO。
5. **不得对外暴露主键自增以外的内部字段**(如 `deleted`、`version`)。
6. 所有时间字段用 `LocalDateTime`,数据库用 `DATETIME`,JSON 序列化格式 `yyyy-MM-dd HH:mm:ss`。
7. 单用户系统,**不做鉴权**,但所有涉及用户的表仍保留 `user_id` 字段,默认值 `1`。
