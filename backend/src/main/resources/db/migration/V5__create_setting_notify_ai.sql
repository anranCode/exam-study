-- V5: 用户设置、提醒日志、AI 调用日志、考前串讲
-- 数据模型准绳见 docs/study-exam/backend/02-data-model.md
--
-- 说明:本脚本一次性建好这四个表(与文档的 V5 完全一致),但当前只用到 ai_call_log——
-- PDF 试卷识别与 AI 讲解都要按 TokenPlan 核算用量,先把它跑起来;
-- user_setting / reminder_log / ai_digest 的接口与页面留到阶段 7、阶段 9,届时无需再加迁移。

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
  `scene`       VARCHAR(32) NOT NULL COMMENT 'EXPLAIN解析 / VARIANT变式题 / MNEMONIC口诀 / DIGEST串讲 / PARSE_PDF试卷识别',
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