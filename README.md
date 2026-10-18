# 自考学习中心(study-exam)

把历年真题录进系统,按遗忘曲线安排复习,用碎片时间刷题,并通过微信推送把你拉回学习状态。
**纯自用**的单用户工具。

> 全部规格见 [`docs/README.md`](docs/README.md)。本文只讲「现在能跑什么、怎么跑、还剩什么」。

---

## 一、现在能跑什么

| 部分 | 状态 |
|---|---|
| 数据库 | ✅ 建库 + 8 个 Flyway 迁移(15 张表),含当前题库与学习进度快照 |
| 后端骨架 | ✅ 统一响应 / 错误码 / 全局异常 / CORS / Swagger / 健康检查 |
| 科目与章节 | ✅ 增删改查全部可用(含删除守卫、章节两级树) |
| 题库核心 | ✅ 题目增删改查、按题型校验、中文题干检索、分页 |
| 批量导入 | ✅ JSON 文件 / JSON body 两个入口,逐条容错、**题干去重**、半数回滚、自动建试卷 |
| **导入记录** | ✅ 每次导入漏掉的题目(题干 + 原因)与识别提醒留档,可回看、复制题干,补录后删除 |
| **练习与判分** | ✅ 五种组题模式、客观题自动判分、主观题逐条自评、幂等提交 |
| **复习排期(SM-2)** | ✅ 三态排期(确定 / 没把握 / 答错)、间隔 1→3→8 天、掌握后出队 |
| 统计口径 | ✅ 正确率只算客观题;连续打卡按「当天确实做过题」判定 |
| AI 配置 | ✅ 千问 TokenPlan 已打通(Anthropic 兼容接口,密钥走环境变量) |
| 前端骨架 | ✅ 响应式布局(手机底部 Tab / 桌面左侧栏)、设计变量、请求封装 |
| 科目管理页 | ✅ 完整可用(科目 + 章节树增删改、取色器、考试场次) |
| 题库管理页 | ✅ 筛选(科目/章节/题型/年份/状态/关键词)+ 表格 + 分页 + 导出 JSON |
| 批量导入页 | ✅ 三步式:上传或粘贴 → 预览标错 → 结果卡片;含格式说明抽屉 |
| **PDF 试卷识别** | ✅ 导入页可直接传试卷 PDF,模型逐页认出题目 → 预览核对 → 入库(见第七节) |

| 题目新建/编辑 | ✅ 动态表单,答案点选而非手打,主观题要点可增删 |
| **刷题页** | ✅ 三段式布局、选项卡片五态(✓/✗ 图标)、主观题自评、断点续做 |
| **本轮小结** | ✅ 得分/正确率/用时、错题清单、一键复习错题 |
| 今日页 | ✅ 真实数据(倒计时、连续天数、到期数、主任务按钮、未完成提醒) |
| **错题本** | ✅ 按科目分组 + 筛选(科目/到期/是否已掌握)+ 单题重做 / 标记已掌握 / 移出 |
| **数据看板** | ✅ 三个大数字 + 各科目正确率 + 近 30 天趋势 + 章节掌握度(ECharts 按需引入) |
| **设置与提醒** | ✅ 每日题量、早间/晚间提醒、企业微信或 Server酱通道、发送测试、近 7 天提醒日志 |
| 手机 / 平板适配 | ✅ 题库改卡片列表、筛选可折叠、错题本分批显示、做题页隐藏底部 Tab、平板侧栏收成图标轨 |
| 串讲 | ⏳ 占位页,阶段 9 一起做 |

---

## 二、跑起来

前置:JDK 17 · Maven 3.9+ · Node 18+ · 本机 MySQL 8(`root` / `123456`)。
数据库账号默认按 `root` / `123456` 连接,换机器时用环境变量 `DB_USERNAME` / `DB_PASSWORD` 覆盖即可。

### 1. 数据库(只需一次)

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS study_exam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

建表不用手工执行任何 SQL——启动时 Flyway 会自动跑迁移。

### 2. 后端(端口 8080)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- 接口文档:http://localhost:8080/swagger-ui.html
- 健康检查:http://localhost:8080/actuator/health

### 3. 前端(端口 5173)

```bash
cd frontend
npm install     # 首次需要
npm run dev
```

打开 http://localhost:5173 ,开发期 `/api` 由 Vite 代理到 8080。

---

## 三、AI(千问 TokenPlan)

**填法**:在启动后端前设置环境变量

```bash
export DASHSCOPE_API_KEY=sk-你的key
```

Windows PowerShell 用 `$env:DASHSCOPE_API_KEY="sk-..."`,或 `setx DASHSCOPE_API_KEY "sk-..."`
(用 `setx` 后要**重启后端**才生效——它只对之后启动的进程可见;后端为此加了「进程环境变量
读不到就读 Windows 用户环境变量」的兜底,所以不必重启整个桌面应用)。

Key 在阿里云百炼控制台创建:https://bailian.console.aliyun.com/ → API-KEY。

| 配置项 | 值 | 环境变量可覆盖 |
|---|---|---|
| 入口 | `https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic` | `DASHSCOPE_BASE_URL` |
| 请求路径 | `/v1/messages`(**Anthropic Messages 协议**,头是 `x-api-key` + `anthropic-version`) | — |
| 模型 | `qwen3.8-flash`(文本与视觉同一把 key) | `DASHSCOPE_MODEL` / `DASHSCOPE_VISION_MODEL` |
| 超时 | 文本 15 秒 / 视觉 180 秒 | — |
| 讲解字数上限 | 120 字 | — |

> 这不是 OpenAI 的 `/chat/completions` + `Authorization: Bearer`——照那个写会 401。
> 详见 `backend/.../ai/AiClient.java` 的注释与 `docs` 里的修正记录。

**没填 Key 也能正常启动**:AI 接口会返回 `code = 6001`(AI 服务暂不可用),练习流程不受影响。
人设与四种场景(精简解析 / 变式题 / 记忆口诀 / 考前串讲)的 Prompt 已写在
`backend/.../ai/PromptTemplates.java`,阶段 9 接上调用即可。


---

## 四、目录

```
app/
├── docs/          规格文档(唯一准绳,实现以它为准)
├── backend/       Spring Boot 后端,详见 backend/README.md
├── frontend/      Vue 3 前端,详见 frontend/README.md
└── .claude/
    └── launch.json   预览用的启动配置(backend / frontend)
```

---

## 五、实施过程中对文档的几处修正

文档是准绳,但有几处照抄会跑不起来、或者不满足实际需要,已按实际情况修正并写在代码注释里:

1. **JDBC 连接串的 `characterEncoding`**。文档写的是 `characterEncoding=utf8mb4`,MySQL 驱动会直接
   报 `Unsupported character encoding 'utf8mb4'`——该参数要的是 Java 字符集名,已改为 `UTF-8`。
   服务端字符集仍由建库时的 `utf8mb4` 决定,中文存读正常。
2. **`question.difficulty` 的实体类型**。DDL 里是 `TINYINT`,实体必须映射为 `Byte`;
   写成 `Integer`/`Short` 会被 `ddl-auto: validate` 拒绝启动。
3. **时间格式**。`spring.jackson.date-format` 只对 `java.util.Date` 生效,对 `LocalDateTime` 无效。
   已注册 Jackson 序列化器,`createdAt` 这类字段现在是 `yyyy-MM-dd HH:mm:ss`,不再是 ISO 的 `T` 格式。
4. **导入结果的 `skipped` 语义扩展**。文档只定义了校验失败的跳过。实际用下来,
   **把同一份试卷重导一遍**是常事,不拦的话题库里会整份翻倍。现在导入前会按「科目 + 归一化题干」
   (去空白与中英文标点、英文转小写)比对库里和本批已有的题目,重复的**跳过而不报错**,
   结果里新增 `duplicates` 列表单独说明;重复**不计入**「失败超过一半就整份回滚」的判定,
   全部重复时也不建空试卷。

另外,`Map.of()` 生成的不可变 Map 在 `get(null)` 时会抛 NPE——题目可以不属于任何章节,
所以按 `chapterId` 建索引的地方统一用 `HashMap`。

---

## 六、下一阶段

已完成:阶段 0—4(骨架 / 科目章节 / 题库 / 批量导入 / 练习判分)+ 阶段 5 的排期内核
(`Sm2Scheduler` 与 `review_item` 已随练习一起落地,因为阶段 4 的验收标准要求答错后立刻进复习队列)。

| 阶段 | 内容 | 依赖 |
|---|---|---|
| 后端 5 / 前端 5 | **错题本 + 复习排期接口/页面** ✅ 已完成 | `review_item` 已建 |
| 后端 6 / 前端 6 | **统计看板** ✅ 已完成(overview / subjects / trend / chapters + ECharts 页面) |
| 后端 7 / 前端 7 | **设置页 + 定时提醒(微信通道)** ✅ 已完成 |
| 后端 8 / 前端 8 | 打包、PWA、云服务器部署 | 全部完成 |
| 后端 9 / 前端 9 | AI 老师接上真实调用(AiClient 与 Prompt 模板已就绪,差接口与讲解入口) | `DASHSCOPE_API_KEY` 已配好 |
| **已完成** | **PDF 试卷 → 导入 JSON 转换器**(导入页可直接上传识别,见第七节) | — |

> 说明:阶段 7 用的 `user_setting` 表还没建,今日页的「每日题量」暂用默认值 10,
> 表建好后自动读真实设置——见 `backend/.../setting/TableAvailability.java`。
> `review_item` / `study_log` 已随阶段 4 建立,今日页的到期数与打卡状态现在都是真实数据。

---

## 七、PDF 试卷识别

**管道已打通,并且导入页已有入口**:在「批量导入题库」页切到「上传试卷 PDF 识别」标签页,
选文件、填课程代码/试卷名/年份,点「开始识别」——几分钟后识别结果直接进到同一套预览表格,
核对完再入库。分三层,按文件类型自动选路:

1. **文字层 PDF** → PDFBox 直接抽文本的可能性已探明(本次的样张是扫描件,无文字层);
2. **扫描件 / 图片型 PDF** → PDFBox 逐页渲染成 JPEG(150 DPI),交给千问的**视觉模型**识别;
3. 识别结果 → 按 `03-api-spec.md` 第五节的导入格式产出 JSON → 走已有的批量导入接口入库。

接口(都已可用):

| 接口 | 用途 |
|---|---|
| `POST /api/v1/ai/parse-pdf` | 上传试卷 PDF,逐页识别成题目 JSON(**不写库**,先给前端预览) |
| `GET /api/v1/ai/diagnose` | 连通性自检:依次打几个官方入口,返回上游原始状态码与响应体,用来区分「密钥无效 / 入口不对 / 模型名不存在」 |
| `GET /api/v1/ai/health` | 配置自检:模型名、是否配好密钥(不返回密钥本身) |

### 识别策略上的几个决定

- **题型靠「卷面上长什么样」判定,不预设科目**:提示词里是一张通用对照表
  (四选一 → `SINGLE_CHOICE`、要写一段话的 → `SHORT_ANSWER`/`ESSAY`…),覆盖全部 13 种题型。
  只有英语卷(13000)的几条特殊规则——阅读判断改写 `T/F/NG`、填空类「一空一条」——才按科目代码开关;
- **题干自足**:阅读类题目的题干里带上对应原文,复习时不用翻回原卷。副作用是原文也进了全文索引,
  搜 `koala` 能把「Australia」那篇的 10 道阅读判断题全找出来;
- **一个空 = 一条题**:填句补文 / 填词补文 / 完形补文是「一段短文带 N 个空」,每个空各算一道题。
  题干抄整段原文,把**本题对应的那个空**写成 `__(36)__`,其余空保持原样,这样一眼能看出问的是哪个空。
  这是提示词里标了 ★★ 的一条——模型天然倾向于把 10 个空合成 1 条,不写死它就会那样输出;
- **题目优先,答案其次**:先把卷面上所有题目录进来,**绝不能因为「给不出答案」就丢掉整道题**。
  答不出的题 `answer` 留空、标 `DRAFT`,补完答案改成「已发布」才能进日常练习;
- **答案**:卷面没有答案时由模型代答,这些题同样标 `DRAFT`(待校对),
  预览页会用「待校对」标签和顶部计数提醒你核对;
- **分值来自大题说明**:分值常常只印一次(「本大题共 25 小题,每小题 2 分」),而那是第一页的事。
  解析器把已认出来的「大题 → 每题分值」**回灌给后面的页**,否则第 2 页往后会集体留空、
  简答题会被按默认 2 分入库;
- **阅读判断的选项**:卷面印的是 `A. True / B. False / C. Not Given`,入库时统一成 `T / F / NG`,
  与数据模型的约定一致;
- **完形补文**没有选项(给词根填形式),校验器为这一种题型放行「无选项」,前端预览同样放行。



### 密钥读取顺序

**进程环境变量 → Windows 用户环境变量**。加第二层兜底是因为 `setx` 只对之后启动的进程生效:
如果应用(或启动它的终端)先开着,`setx` 设的变量它读不到,用户明明「设过了」却调不通。
用 `setx` 后重启后端即可生效,不必重启整个桌面应用。

### 实测(2026-10-16)

**中文政治卷**:6 页扫描件《习近平新时代中国特色社会主义思想概论》(课程代码 15040)。
识别出 **31 题**(单选 25 + 简答 5 + 材料分析 1),与卷面完全一致,**分值合计正好 100 分**;
6 次调用、约 80 秒、1.4 万输入 + 0.7 万输出 token,零警告。

**英语卷**:7 页扫描件(提示词通用化**之前**的实测)。识别出 53 条,按题型是阅读判断 10、
阅读选择 5、概括段意与补全句子 11、填句补文 5、填词补文 10、完形补文 11、短文写作 1 ——
填空类**一空一条**,整体贴着本卷的 51 题。多出来的 2 条是跨页重复的「只有原文、没有答案」的空条目,
浏览器预览里会被标成不合格,一键跳过。英语的特殊规则(阅读判断改写 T/F/NG、填空类一空一条)
原样保留在提示词的「仅英语卷适用」一节,按科目代码 13000 生效。

### 踩过的坑

- **提示词一度是给英语卷写死的**(人设「英语试卷录入助手」+ 七大国定大题对照表)。结果一份中文政治卷
  被模型以「与分析要求不符」为由**整页拒识**,31 道只出了 8 道,还在 `warnings` 里写「该科目不含此类
  中文政治简答题」。修法是把题型判定换成**与科目无关的通用对照表**,只有英语卷的特殊规则按科目代码开关。
  教训:提示词里任何「本卷是 X 科」的硬编码,都会在换一份卷子时变成模型拒绝输出的理由;
- **分值只印在大题说明里**。解析器现在把已认出的「大题 → 每题分值」回灌给后面的页,
  否则第 2 页往后集体留空,简答题会按默认 2 分入库(实际 7 分);
- **协议、地址、底层 HTTP 客户端**三处都要对:Anthropic Messages(`/v1/messages` + `x-api-key`)、
  `token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic`、以及 JDK HttpClient
  (这台机器上默认的 HttpURLConnection 连这个 HTTPS 端点会被重置连接);
- 结构化提取请求加 `"thinking":{"type":"disabled"}` 关掉思考,速度从 50—140 秒/页降到 8—20 秒/页。

### 识别失败时的兜底

- **单页重试一次**:上游偶发抖动(`HTTP/1.1 header parser received no bytes`)或返回非法 JSON 时,
  同一页退避 1.5 秒后重发;两次都失败才跳过,并在提醒里写明原因。丢一页就是好几道题,值得这一试;
- **题号自报核对**:要求模型每页顺带报一个 `covered = [起始题号, 结束题号]`——让模型**读出题号**
  比让它**提取题目**容易得多。输出条数对不上就报「第 5 页能看到的题号是 27—29(共 3 道),
  但只输出了 0 道」,专门抓「看见了却没输出」这种最隐蔽的丢题;
- **英语卷按题型比对标准题数**:少了报漏识或合并,多了报跨页重复。**只对 13000 生效**——
  别的科目大题结构各不相同,拿这张表去比只会刷出一串没意义的警告。

---

## 八、提醒(微信通道)

**在哪配**:设置页 → 通道配置。支持两种:

| 通道 | 要填什么 | 怎么拿 |
|---|---|---|
| 企业微信群机器人 | Webhook 地址 | 群聊 → 群设置 → 群机器人 → 添加机器人 |
| Server酱 | SendKey | [sct.ftqq.com](https://sct.ftqq.com) 控制台 |

填完点「**发送测试提醒**」——它会**先保存再发**,微信里收到就说明通道可用;
失败会当场把原因显示出来(例如 `企业微信返回 93000:invalid webhook url`)。

**密钥安全**:`GET /settings` 只返回掩码(`…a1b2c3`),完整值不出服务端;
提交时把掩码原样传回视为「未修改」,传空串表示清除。

**调度规则**(每分钟检查一次,±1 分钟窗口):

- 早间:有到期题才发,文案带各科目到期数 + 跳转 `/practice/daily`;
- 晚间:当天**没做过题**才发(做过就不打扰),文案带连续打卡天数 + 跳转首页;
- **当天同类只发一条**,靠 `reminder_log` 的唯一键保证;
- 发送失败不抛异常,原因写进 `reminder_log.fail_reason`,设置页的「近 7 天提醒」能看到。

**两个配置项**:

- `study-exam.notify.app-base-url`(默认 `http://localhost:8080`)——提醒里的链接前缀。
  开发期前端在 `:5173`,想点链接直接进页面就改成 `http://localhost:5173`;上线后由后端托管前端产物,保持 `:8080` 即可。
- 兜底环境变量 `WECOM_WEBHOOK_URL` / `SERVERCHAN_KEY`:设置页没填时退回这两个值。

---

## 九、部署到服务器(数据随迁移一起带走)

Flyway 迁移 V1—V8 已经包含建表、四条初始科目,以及**本机当前数据快照**
(1354 道题、27 份试卷、错题本排期、练习与答题记录等)。因此全新服务器上
**不需要手工导入任何 SQL**,首次启动就会自动建表并灌好数据。

### 1. 打包

```bash
cd frontend && npm run build
mkdir -p ../backend/src/main/resources/static
cp -r dist/. ../backend/src/main/resources/static/
cd ../backend && mvn -DskipTests package
```

产物是 `backend/target/study-exam-backend-0.1.0.jar`。前端产物放进 `static/` 后由后端同源托管,
`/api` 与页面同域,服务器上不需要再配 CORS。

### 2. 服务器环境

```bash
mysql -uroot -p -e "CREATE DATABASE study_exam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

export DB_USERNAME=study_exam
export DB_PASSWORD='换成你的密码'
export DASHSCOPE_API_KEY='你的千问 Key'
java -jar study-exam-backend-0.1.0.jar
```

首次启动会自动执行 V1—V8;数据落在服务器自己的 MySQL 里,之后与本地互不影响。
提醒里的跳转链接记得改:`--study-exam.notify.app-base-url=http://<服务器地址>:8080`。

### 3. 数据快照的边界

V8 是 **2026-10-18 生成的快照**。之后在本机新导入的题目、新做的练习不会自动进入 V8。
需要把最新数据带到服务器时,加一个 V9 迁移重新导出一份即可(先 `mysqldump` 导出,
再把 `INSERT INTO` 换成 `INSERT IGNORE INTO`,这样在已有数据的机器上重复执行也不会产生副本):

```bash
mysqldump -uroot -p --default-character-set=utf8mb4 --no-create-info --complete-insert \
  --skip-comments --skip-add-locks --skip-disable-keys --single-transaction \
  --result-file=backend/src/main/resources/db/migration/V9__seed_latest.sql \
  study_exam chapter question paper paper_question practice_session answer_record review_item study_log import_log
```

不含在快照里的表:`subject` / `user` / `user_setting`(V1、V5 已建好同样的初始数据)、
`ai_call_log` / `reminder_log` / `ai_digest`(运行期遥测,换机器没有意义)。
