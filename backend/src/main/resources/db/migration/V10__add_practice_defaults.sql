-- V10: 自由练习的默认条件(题源 / 科目 / 题型 / 题量)
--
-- 需求:在设置里给「题库」「错题」两个题源分别配好默认科目、题型、题量,
-- 进入自由练习时自动带入,不用每次重选。
--
-- 题型存逗号分隔的枚举名(如 SINGLE_CHOICE,MULTIPLE_CHOICE),NULL/空串表示不限;
-- 科目不建外键:科目被删掉时默认值失效即可,前端会忽略不存在的科目。
ALTER TABLE `user_setting`
  ADD COLUMN `practice_source` VARCHAR(16) NOT NULL DEFAULT 'LIBRARY'
      COMMENT '默认题源:LIBRARY 题库 / WRONG 错题' AFTER `daily_question_count`,
  ADD COLUMN `library_subject_id` BIGINT NULL
      COMMENT '题库源默认科目,NULL=全部' AFTER `practice_source`,
  ADD COLUMN `library_types` VARCHAR(255) NULL
      COMMENT '题库源默认题型,逗号分隔,NULL/空=全部' AFTER `library_subject_id`,
  ADD COLUMN `library_count` INT NOT NULL DEFAULT 10
      COMMENT '题库源默认题量' AFTER `library_types`,
  ADD COLUMN `wrong_subject_id` BIGINT NULL
      COMMENT '错题源默认科目,NULL=全部' AFTER `library_count`,
  ADD COLUMN `wrong_types` VARCHAR(255) NULL
      COMMENT '错题源默认题型,逗号分隔,NULL/空=全部' AFTER `wrong_subject_id`,
  ADD COLUMN `wrong_count` INT NOT NULL DEFAULT 10
      COMMENT '错题源默认题量' AFTER `wrong_types`;
