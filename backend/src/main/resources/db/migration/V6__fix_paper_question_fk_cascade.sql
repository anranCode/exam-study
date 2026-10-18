-- V6: 修正 paper_question → question 的外键动作
--
-- 问题:阶段 2 建表时,answer_record 与 review_item 的题目外键都声明了 ON DELETE CASCADE
-- (对应 04-features.md 阶段 2「删除题目时级联清理」),但 paper_question 这张关联表漏了。
-- 后果:只要一道题被收进过试卷,删除它就会因外键约束失败——题库页点「删除」直接报错。
--
-- 修法:重建该外键,加上 ON DELETE CASCADE,与另外两张表保持一致。

ALTER TABLE `paper_question` DROP FOREIGN KEY `fk_pq_question`;

ALTER TABLE `paper_question`
  ADD CONSTRAINT `fk_pq_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE CASCADE;