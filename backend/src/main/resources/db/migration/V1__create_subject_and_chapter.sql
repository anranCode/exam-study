-- V1: 用户、科目、章节
-- 数据模型准绳见 docs/study-exam/backend/02-data-model.md

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

-- 四条初始科目:自考本科四门,颜色见 ui-design-spec「科目识别色」
INSERT INTO `subject` (`code`, `name`, `short_name`, `color`, `exam_slot`, `sort_order`) VALUES
  ('03709', '马克思主义基本原理概论',           '马原', '#E5484D', 'D1_AM', 1),
  ('03708', '中国近现代史纲要',                 '史纲', '#F5A524', 'D1_PM', 2),
  ('15040', '习近平新时代中国特色社会主义思想概论', '习概', '#2F6FED', 'D2_AM', 3),
  ('13000', '英语(专升本)',                     '英语', '#12A150', 'D2_PM', 4);