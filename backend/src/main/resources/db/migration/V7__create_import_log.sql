-- V7: 导入记录
--
-- 为什么要有这张表:导入结果的失败明细原先只在页面上出现一次,离开页面就没了。
-- 识别/导入漏掉的题目需要人工补录,得有一份可回看的清单,写清楚「哪一条没进去、为什么」。
--
-- 一次导入(一份 PDF / 一份 JSON)记一行;失败条目的题干和原因存 JSON。
-- 识别阶段的提醒(如某页没认出来)也一并存下 —— 那是「题目根本没出现」的主要原因。

CREATE TABLE `import_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `source`       VARCHAR(16)  NOT NULL COMMENT 'PDF 识别 / JSON 导入',
  `subject_code` VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '课程代码',
  `paper_name`   VARCHAR(191) NULL COMMENT '试卷名或文件名',
  `status`       VARCHAR(24)  NOT NULL COMMENT 'IMPORTED 全部入库 / PARTIAL 部分入库 / DUPLICATED 全部重复 / FAILED 整份未入库',
  `total`        INT          NOT NULL DEFAULT 0 COMMENT '本次条目总数',
  `imported`     INT          NOT NULL DEFAULT 0 COMMENT '成功入库数',
  `skipped`      INT          NOT NULL DEFAULT 0 COMMENT '跳过数(校验失败 + 重复)',
  `duplicated`   INT          NOT NULL DEFAULT 0 COMMENT '其中因题干重复而跳过的',
  `issues`       JSON         NULL COMMENT '未入库条目:[{index, stem, reason}]',
  `warnings`     JSON         NULL COMMENT '识别阶段的提醒(字符串数组)',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_import_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入记录';
