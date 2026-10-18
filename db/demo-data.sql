-- 演示数据:章节 + 若干真题,方便查看界面效果。
-- 想清空重新开始:DELETE FROM question WHERE source LIKE '演示数据%'; DELETE FROM chapter;
USE study_exam;

INSERT INTO `chapter` (`subject_id`, `parent_id`, `name`, `sort_order`) VALUES
  (1, NULL, '第一章 物质世界及其发展规律', 1),
  (1, NULL, '第二章 认识世界和改造世界', 2),
  (1, NULL, '第三章 人类社会及其发展规律', 3),
  (2, NULL, '第一章 反对外国侵略的斗争', 1),
  (2, NULL, '第二章 对国家出路的早期探索', 2),
  (3, NULL, '第一章 新时代坚持和发展中国特色社会主义', 1),
  (4, NULL, '第一部分 阅读判断', 1),
  (4, NULL, '第二部分 阅读选择', 2);

-- 马原 单选题(第一章):answer 故意混用大小写,验证入库时会被规范化
INSERT INTO `question`
  (`subject_id`, `chapter_id`, `type`, `stem`, `options`, `answer`, `analysis`,
   `difficulty`, `source`, `source_year`, `score`, `status`) VALUES
(1, (SELECT id FROM chapter WHERE subject_id=1 AND name='第一章 物质世界及其发展规律'), 'SINGLE_CHOICE',
 '马克思主义哲学的基本问题是（  ）',
 JSON_ARRAY(JSON_OBJECT('key','A','text','物质和意识的关系问题'),
            JSON_OBJECT('key','B','text','思维和存在的关系问题'),
            JSON_OBJECT('key','C','text','世界的本原问题'),
            JSON_OBJECT('key','D','text','世界是否可知的问题')),
 'B', '恩格斯指出，全部哲学特别是近代哲学的重大的基本问题，是思维和存在的关系问题。',
 1, '演示数据 · 2024年10月真题', 2024, 2.0, 'PUBLISHED'),

(1, (SELECT id FROM chapter WHERE subject_id=1 AND name='第一章 物质世界及其发展规律'), 'MULTIPLE_CHOICE',
 '下列选项中，属于客观唯心主义观点的有（  ）',
 JSON_ARRAY(JSON_OBJECT('key','A','text','存在就是被感知'),
            JSON_OBJECT('key','B','text','理生万物，理主动静'),
            JSON_OBJECT('key','C','text','物质决定意识'),
            JSON_OBJECT('key','D','text','世界是绝对精神的产物')),
 'BD', '客观唯心主义把某种脱离人的“客观精神”当作世界本原。B、D 属于客观唯心主义，A 属于主观唯心主义，C 是唯物主义。',
 2, '演示数据 · 2024年10月真题', 2024, 2.0, 'PUBLISHED'),

(1, (SELECT id FROM chapter WHERE subject_id=1 AND name='第二章 认识世界和改造世界'), 'SINGLE_CHOICE',
 '实践是检验真理的唯一标准，这是因为实践具有（  ）',
 JSON_ARRAY(JSON_OBJECT('key','A','text','直接现实性'),
            JSON_OBJECT('key','B','text','主观能动性'),
            JSON_OBJECT('key','C','text','社会历史性'),
            JSON_OBJECT('key','D','text','客观规律性')),
 'A', '实践的直接现实性，使它能把主观认识同客观实际联系起来加以对照。',
 2, '演示数据 · 2023年10月真题', 2023, 2.0, 'PUBLISHED'),

(2, (SELECT id FROM chapter WHERE subject_id=2 AND name='第一章 反对外国侵略的斗争'), 'SINGLE_CHOICE',
 '中国近代史上第一个不平等条约是（  ）',
 JSON_ARRAY(JSON_OBJECT('key','A','text','《北京条约》'),
            JSON_OBJECT('key','B','text','《南京条约》'),
            JSON_OBJECT('key','C','text','《马关条约》'),
            JSON_OBJECT('key','D','text','《辛丑条约》')),
 'B', '1842年签订的中英《南京条约》是中国近代史上第一个不平等条约。',
 1, '演示数据 · 2024年4月真题', 2024, 2.0, 'PUBLISHED'),

(3, (SELECT id FROM chapter WHERE subject_id=3 AND name='第一章 新时代坚持和发展中国特色社会主义'), 'SINGLE_CHOICE',
 '中国特色社会主义最本质的特征是（  ）',
 JSON_ARRAY(JSON_OBJECT('key','A','text','共同富裕'),
            JSON_OBJECT('key','B','text','中国共产党领导'),
            JSON_OBJECT('key','C','text','社会主义市场经济'),
            JSON_OBJECT('key','D','text','人民当家作主')),
 'B', '中国特色社会主义最本质的特征是中国共产党领导，这也是中国特色社会主义制度的最大优势。',
 1, '演示数据 · 2024年10月真题', 2024, 2.0, 'PUBLISHED');
-- 注:这里原本还有一道英语「阅读判断」演示题,但它的题干只有一句陈述、没抄原文,
-- 做题时无从判断 T/F/NG。阅读类题目的题干必须自带原文(格式见 README 第七节),
-- 与其留一道做不了的题,不如不放——英语题库由 PDF 识别导入,题干天然带原文。

-- 马原 主观题(第三章)
INSERT INTO `question`
  (`subject_id`, `chapter_id`, `type`, `stem`, `answer_points`, `answer`,
   `difficulty`, `source`, `source_year`, `score`, `status`) VALUES
(1, (SELECT id FROM chapter WHERE subject_id=1 AND name='第三章 人类社会及其发展规律'), 'SHORT_ANSWER',
 '简述生产力与生产关系的辩证关系。',
 JSON_ARRAY('生产力决定生产关系，生产力状况决定生产关系的性质和变化',
            '生产关系反作用于生产力，适合时促进发展，不适合时阻碍发展',
            '生产关系一定要适合生产力状况，这是社会发展的基本规律'),
 NULL, 2, '演示数据 · 2024年10月真题', 2024, 6.0, 'PUBLISHED'),

(1, (SELECT id FROM chapter WHERE subject_id=1 AND name='第三章 人类社会及其发展规律'), 'ESSAY',
 '试述社会存在与社会意识的辩证关系及其现实意义。',
 JSON_ARRAY('社会存在决定社会意识，社会意识是社会存在的反映',
            '社会意识具有相对独立性，有时落后于社会存在，有时超前于社会存在',
            '先进的社会意识对社会发展起积极的推动作用，落后的社会意识起阻碍作用',
            '现实意义：坚持物质文明和精神文明协调发展，重视意识形态工作'),
 NULL, 3, '演示数据 · 2023年10月真题', 2023, 10.0, 'PUBLISHED'),

(1, NULL, 'SHORT_ANSWER',
 '简述矛盾的普遍性与特殊性的关系。',
 JSON_ARRAY('矛盾的普遍性即共性，特殊性即个性，二者相互联结',
            '普遍性寓于特殊性之中，并通过特殊性表现出来',
            '特殊性也离不开普遍性，二者在一定条件下相互转化'),
 NULL, 2, '演示数据 · 未归章', 2023, 6.0, 'PUBLISHED'),

-- 故意留一道 DRAFT,用来验证题库列表的状态筛选
(1, NULL, 'SHORT_ANSWER',
 '（待校对）简述实践与认识的辩证关系。',
 JSON_ARRAY('实践是认识的基础，决定认识的产生和发展',
            '认识对实践具有反作用，正确的认识指导实践取得成功'),
 NULL, 2, '演示数据 · 待校对', 2024, 6.0, 'DRAFT');