-- V2: 题目、试卷、试卷题目关联
-- 数据模型准绳见 docs/study-exam/backend/02-data-model.md

-- 题目
CREATE TABLE `question` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `subject_id`    BIGINT       NOT NULL,
  `chapter_id`    BIGINT       NULL COMMENT '可为空,导入时未归章',
  `type`          VARCHAR(32)  NOT NULL COMMENT '题型,见 02-data-model.md 取值表',
  `stem`          TEXT         NOT NULL COMMENT '题干(纯文本,公式用 $...$)',
  `options`       JSON         NULL COMMENT '选项数组 [{key,text}]',
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