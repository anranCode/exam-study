-- V12: 模拟考试的限时
-- 普通练习不显示倒计时,只有模拟考试才用;为空表示不限时。
ALTER TABLE `practice_session`
  ADD COLUMN `duration_min` INT NULL COMMENT '模拟考试限时(分钟);普通练习为空' AFTER `paper_id`;
