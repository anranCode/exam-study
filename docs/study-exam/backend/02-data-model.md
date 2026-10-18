# 数据库设计

> 本文是数据模型的**唯一准绳**。实体类字段名、前端字段名都必须与本文一致,不允许自行改名或加下划线/驼峰转换以外的变体。
>
> 数据库:MySQL 8.0 · 字符集 `utf8mb4` / `utf8mb4_unicode_ci` · 所有表引擎 `InnoDB`

---

## 一、实体关系总览

```
user ─┬─< practice_session ─< answer_record >─ question
      ├─< review_item ──────────────────────────┘
      ├─< study_log
      ├─< user_setting  (1:1)
      ├─< reminder_log
      └─< ai_call_log

subject ─┬─< chapter ─┬─< question
         │            └─< question
         └─< paper ─< paper_question >─ question
```

一句话理解:`subject` 是科目,`chapter` 是章节,`question` 是题目(挂在科目和章节下),`paper` 是整卷(通过 `paper_question` 关联题目),`practice_session` 是一次练习,`answer_record` 是这次练习里每道题的作答,`review_item` 是错题本与复习队列。

---

## 二、表清单

| 表名 | 中文名 | 说明 |
|---|---|---|
| `user` | 用户 | 只有一条记录 |
| `subject` | 科目 | 四门课 |
| `chapter` | 章节 | 科目的知识单元 |
| `question` | 题目 | 核心表 |
| `paper` | 试卷 | 一套完整真题/模拟卷 |
| `paper_question` | 试卷-题目 | 多对多关联 |
| `practice_session` | 练习场次 | 一次刷题 |
| `answer_record` | 作答记录 | 每道题的作答 |
| `review_item` | 复习队列 | 错题本 + 遗忘曲线排期 |
| `study_log` | 学习日志 | 每日汇总,用于打卡与统计 |
| `user_setting` | 用户设置 | 每日题量、提醒时间 |
| `reminder_log` | 提醒日志 | 防重复发送 |
| `ai_call_log` | AI 调用日志 | token 用量统计 |
| `ai_digest` | 考前串讲 | 每科一份冲刺清单 |

---

## 三、建表语句

### V1__create_subject_and_chapter.sql

```sql
-- 用户(单用户系统,保留表结构以便日后扩展)
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `username`    VARCHAR(64)  NOT NULL COMMENT '登录名,单用户固定为 default',
  `nickname`    VARCHAR(64)  NOT NULL DEFAULT '考生' COMMENT '显示名',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

INSERT INTO `user` (`id`, `username`, `nickname`) VALUES (1, 'default', '考生');

-- 科目
CREATE TABLE `subject` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `code`         VARCHAR(16)  NOT NULL COMMENT '课程代码,如 03709 / 13000',
  `name`         VARCHAR(128) NOT NULL COMMENT '科目全称',
  `short_name`   VARCHAR(32)  NOT NULL COMMENT '简称,界面显示用,如「马原」',
  `color`        VARCHAR(16)  NOT NULL DEFAULT '#2F6FED' COMMENT '标识色,见 ui-design-spec',
  `exam_date`    DATE         NULL COMMENT '考试日期',
  `exam_slot`    VARCHAR(16)  NULL COMMENT '考试场次:A_DAY_BEFORE_NOON 等,见下方取值',
  `total_score`  INT          NOT NULL DEFAULT 100,
  `pass_score`   INT          NOT NULL DEFAULT 60,
  `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '列表排序,越小越前',
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否参与每日任务',
  `remark`       VARCHAR(255) NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='科目';

-- 章节
CREATE TABLE `chapter` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `subject_id`  BIGINT       NOT NULL,
  `parent_id`   BIGINT       NULL COMMENT '父章节,支持两级;顶级为空',
  `name`        VARCHAR(128) NOT NULL,
  `sort_order`  INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chapter_subject` (`subject_id`, `sort_order`),
  CONSTRAINT `fk_chapter_subject` FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节';
```

**`exam_slot` 取值**(两天 × 上午下午):

| 值 | 含义 |
|---|---|
| `D1_AM` | 第一天上午 9:00—11:30 |
| `D1_PM` | 第一天下午 14:30—17:00 |
| `D2_AM` | 第二天上午 9:00—11:30 |
| `D2_PM` | 第二天下午 14:30—17:00 |

> 已知:英语(专升本)13000 为 `D2_PM`。其余三门待按准考证确认后填入。

### V2__create_question_and_paper.sql

```sql
-- 题目
CREATE TABLE `question` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `subject_id`    BIGINT       NOT NULL,
  `chapter_id`    BIGINT       NULL COMMENT '可为空,导入时未归章',
  `type`          VARCHAR(32)  NOT NULL COMMENT '题型,见下方取值表',
  `stem`          TEXT         NOT NULL COMMENT '题干(纯文本,公式用 $...$)',
  `options`       JSON         NULL COMMENT '选项数组,见下方结构说明',
  `answer`        VARCHAR(255) NULL COMMENT '客观题正确答案,如 "A" / "ABD" / "T"',
  `answer_points` JSON         NULL COMMENT '主观题参考答案要点,字符串数组',
  `analysis`      TEXT         NULL COMMENT '解析',
  `difficulty`    TINYINT      NOT NULL DEFAULT 2 COMMENT '1简单 2中等 3困难',
  `source`        VARCHAR(64)  NULL COMMENT '来源,如「2024年10月真题」',
  `source_year`   INT          NULL COMMENT '真题年份,便于筛选',
  `score`         DECIMAL(4,1) NOT NULL DEFAULT 2.0 COMMENT '本题分值',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT待校对 / PUBLISHED已入库',
  `ai_analysis`   TEXT         NULL COMMENT 'AI 老师精简解析,按需生成后缓存',
  `ai_analysis_at` DATETIME    NULL COMMENT 'AI 解析生成时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_question_subject` (`subject_id`, `type`),
  KEY `idx_question_chapter` (`chapter_id`),
  KEY `idx_question_source`  (`subject_id`, `source_year`),
  FULLTEXT KEY `ft_question_stem` (`stem`) WITH PARSER ngram COMMENT '题干检索',
  CONSTRAINT `fk_question_subject` FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`),
  CONSTRAINT `fk_question_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `chapter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目';

-- 试卷
CREATE TABLE `paper` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `subject_id`   BIGINT       NOT NULL,
  `name`         VARCHAR(128) NOT NULL COMMENT '如「2024年10月真题」',
  `type`         VARCHAR(16)  NOT NULL DEFAULT 'REAL' COMMENT 'REAL真题 / MOCK模拟',
  `year`         INT          NULL,
  `total_score`  INT          NOT NULL DEFAULT 100,
  `duration_min` INT          NOT NULL DEFAULT 150 COMMENT '考试时长(分钟)',
  `remark`       VARCHAR(255) NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_paper_subject` (`subject_id`, `year`),
  CONSTRAINT `fk_paper_subject` FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷';

-- 试卷-题目关联
CREATE TABLE `paper_question` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `paper_id`     BIGINT      NOT NULL,
  `question_id`  BIGINT      NOT NULL,
  `section_name` VARCHAR(64) NULL COMMENT '大题名,如「一、单项选择题」',
  `sort_order`   INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_question` (`paper_id`, `question_id`),
  KEY `idx_pq_paper_sort` (`paper_id`, `sort_order`),
  CONSTRAINT `fk_pq_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`),
  CONSTRAINT `fk_pq_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷题目关联';
```

**`question.type` 取值表**:

| 值 | 中文 | 判分方式 | 对应科目 |
|---|---|---|---|
| `SINGLE_CHOICE` | 单项选择题 | 客观,自动判分 | 全部 |
| `MULTIPLE_CHOICE` | 多项选择题 | 客观,自动判分 | 备用 |
| `TRUE_FALSE` | 判断题 | 客观,自动判分 | 备用 |
| `READING_JUDGE` | 阅读判断 | 客观(答案 `T`/`F`/`NG`) | 英语 |
| `READING_CHOICE` | 阅读选择 | 客观 | 英语 |
| `SECTION_MATCH` | 概括段落大意和补全句子 | 客观 | 英语 |
| `SENTENCE_FILL` | 填句补文 | 客观 | 英语 |
| `WORD_FILL` | 填词补文 | 客观 | 英语 |
| `CLOZE_TRANSFORM` | 完形补文 | 客观 | 英语 |
| `SHORT_ANSWER` | 简答题 | 主观,自评 | 马原/史纲/习概 |
| `ESSAY` | 论述题 | 主观,自评 | 马原/史纲 |
| `MATERIAL_ANALYSIS` | 材料分析题 | 主观,自评 | 习概 |
| `WRITING` | 短文写作 | 主观,自评 | 英语 |

**`options` JSON 结构**(客观题):

```json
[
  {"key": "A", "text": "思维和存在的关系问题"},
  {"key": "B", "text": "物质和意识哪个是第一性"},
  {"key": "C", "text": "世界的本原是什么"},
  {"key": "D", "text": "世界是否可知"}
]
```

**`answer_points` JSON 结构**(主观题,即评分要点):

```json
[
  "科学地回答了思维和存在的关系问题",
  "为人们认识和改造世界提供了根本遵循",
  "是划分唯物主义和唯心主义的唯一标准"
]
```

### V3__create_practice_and_answer.sql

```sql
-- 练习场次
CREATE TABLE `practice_session` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT      NOT NULL DEFAULT 1,
  `subject_id`       BIGINT      NULL COMMENT '为空表示跨科目',
  `paper_id`         BIGINT      NULL COMMENT 'MOCK 模式时有值',
  `mode`             VARCHAR(16) NOT NULL COMMENT 'DAILY/RANDOM/CHAPTER/MOCK/REVIEW',
  `status`           VARCHAR(16) NOT NULL DEFAULT 'ONGOING' COMMENT 'ONGOING/FINISHED/ABANDONED',
  `total_count`      INT         NOT NULL DEFAULT 0,
  `answered_count`   INT         NOT NULL DEFAULT 0,
  `correct_count`    INT         NOT NULL DEFAULT 0,
  `score`            DECIMAL(5,1) NULL COMMENT '本轮得分(客观题得分 + 主观题自评得分)',
  `started_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at`      DATETIME    NULL,
  `duration_seconds` INT         NULL COMMENT '实际用时',
  PRIMARY KEY (`id`),
  KEY `idx_session_user_time` (`user_id`, `started_at`),
  KEY `idx_session_status` (`user_id`, `status`),
  CONSTRAINT `fk_session_subject` FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='练习场次';

-- 作答记录
CREATE TABLE `answer_record` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `session_id`    BIGINT      NOT NULL,
  `question_id`   BIGINT      NOT NULL,
  `user_answer`   VARCHAR(255) NULL COMMENT '客观题所选项,如 "B";主观题存自述',
  `confidence`    VARCHAR(16) NOT NULL DEFAULT 'SURE' COMMENT 'SURE确定 / UNSURE没把握 / WRONG答错',
  `is_correct`    TINYINT(1)  NULL COMMENT '客观题是否正确;主观题为空',
  `self_score`    INT         NULL COMMENT '主观题自评:勾选到的要点数',
  `total_points`  INT         NULL COMMENT '主观题要点总数',
  `spent_seconds` INT         NOT NULL DEFAULT 0,
  `answered_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_answer_session_question` (`session_id`, `question_id`),
  KEY `idx_answer_question` (`question_id`, `answered_at`),
  CONSTRAINT `fk_answer_session`  FOREIGN KEY (`session_id`)  REFERENCES `practice_session` (`id`),
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作答记录';
```

### V4__create_review_and_stats.sql

```sql
-- 复习队列(错题本)
CREATE TABLE `review_item` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT      NOT NULL DEFAULT 1,
  `question_id`    BIGINT      NOT NULL,
  `stage`          INT         NOT NULL DEFAULT 0 COMMENT 'SM-2 复习阶段,0 表示新错题',
  `interval_days`  INT         NOT NULL DEFAULT 1 COMMENT '当前间隔天数',
  `ease_factor`    DECIMAL(4,2) NOT NULL DEFAULT 2.50 COMMENT '难度系数,最小 1.30',
  `wrong_count`    INT         NOT NULL DEFAULT 0 COMMENT '累计答错次数',
  `correct_streak` INT         NOT NULL DEFAULT 0 COMMENT '连续答对次数',
  `last_review_at` DATETIME    NULL,
  `next_review_at` DATETIME    NOT NULL COMMENT '下次该复习的时间,查询核心字段',
  `status`         VARCHAR(16) NOT NULL DEFAULT 'LEARNING' COMMENT 'LEARNING在队列 / MASTERED已掌握',
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_user_question` (`user_id`, `question_id`),
  KEY `idx_review_due` (`user_id`, `status`, `next_review_at`),
  CONSTRAINT `fk_review_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='复习队列';

-- 学习日志(每日汇总,打卡与统计的数据源)
CREATE TABLE `study_log` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT      NOT NULL DEFAULT 1,
  `log_date`        DATE        NOT NULL COMMENT '统计日期',
  `session_count`   INT         NOT NULL DEFAULT 0 COMMENT '当日练习场次数',
  `question_count`  INT         NOT NULL DEFAULT 0 COMMENT '当日作答题数',
  `correct_count`   INT         NOT NULL DEFAULT 0 COMMENT '当日答对题数(仅客观题)',
  `study_seconds`   INT         NOT NULL DEFAULT 0 COMMENT '当日学习时长',
  `review_done`     INT         NOT NULL DEFAULT 0 COMMENT '当日完成的复习题数',
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_log_user_date` (`user_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习日志';
```

### V5__create_setting_notify_ai.sql

```sql
-- 用户设置(单行)
CREATE TABLE `user_setting` (
  `id`                    BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`               BIGINT      NOT NULL,
  `daily_question_count`  INT         NOT NULL DEFAULT 10 COMMENT '每日任务题量',
  `notify_channel`        VARCHAR(16) NOT NULL DEFAULT 'WECOM' COMMENT 'WECOM企业微信群机器人 / SERVERCHAN Server酱',
  `notify_webhook`        VARCHAR(512) NULL COMMENT '企业微信群机器人 Webhook 地址',
  `notify_key`            VARCHAR(255) NULL COMMENT 'Server酱 SendKey(通道为 SERVERCHAN 时使用)',
  `reminder_enabled`      TINYINT(1)  NOT NULL DEFAULT 1,
  `reminder_time`         TIME        NOT NULL DEFAULT '07:00:00' COMMENT '早间主提醒时间',
  `evening_reminder_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `evening_reminder_time` TIME        NOT NULL DEFAULT '21:30:00' COMMENT '晚间未打卡提醒',
  `dark_mode`             VARCHAR(16) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO/LIGHT/DARK',
  `created_at`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设置';

INSERT INTO `user_setting` (`user_id`) VALUES (1);

-- 提醒日志(防重复发送)
CREATE TABLE `reminder_log` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT      NOT NULL DEFAULT 1,
  `log_date`   DATE        NOT NULL,
  `type`       VARCHAR(16) NOT NULL COMMENT 'MORNING早间 / EVENING晚间',
  `sent_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `success`    TINYINT(1)  NOT NULL DEFAULT 1,
  `fail_reason` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reminder_user_date_type` (`user_id`, `log_date`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒日志';

-- AI 调用日志(核算 TokenPlan 用量)
CREATE TABLE `ai_call_log` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      NOT NULL DEFAULT 1,
  `scene`       VARCHAR(32) NOT NULL COMMENT 'EXPLAIN解析 / VARIANT变式题 / MNEMONIC口诀 / DIGEST串讲',
  `question_id` BIGINT      NULL,
  `subject_id`  BIGINT      NULL,
  `model`       VARCHAR(32) NOT NULL COMMENT '如 qwen-plus',
  `tokens_in`   INT         NOT NULL DEFAULT 0,
  `tokens_out`  INT         NOT NULL DEFAULT 0,
  `duration_ms` INT         NOT NULL DEFAULT 0,
  `success`     TINYINT(1)  NOT NULL DEFAULT 1,
  `fail_reason` VARCHAR(255) NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_log_scene` (`scene`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 调用日志';

-- 考前串讲清单(每科一份,可覆盖重生成)
CREATE TABLE `ai_digest` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      NOT NULL DEFAULT 1,
  `subject_id`  BIGINT      NOT NULL,
  `content`     TEXT        NOT NULL COMMENT '必背 N 点清单(结构化文本)',
  `model`       VARCHAR(32) NOT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_digest_user_subject` (`user_id`, `subject_id`),
  CONSTRAINT `fk_digest_subject` FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考前串讲清单';
```

---

## 四、关键索引说明

| 索引 | 为什么需要 |
|---|---|
| `idx_review_due` | 每次生成每日任务、每分钟检查提醒都要查「该用户到期复习题」,这是全系统最高频查询 |
| `idx_answer_session_question` (唯一) | 保证同一场次同一题只能作答一次,提交接口的幂等基础 |
| `ft_question_stem` (ngram 全文) | 题库检索。中文必须用 `ngram` 解析器,默认解析器对中文无效 |
| `idx_session_user_time` | 统计页查近 30 天练习记录 |

`ngram` 全文索引需要在 MySQL 配置里设置 `ngram_token_size`(默认 2 即可)。

---

## 五、统计数据来源约定

| 指标 | 计算方式 |
|---|---|
| 总题量 | `COUNT(question WHERE subject_id = ?)` |
| 已掌握题数 | `COUNT(review_item WHERE status = 'MASTERED')` |
| 平均正确率 | `SUM(study_log.correct_count) / SUM(study_log.question_count)` |
| 连续打卡天数 | 从今天往前遍历 `study_log`,断档即停 |
| 各科目正确率 | `answer_record JOIN question ON subject_id` 分组统计 |
| 近 30 天趋势 | 按 `study_log.log_date` 取 30 行,缺的日期补 0 |
| AI 用量 | 按 `ai_call_log.created_at` 分组,汇总 `tokens_in + tokens_out` 与调用次数 |

**注意**:正确率只统计客观题(`is_correct IS NOT NULL`),主观题不进分母,否则数字会被自评尺度污染。
