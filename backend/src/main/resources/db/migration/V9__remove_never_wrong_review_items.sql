-- V9: 错题本只保留「答错 / 没把握」的题
--
-- 旧逻辑在答对时也会推进排期:队列里没有这道题就用 newItem() 新建一条。
-- 于是做对过的新题也会以 wrong_count = 0 进入错题本,列表里显示「错 0 次」,
-- 既不符合「错题本」的语义,也会占掉按错误次数排序的列表位置。
--
-- 新逻辑(见 PracticeService#advanceSchedule)只有 WRONG / UNSURE 会新建条目,
-- SURE 只推进已经在队列里的题。这里把历史上从未答错过的条目清掉。
DELETE FROM `review_item` WHERE `wrong_count` = 0;
