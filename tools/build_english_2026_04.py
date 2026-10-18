# -*- coding: utf-8 -*-
"""
把《2026年4月 英语(专升本)真题》整理成本项目的题库导入 JSON。

来源:扫描件 PDF(7 页,卷面标"共8页",缺第 8 页),卷面无答案,
      答案由我(模型)代做,因此所有题目标 DRAFT 待人工核对。
映射约定:
  · 阅读判断卷面用 A/B/C(True/False/Not Given) → 本系统用 T/F/NG
  · 阅读类题目的题干里带上对应原文,保证复习时不用翻回原卷
  · 分值按卷面标注:1 / 1.5 / 2 / 30,合计 100
"""
import json, io

SUBJECT_CODE = "13000"
DRAFT = "DRAFT"

S1 = "第一部分 阅读判断"
S2 = "第二部分 阅读选择"
S3 = "第三部分 概括段落大意和补全句子"
S4 = "第四部分 填句补文"
S5 = "第五部分 填词补文"
S6 = "第六部分 完形补文"
S7 = "第七部分 短文写作"

TFNG = [{"key": "T", "text": "True"}, {"key": "F", "text": "False"}, {"key": "NG", "text": "Not Given"}]
def abcd(a, b, c, d):
    return [{"key": "A", "text": a}, {"key": "B", "text": b}, {"key": "C", "text": c}, {"key": "D", "text": d}]

# ── 原文(逐字对照扫描件)──────────────────────────────────
AUSTRALIA = (
 "Australia was a country I always wanted to visit. This year I saved up some money and booked a flight "
 "to go travelling. I had some friends studying there, so it was a perfect opportunity to see friends and "
 "travel around.\n\n"
 "The flight made me realize exactly how far away Australia is from home. I spent 10 hours flying from "
 "London to Hong Kong, then a further 8 hours to Sydney, my first stop in Australia. The flight went quite "
 "quickly though, since there were lots of films to keep me entertained.\n\n"
 "When I arrived, I felt strange. Everything was upside down over there. It was the start of winter in May! "
 "Fortunately, for me the weather was still warm and sunny. It also took me a while to get used to the time "
 "difference.\n\n"
 "Australia is truly beautiful. It is full of views and wildlife you would not find anywhere else in the "
 "world. One of the highlights of my trip was visiting a koala (考拉) reserve in Brisbane. There were koalas "
 "everywhere sleeping in the trees. I even got a chance to hold one. I learnt lots of facts about koalas too. "
 "They can sleep for up to 22 hours a day to save energy! At the reserve there was also a big lawn full of "
 "kangaroos (袋鼠). We were allowed to feed and touch them. They were really cute and friendly.\n\n"
 "I loved visiting Australia, from the mountains and waterfalls in the National Parks to the long white "
 "sandy beaches. It's an incredible country and worth visiting. I'm already dreaming of going back!"
)

VEG_P1 = ("Veganism. What does it mean? To some of you it might be a completely new word. And it's certainly a "
 "word I have only become familiar with in recent years. A vegan diet is one that contains no animal products. "
 "That means no meat, no milk no eggs, etc.")
VEG_P2 = ("I became interested in vegan lifestyle months ago. As I was surfing videos on the Internet, I came across "
 "a woman called Freelee. She claimed to regularly eat around fifty bananas a day to stay healthy. Fifty bananas! "
 "I quickly realized that this was not the diet for me. However, what she was saying about veganism did pique my "
 "interest. And I went on to watch many more vegan videos. Eventually, I switched to veganism about six months ago.")
VEG_P45 = ("While veganism is often associated with physical weakness, that is certainly not the case for me. I have "
 "discovered many delicious vegan products, which one might never consider as vegan.\n\n"
 "Personally, eating this way has given me a much more positive outlook on life. Another vegan Kristina's story is "
 "even more amazing. Kristina was bitten by a deadly spider. She was not given the medicines required to survive "
 "the bite. Yet she made a full and speedy recovery. She attributes it to the magic power of a vegan lifestyle.")

WATER = {
 1: ("①", "Over the last 15 years, more and more people in the US have been drinking bottled water. There is no sign "
     "of slowing down. In fact, sales of bottled water are going up faster than other kind of drink. It is true even "
     "in cities where it is safe to drink tap water. This has been a disaster for the environment and for our health."),
 2: ("②", "Most bottled water comes from underground water. Much of this underground water have flowed into rivers and "
     "lakes. Using this water can make droughts (干旱) more serious. Many companies use water from mountain glaciers "
     "(冰川), but this is not much better. When the glacier melts, the water usually flows to areas like rivers or "
     "wetlands. When companies take glacier water, they take it from areas that need it."),
 3: ("③", "It takes about 1.6 liters of water to make one liter of bottled water. This wastes a lot of water. Also "
     "transporting the bottled water to stores burns fuel and creates pollution. The problems do not stop when the "
     "water reaches people who buy it. Water bottles are made from plastic which does not break down. Although it can "
     "be recycled, people usually do not put it in the recycle can."),
 4: ("④", "Some may say that bottled water is safer and healthier than tap water. But that is not true. Sometimes "
     "chemicals or bacteria get into bottled water, and companies have to destroy them. Actually, tap water is often "
     "healthier than bottled water. Bottled water may lack fluoride (氟化物) which helps protect our teeth. Fluoride "
     "is found in tiny amounts in tap water."),
 5: ("⑤", "Obviously, more people need to know that tap water is safer and healthier. Unfortunately, tap water cannot "
     "advertise itself. When a product that is cheaper and better does not win, that is bad news. When the product is "
     "water, we all lose."),
}
WATER_HEADINGS = [
 ("A", "Making droughts worse"),
 ("B", "Harmful elements in bottled water"),
 ("C", "Need for a better image of tap water"),
 ("D", "Growing attention to tap water"),
 ("E", "Causing waste and pollution"),
 ("F", "Increasing sales of bottled water"),
]
WATER_ENDINGS = [
 ("A", "in tiny amounts"),
 ("B", "towards tap water"),
 ("C", "does harm to the environment"),
 ("D", "brings more health benefits"),
 ("E", "with the problem of drought"),
 ("F", "despite the safety of tap water"),
]
BIRDS_OPTIONS = [
 ("A", "The bands contained a name and address."),
 ("B", "Birds use the earth's magnetic field to navigate too."),
 ("C", "A map and a compass are often helpful for long trips."),
 ("D", "They fly thousands of miles from one home to another."),
 ("E", "They learn the route that the older geese have traveled before."),
 ("F", "They have answered many questions about how birds navigate."),
]
HOMETOWN_WORDS = [
 ("A", "consists"), ("B", "mainly"), ("C", "attending"), ("D", "clear"),
 ("E", "small"), ("F", "center"), ("G", "hardly"), ("H", "beautiful"),
 ("I", "raise"), ("J", "known"), ("K", "home"), ("L", "way"),
]

items = []

def add(section, type_, chapter, stem, answer=None, options=None, points=None,
        analysis=None, score=None, difficulty=2):
    assert len(stem) <= 2000, f"题干超过 2000 字({len(stem)}):{stem[:40]}"
    item = {
        "sectionName": section, "type": type_, "chapterName": chapter, "stem": stem,
        "score": score, "difficulty": difficulty, "status": DRAFT,
    }
    if options is not None:
        item["options"] = options
    if answer is not None:
        item["answer"] = answer
    if points is not None:
        item["answerPoints"] = points
    if analysis:
        item["analysis"] = analysis
    items.append(item)

# ── 一、阅读判断 1—10(1 分)──────────────────────────────────
judge = [
 (1,  "I longed for a visit to Australia.", "T", "开头就说澳大利亚是他一直想去的国家。"),
 (2,  "I made some new friends during my visit in Australia.", "F",
      "原文说他有朋友在那里读书,不是旅途中新交的朋友。"),
 (3,  "I spent over 20 hours on my flight to Australia.", "F",
      "10 小时到香港再加 8 小时到悉尼,合计 18 小时,不到 20 小时。"),
 (4,  "I watched films to kill the flight time.", "T",
      "原文说飞机上有很多电影让他不无聊。"),
 (5,  "I felt very tired because of the time difference.", "NG",
      "原文只说花了一段时间适应时差,没有说感到疲惫。"),
 (6,  "Koalas can be found in many parts of the world.", "F",
      "原文说澳大利亚有别处见不到的野生动植物。"),
 (7,  "Koalas spend most of their time sleeping.", "T",
      "原文说考拉一天可睡到 22 小时以保存体力。"),
 (8,  "Koalas feed chiefly on tree leaves.", "NG",
      "原文没有提到考拉吃什么。"),
 (9,  "Visitors were asked to keep distance from kangaroos.", "F",
      "原文说他们被允许喂食和触摸袋鼠。"),
 (10, "I enjoyed the sandy beaches in Australia.", "T",
      "原文结尾说最爱从高山瀑布到白色沙滩,还想再去。"),
]
for n, stmt, ans, why in judge:
    add(S1, "READING_JUDGE", S1, f"【原文 · Australia】\n{AUSTRALIA}\n\n{stmt}",
        answer=ans, options=TFNG, analysis=why, score=1.0, difficulty=1)

# ── 二、阅读选择 11—15(2 分)──────────────────────────────────
choice = [
 (11, "According to Paragraph 1, a vegan diet is ________.",
    ["advised by food experts", "familiar to most people", "followed by many people", "free from animal products"],
    "D", VEG_P1, "第 1 段说 vegan diet 不含任何动物制品。"),
 (12, "The author knew the vegan lifestyle through ________.",
    ["online videos", "her friends", "vegan books", "TV programs"],
    "A", VEG_P2, "原文说他在网上刷视频时接触到 vegan 生活方式。"),
 (13, "The word \"pique\" (Para. 2) probably means \"________\".",
    ["express", "arouse", "expand", "protect"],
    "B", VEG_P2, "pique one's interest 意为激起兴趣,与 arouse 最接近。"),
 (14, "The author believes that the vegan diet is ________.",
    ["healthy", "cheap", "sugar-free", "low-salt"],
    "A", VEG_P45, "原文说素食没有让他虚弱,反而让他更积极,认为有益健康。"),
 (15, "Kristina is mentioned because she ________.",
    ["attracted a lot of fans", "had a positive attitude toward life",
     "went on a special fruit-based diet", "quickly recovered from a spider bite"],
    "D", VEG_P45, "克里斯蒂娜被毒蜘蛛咬后迅速痊愈,作者归因于素食。"),
]
for n, stem, opts, ans, para, why in choice:
    add(S2, "READING_CHOICE", S2, f"【原文 · Veganism】\n{para}\n\n{n}. {stem}",
        answer=ans, options=abcd(*opts), analysis=why, score=2.0, difficulty=2)

# ── 三、概括段落大意和补全句子 16—25(1 分)─────────────────────
headings = [(16, 1, "F", "第①段讲瓶装水销量持续上升。"),
            (17, 2, "A", "第②段讲取水加剧干旱。"),
            (18, 3, "E", "第③段讲生产运输浪费水并造成污染。"),
            (19, 4, "B", "第④段讲瓶装水也可能含有害物质。"),
            (20, 5, "C", "第⑤段讲自来水需要更好的形象宣传。")]
for n, para_no, ans, why in headings:
    mark, text = WATER[para_no]
    add(S3, "SECTION_MATCH", S3,
        f"【原文 · Bottled Water 第{mark}段】\n{text}\n\n请为第 {mark} 段选择正确的小标题:",
        answer=ans, options=[{"key": k, "text": t} for k, t in WATER_HEADINGS],
        analysis=why, score=1.0, difficulty=2)

endings = [
 (21, "City people prefer bottled water ________.", 1, "F", "第①段说即使在自来水安全的城市,人们仍买瓶装水。"),
 (22, "Taking water from glaciers helps little ________.", 2, "E", "第②段说取冰川水会加剧取水地区的干旱。"),
 (23, "Transportation of bottled water ________.", 3, "C", "第③段说运输瓶装水要烧燃料、造成污染。"),
 (24, "Compared with bottled water, tap water ________.", 4, "D", "第④段说自来水往往更健康。"),
 (25, "People need to change their attitude ________.", 5, "B", "第⑤段说要让更多人知道自来水更安全,即改变对自来水的态度。"),
]
for n, sentence, para_no, ans, why in endings:
    mark, text = WATER[para_no]
    add(S3, "SECTION_MATCH", S3,
        f"【原文 · Bottled Water 第{mark}段】\n{text}\n\n补全句子:{sentence}",
        answer=ans, options=[{"key": k, "text": t} for k, t in WATER_ENDINGS],
        analysis=why, score=1.0, difficulty=2)

# ── 四、填句补文 26—30(2 分)──────────────────────────────────
birds = [
 (26, "Every year hundreds of species of birds take long trips. ___ In the fall, they fly to warmer climates "
      "where food is plentiful all winter.", "D", "空格后讲秋季与春季的迁徙,前面应先说它们飞越数千英里。"),
 (27, "In the 1800s, scientists started putting bands around birds' legs. ___ When people found the banded birds, "
      "they contacted the person named on the band", "A", "下文说人们联系环上的名字,所以环上写有姓名地址。"),
 (28, "Today scientists still put bands on birds' legs. They also use new ways of tracking birds such as airplanes, "
      "computers, and satellites. ___", "F", "新手段回答了关于鸟类如何导航的许多问题。"),
 (29, "Birds often migrate in groups. You may have seen Canada geese flying high in the sky in a V-form. Young birds "
      "follow their parents, ___", "E", "幼鸟跟着父母,学习长辈走过的路线。"),
 (30, "___ Chemicals in their brains allow them to sense the magnetic field and travel in the right direction.",
      "B", "空格后讲磁场感应,承接「鸟类也利用地磁场导航」。"),
]
for n, stem, ans, why in birds:
    add(S4, "SENTENCE_FILL", S4, f"【原文 · Bird Maps and Compasses】\n{stem}\n\n第 {n} 空应填入:",
        answer=ans, options=[{"key": k, "text": t} for k, t in BIRDS_OPTIONS],
        analysis=why, score=2.0, difficulty=2)

# ── 五、填词补文 31—40(1.5 分)────────────────────────────────
words = [
 (31, "My hometown and my college town have several things in common. First, both are ___ communities. "
      "My hometown, Gridlock, has a population of only about 10,000 people.", "E", "人口只有 1 万,对应 small。"),
 (32, "Similarly, my college town, Subnormal, ___ of about 11,000 local residents.", "A", "consist of 意为「由…组成」。"),
 (33, "This population rises to 15,000 people when the college students are ___ classes.", "C",
      "attending classes 意为「上课」。"),
 (34, "Second, both are located in rural areas. Gridlock is surrounded by farmland, which is devoted ___ to "
      "growing corn and beans.", "B", "mainly 修饰 devoted to,表示「主要用于」。"),
 (35, "Likewise, Subnormal lies in the ___ of farmland that is used to ___ pigs and cattle.", "F",
      "in the center of 位于…中心。"),
 (36, "Likewise, Subnormal lies in the center of farmland that is used to ___ pigs and cattle.", "I",
      "raise pigs and cattle 意为饲养猪牛。"),
 (37, "Third, both contain college campuses. Gridlock is ___ to Neutron College, which is famous for its "
      "Agricultural Economics program.", "K", "be home to 意为「是…的所在地」。"),
 (38, "In the same ___, Subnormal boasts the ___ campus of Quark College.", "L", "in the same way 意为「同样地」。"),
 (39, "In the same way, Subnormal boasts the ___ campus of Quark College, which is well ___ for its Agricultural "
      "Engineering Department.", "H", "形容校园用 beautiful。"),
 (40, "Subnormal boasts the beautiful campus of Quark College, which is well ___ for its Agricultural "
      "Engineering Department.", "J", "be well known for 意为「以…闻名」。"),
]
for n, stem, ans, why in words:
    add(S5, "WORD_FILL", S5, f"【原文 · My Hometown and My College Town】\n{stem}\n\n第 {n} 空应填入:",
        answer=ans, options=[{"key": k, "text": t} for k, t in HOMETOWN_WORDS],
        analysis=why, score=1.5, difficulty=2)

# ── 六、完形补文 41—50(1.5 分,无选项)─────────────────────────
cloze = [
 (41, "Technology has become an essential part of our daily (day) life. It ___ (accompany) us everywhere we go.",
      "accompanies", "主语 It 为第三人称单数,用一般现在时 accompanies。"),
 (42, "Digital technology, in the form of smart ___ (phone), web-based TVs and computers, is changing the world.",
      "phones", "指多种智能设备,用复数 phones。"),
 (43, "Digital technology, in the form of smart phones, web-based TVs and computers, is ___ (change) the world, "
      "especially for advertisers.", "changing", "is + 现在分词,构成现在进行时。"),
 (44, "They can ___ (easy) take part in surveys or just turn them off if they want to.", "easily",
      "修饰动词 take part 要用副词 easily。"),
 (45, "They can also promote a brand by ___ (suggest) how good a product is to other people.", "suggesting",
      "by 后接动名词。"),
 (46, "Social media networks also play an ___ (importance) part in global advertising strategies.", "important",
      "修饰名词 part 要用形容词 important。"),
 (47, "While they browse the Internet, it would be ___ (help) for such ads to show up.", "helpful",
      "be helpful for 意为「有帮助」。"),
 (48, "Internet ___ (user) click on these ads more often if they look for relevant information.", "users",
      "主语指众多用户,用复数 users。"),
 (49, "Internet users click on these ads more often if they look for relevant ___ (inform).", "information",
      "inform 的名词形式是不可数名词 information。"),
 (50, "Advertisers are now ___ (face) with a great challenge.", "faced", "be faced with 意为「面临」。"),
]
for n, stem, ans, why in cloze:
    add(S6, "CLOZE_TRANSFORM", S6, f"【原文 · Advertising with Digital Technology】\n{stem}\n\n第 {n} 空应填入:",
        answer=ans, analysis=why, score=1.5, difficulty=2)

# ── 七、短文写作 51(30 分,主观题)────────────────────────────
add(S7, "WRITING", S7,
    "某英文报社正在举办题为 \"Safe Journey\" 的征文活动。请你根据下述写作要点提交一篇英文短文应征。\n"
    "· 旅途中安全的重要性。\n· 旅途中应该特别注意的事项。\n\n(要求 100 词左右,答案写在答题纸上。)",
    points=["点明旅途安全的重要性(如避免意外、保障顺利出行)",
            "列出旅途中应特别注意的事项(如保管财物、注意交通与住宿安全、留意天气与当地规定)",
            "结构完整:开头引入、正文分点、结尾总结,词数 100 左右"],
    analysis="应用文写作按要点给分:第一段点题说明安全的重要性,第二段分点写注意事项,结尾一句收束。",
    score=30.0, difficulty=2)

total = sum(i["score"] for i in items)
doc = {
    "subjectCode": SUBJECT_CODE,
    "paper": {"name": "2026年4月英语(专升本)真题", "type": "REAL", "year": 2026,
              "totalScore": 100, "durationMin": 150,
              "remark": "由扫描件 PDF 识别整理;卷面无答案,答案由 AI 代做,状态均为待校对(DRAFT)"},
    "items": items,
}
out = io.open("E:/study/exam-system/app/backend/src/main/resources/sample/sample-english-2026-04.json",
              "w", encoding="utf-8")
json.dump(doc, out, ensure_ascii=False, indent=2)
out.close()

from collections import Counter
print(f"题目 {len(items)} 道,分值合计 {total}(应为 100)")
print("题型分布:", dict(Counter(i["type"] for i in items)))
print("最长题干:", max(len(i["stem"]) for i in items), "字(上限 2000)")