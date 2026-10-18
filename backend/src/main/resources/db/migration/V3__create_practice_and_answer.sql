-- V3: 练习场次、作答记录
-- 数据模型准绳见 docs/study-exam/backend/02-data-model.md
--
-- 外键动作说明(文档只给了基础 DDL,这里补上删除语义,与 04-features.md 阶段 2 的
-- 「删除题目时级联清理答题记录与复习队列」一致):
--   fk_answer_session  ON DELETE CASCADE  删除场次时连带删除其作答
--   fk_answer_question ON DELETE CASCADE  删除题目时连带删除其作答
--   fk_session_subject ON DELETE SET NULL 科目被删后场次保留(subject_id 可空),不阻断删除

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
  CONSTRAINT `fk_session_subject` FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`) ON DELETE SET NULL
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
  CONSTRAINT `fk_answer_session`  FOREIGN KEY (`session_id`)  REFERENCES `practice_session` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作答记录';