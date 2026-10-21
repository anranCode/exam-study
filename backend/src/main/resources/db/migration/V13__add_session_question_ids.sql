-- V13: 场次保存题目 ID
--
-- 模拟考试要按「整卷」统计:交卷时没答的题也应计入满分、列为未作答,
-- 但 practice_session 原来只存题数,结束后无法还原抽到了哪些题。
-- 这里把出题顺序的题目 ID 存下来,结果页和详情页都能按整卷算分。
ALTER TABLE `practice_session`
  ADD COLUMN `question_ids` JSON NULL COMMENT '本场抽到的题目 ID(按出题顺序)' AFTER `duration_min`;
