-- V4: 复习队列、学习日志
-- 数据模型准绳见 docs/study-exam/backend/02-data-model.md
--
-- 阶段 4(练习与判分)的验收标准要求在答错后 review_item 立刻出现记录,因此本表与
-- Sm2Scheduler 一起先行落地;阶段 5 的错误本列表与手动操作接口在其上继续做。
-- fk_review_question ON DELETE CASCADE:删除题目时连带清理其复习条目(阶段 2 的约定)。

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
  CONSTRAINT `fk_review_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE CASCADE
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