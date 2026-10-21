-- V11: 科目专属 AI 提示词
--
-- 生成答案/解析时,把这段提示词追加到系统提示词里,并标记为「优先遵守」。
-- 例:英语科目要求答案用英文、词汇不超过四级;写作题按 120—150 词;解析要短。
ALTER TABLE `subject`
  ADD COLUMN `ai_prompt` TEXT NULL
      COMMENT 'AI 生成答案/解析时的科目专属提示词,优先于通用规则' AFTER `remark`;
