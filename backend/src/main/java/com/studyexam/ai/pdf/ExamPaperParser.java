package com.studyexam.ai.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyexam.ai.AiClient;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.config.AiProperties;
import com.studyexam.question.dto.ImportItemDTO;
import com.studyexam.question.dto.ImportPaperDTO;
import com.studyexam.question.dto.QuestionImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PDF 试卷识别:扫描件/图片型 PDF → 可直接入库的题目 JSON。
 *
 * <p>流程:PDF 逐页渲染成图片 → 每页交给千问的视觉模型 → 按页累积「已出现的短文」作为
 * 后续页的上下文 → 汇总成 {@link QuestionImportRequest},交给已有的批量导入接口入库。
 * 识别结果**不直接写库**:先返回给前端预览、人工修正,再由用户点导入——这与录题的既定流程一致。
 *
 * <p>为什么把前面的短文回灌给模型:自考英语的阅读题里,原文往往在第 1 页,而题目在第 2 页。
 * 只给单页会导致题干缺少原文,复习时又得翻回原卷。回灌之后题干里能带上对应段落,题目才自足。
 *
 * <p><b>提示词与科目无关</b>:题型靠「卷面上长什么样」判定,不预设某一份卷子的大题结构。
 * 早期版本按英语七大题写死了对照表,结果一份中文政治卷被模型以「与分析要求不符」为由
 * 整页拒识(见 git 历史与 README 第七节)。只有英语卷(13000)的题数与填空题规则
 * 才是按科目代码开关的。
 */
@Service
public class ExamPaperParser {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperParser.class);
    private static final String SCENE = "PARSE_PDF";
    private static final int MAX_TOKENS = 16384;
    /** 回灌给模型的短文上下文上限,避免越到后面输入越长。 */
    private static final int MAX_CONTEXT_CHARS = 6000;
    private static final int MAX_STEM_CHARS = 1900;

    /** 「一段短文带 N 个空」的题型:一空一条,每一条必须带 blankNo。 */
    private static final Set<String> BLANK_TYPES = Set.of("SENTENCE_FILL", "WORD_FILL", "CLOZE_TRANSFORM");

    /**
     * 一页最多试几次。丢一页就是好几道题,而上游偶发抖动(读到一半连接被重置、
     * 返回空 body)与非法 JSON 都是重试一次就能救回来的,所以不吝啬这一次重试。
     */
    private static final int PAGE_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 1500;

    /** 英语(专升本)的课程代码:只有这门课的题型与题数是固定的,可以按表核对。 */
    private static final String ENGLISH_SUBJECT_CODE = "13000";

    /**
     * 英语卷七大题的标准题数,只用来做识别后的自检 —— **仅对 13000 生效**。
     *
     * <p>别的科目(政治、专业课等)大题名与题数各不相同,拿这张表去比只会产生一串
     * 「本卷应有 READING_JUDGE 的题目」这种毫无意义的警告,所以按科目代码开关。
     */
    private static final Map<String, Integer> STANDARD_COUNT_BY_TYPE = Map.of(
            "READING_JUDGE", 10,
            "READING_CHOICE", 5,
            "SECTION_MATCH", 10,
            "SENTENCE_FILL", 5,
            "WORD_FILL", 10,
            "CLOZE_TRANSFORM", 10,
            "WRITING", 1);

    private final AiProperties props;
    private final AiClient aiClient;
    private final PdfPageRenderer renderer;
    private final ObjectMapper objectMapper;

    public ExamPaperParser(AiProperties props, AiClient aiClient,
                           PdfPageRenderer renderer, ObjectMapper objectMapper) {
        this.props = props;
        this.aiClient = aiClient;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    public record ParseResult(QuestionImportRequest request, int totalPages, int parsedPages,
                              int calls, int tokensIn, int tokensOut, long durationMs,
                              List<String> warnings) {
    }

    public ParseResult parse(byte[] pdfBytes, String subjectCode, String subjectName, String paperName,
                             Integer year, boolean generateAnswers) {
        if (!PdfPageRenderer.looksLikePdf(pdfBytes)) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR, "这不是一个 PDF 文件");
        }
        long start = System.currentTimeMillis();
        PdfPageRenderer.Rendered rendered = renderer.render(
                pdfBytes, props.getPdfRenderDpi(), props.getPdfMaxPages());

        List<String> warnings = new ArrayList<>();
        boolean truncated = rendered.totalPages() > rendered.pages().size();
        if (truncated) {
            warnings.add("这份 PDF 共 " + rendered.totalPages() + " 页,本次只识别前 "
                    + rendered.pages().size() + " 页(可在配置里调整 pdf-max-pages)");
        }

        List<ImportItemDTO> items = new ArrayList<>();
        List<String> knownPassages = new ArrayList<>();
        // 卷面常常只在大题说明里写一次分值(「本大题共 25 小题,每小题 2 分」),
        // 而那是第一页的事——后面几页看不到说明,模型就会把分值留空。
        // 把已经认出来的「大题 → 每题分值」回灌给后面的页,和短文回灌是同一个思路。
        Map<String, BigDecimal> knownSectionScores = new LinkedHashMap<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        Map<String, Integer> blankNoMissing = new LinkedHashMap<>();
        int calls = 0;
        int tokensIn = 0;
        int tokensOut = 0;

        int perCall = Math.max(1, props.getPdfPagesPerCall());
        for (int i = 0; i < rendered.pages().size(); i += perCall) {
            List<PdfPageRenderer.RenderedPage> batch = rendered.pages()
                    .subList(i, Math.min(i + perCall, rendered.pages().size()));

            List<AiClient.ImagePart> images = batch.stream()
                    .map(p -> new AiClient.ImagePart("image/jpeg", p.jpeg()))
                    .toList();
            String pageLabel = batch.size() == 1
                    ? "第 " + batch.get(0).pageNumber() + " 页"
                    : "第 " + batch.get(0).pageNumber() + "—" + batch.get(batch.size() - 1).pageNumber() + " 页";

            JsonNode page = null;
            String lastError = null;
            for (int attempt = 1; attempt <= PAGE_ATTEMPTS && page == null; attempt++) {
                try {
                    AiClient.AiReply reply = aiClient.chat(AiClient.AiRequest.vision(
                            SCENE, props.getVisionModel(), systemPrompt(generateAnswers),
                            userPrompt(pageLabel, subjectCode, subjectName, generateAnswers,
                                    knownPassages, knownSectionScores),
                            images, MAX_TOKENS).withoutThinking());
                    calls++;
                    tokensIn += reply.tokensIn();
                    tokensOut += reply.tokensOut();

                    page = extractJson(reply.content());
                    if (page == null) {
                        lastError = "返回的不是合法 JSON";
                        log.warn("{} 第 {} 次尝试返回非法 JSON,原始返回:{}", pageLabel, attempt,
                                reply.content().substring(0, Math.min(200, reply.content().length())));
                    }
                } catch (BusinessException e) {
                    lastError = e.getMessage();
                    log.warn("{} 第 {} 次识别失败:{}", pageLabel, attempt, e.getMessage());
                }
                if (page == null && attempt < PAGE_ATTEMPTS) {
                    backoffBeforeRetry();
                }
            }
            if (page == null) {
                // 一页要丢掉好几道题,所以失败了要重试;两次都不行才跳过,并且把原因留在提醒里
                warnings.add(pageLabel + " 识别失败,已跳过(重试 " + PAGE_ATTEMPTS + " 次):" + lastError);
                continue;
            }

            int before = items.size();
            collectPassages(page.path("passages"), knownPassages);
            collectItems(page.path("items"), items, warnings, pageLabel, typeCounts, blankNoMissing);
            rememberSectionScores(items, before, knownSectionScores);
            checkCoverage(page.path("covered"), before, items.size(), pageLabel, warnings);
            page.path("warnings").forEach(w -> {
                String text = w.asText("");
                if (!text.isBlank()) {
                    warnings.add(pageLabel + ":" + text);
                }
            });
        }

        checkCompleteness(typeCounts, blankNoMissing, subjectCode, truncated, warnings);

        if (items.isEmpty()) {
            throw BusinessException.of(ErrorCode.AI_UNAVAILABLE,
                    "没有从这份 PDF 里识别出任何题目,请确认它是试卷而不是封面或答案页");
        }

        ImportPaperDTO paper = new ImportPaperDTO(
                paperName == null || paperName.isBlank() ? "PDF 识别导入的试卷" : paperName,
                "REAL", year, 100, 150,
                "由 PDF 识别生成,建议逐题核对答案后再使用");
        QuestionImportRequest request = new QuestionImportRequest(subjectCode, paper, items);

        long cost = System.currentTimeMillis() - start;
        log.info("PDF 识别完成:{} 页,识别出 {} 题,调用 {} 次,输入 {} tokens,输出 {} tokens,耗时 {}ms",
                rendered.pages().size(), items.size(), calls, tokensIn, tokensOut, cost);
        return new ParseResult(request, rendered.totalPages(), rendered.pages().size(),
                calls, tokensIn, tokensOut, cost, warnings);
    }

    private void backoffBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Prompt ─────────────────────────────────────────────

    private String systemPrompt(boolean generateAnswers) {
        return """
                你是自考试卷录入助手。用户会给你一份试卷的页面图片(扫描件),你要把上面的题目
                逐题转成结构化 JSON,供他的题库系统入库。

                **任何科目都要照常识别。** 自考科目五花八门——政治、语文、英语、数学、专业课,
                题型各不相同。按下面这张表判定就行,**不要因为「题型和某个预设对不上」就拒绝输出**,
                更不要因此一条都不给。

                ## 一、题型对照表(按卷面上长什么样判定)

                | 卷面上长这样 | type |
                |---|---|
                | 单项选择题:四个选项选一个 | SINGLE_CHOICE |
                | 多项选择题:五个选项选多个 | MULTIPLE_CHOICE |
                | 判断题:填「对 / 错」或「√ / ×」 | TRUE_FALSE |
                | 【英语】阅读判断:选项是 True / False / Not Given | READING_JUDGE |
                | 【英语】阅读理解选择:先给一篇短文,再就短文出选择题 | READING_CHOICE |
                | 【英语】概括段落大意、补全句子 | SECTION_MATCH |
                | 【英语】填句补文:短文挖空,给若干句子备选 | SENTENCE_FILL |
                | 【英语】填词补文:短文挖空,给若干单词备选 | WORD_FILL |
                | 【英语】完形补文:括号里给词根,填出正确形式 | CLOZE_TRANSFORM |
                | 简答题 | SHORT_ANSWER |
                | 论述题 | ESSAY |
                | 材料分析题:给一段材料,结合材料回答问题 | MATERIAL_ANALYSIS |
                | 短文写作 / 作文 | WRITING |

                拿不准时的兜底:有字母选项的一律 SINGLE_CHOICE;要写一段话回答的,
                卷面叫「简答」用 SHORT_ANSWER、叫「论述」用 ESSAY、先给材料再问用
                MATERIAL_ANALYSIS、叫「写作 / 作文」用 WRITING。

                ## 二、必守规则

                1. **只输出卷面上真实存在的题目**,题号原样照抄。不要编造、不要补齐、不要重复输出。
                   题目跨页时(题干或选项没显示全),只输出这一页可见的部分,并在 warnings 里说明。

                2. ★★ **题目优先,答案其次**。先把**这一页上所有题目**都输出,一道都不能少。
                   **绝不能因为「给不出答案」就不输出这道题**——录进题目是第一位的,
                   答案用户自己会补。这一条比下面的答案规则更重要。

                3. **答案**:%s
                   - 卷面上印了答案 → 照抄;
                   - 卷面没印 → 按上面那句话处理;
                   - 实在确定不了(数学计算、没有原文的阅读理解等)→ **answer 留空**,
                     并在 warnings 里说明哪几题没给答案。**不要瞎猜。**
                   - 主观题(简答 / 论述 / 材料分析 / 写作)没有 answer,改给 `answerPoints`
                     (参考答案要点数组);想不出要点就给空数组。

                4. **status 一律填 DRAFT**。识别出来的题目都要人工核对一遍,先标 DRAFT 是对的。

                5. **题干必须自足**。阅读类题目要把该题对应的原文抄进题干开头,格式:
                   `【原文 · 短标题】原文段落…\\n\\n题干句子`
                   原文照抄,**不要翻译、不要改写、不要压缩**。原文若已出现在下面给你的
                   「本卷前面已出现的短文」里,就从那里取。
                   材料分析题同理:材料抄进题干,前面加 `【材料】`。

                6. **sectionName 和 chapterName 都填卷面上的大题名**,原样照抄
                   (如「一、单项选择题」「二、简答题」「三、材料题」)。
                   **不要套用别的卷子的大题名。**

                7. **分值**:分值**常常只写在大题说明里**(如「本大题共 25 小题,每小题 2 分」),
                   那就把这个分值填到该大题下**每一题**的 score 上,**每一题都要填**;
                   不要因为这一页看不到大题说明就留空——本卷前面已标明的分值我会在下面告诉你。
                   卷面上确实没有分值的才留空。difficulty 按你的判断填 1(简单)/2(中等)/3(困难)。

                8. **选项要完整**:客观题的 options 按卷面顺序全给,一个都不能少。

                ## 三、仅英语卷适用的两条(其它科目请忽略)

                - 阅读判断的选项要换写法:卷面印的是 A. True / B. False / C. Not Given,
                  要输出成 key 为 T / F / NG 的选项,answer 也用 T / F / NG,不要用 A/B/C。
                - 填句补文 / 填词补文 / 完形补文这三种「一段短文带 N 个空」的题型,
                  ★★ **一个空 = 一条题目**:有几个空就输出几条,不是一段短文一条。每条的写法:
                  `blankNo` 填该空的卷面题号(整数,如 36);`stem` 抄整段原文,并把
                  **本题对应的那个空**改写成 `__(36)__`,其余空保持原来的下划线;
                  `options` 给**完整库**——填词补文 12 个词(key 用 A—L)、
                  填句补文 6 句(key 用 A—F)、完形补文没有选项给空数组 [];
                  `answer` 填该空的词 / 句子字母 / 变形后的词。
                  把 10 个空合并成 1 条 = 错。宁可多输出几条,也不要合并。

                ## 四、输出格式

                输出**只有一个 JSON 对象**,不要 Markdown 代码块标记,不要任何解释文字:

                {
                  "covered": [5, 12],
                  "passages": [ { "title": "短标题", "text": "这一页出现的原文全文" } ],
                  "items": [
                    {
                      "sectionName": "一、单项选择题",
                      "type": "SINGLE_CHOICE",
                      "chapterName": "一、单项选择题",
                      "stem": "马克思主义哲学的基本问题是（  ）",
                      "options": [ {"key":"A","text":"物质和意识的关系问题"},
                                   {"key":"B","text":"思维和存在的关系问题"} ],
                      "answer": "B",
                      "analysis": "恩格斯指出哲学的基本问题是思维和存在的关系问题。",
                      "score": 2.0,
                      "difficulty": 1,
                      "status": "DRAFT"
                    },
                    {
                      "sectionName": "二、简答题",
                      "type": "SHORT_ANSWER",
                      "chapterName": "二、简答题",
                      "stem": "简述实现高水平科技自立自强的重大意义。",
                      "options": [],
                      "answer": "",
                      "answerPoints": [ "是国家强盛之基、安全之要", "是推动高质量发展的必然要求" ],
                      "analysis": "",
                      "score": 7.0,
                      "difficulty": 2,
                      "status": "DRAFT"
                    }
                  ],
                  "warnings": [ "第 46 题的图看不清,未识别" ]
                }

                `covered` 是**这一页上你能看见的题号的起止** `[起始, 结束]`,这一页没有题目就填 `[]`。
                例如 `[5, 12]` 表示这一页有第 5—12 题共 8 道,那 items 里就该有 8 条。
                这个字段专门用来核对有没有漏题,**务必按实际看到的如实填**。
                """.formatted(generateAnswers
                ? "卷面上没有答案(别去猜「官方答案」),所以你要自己把题做一遍,给出正确答案"
                : "本次**不要代答**:卷面没印答案的题目,answer 留空即可");
    }

    private String userPrompt(String pageLabel, String subjectCode, String subjectName,
                              boolean generateAnswers, List<String> knownPassages,
                              Map<String, BigDecimal> knownSectionScores) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是自考《").append(subjectName == null || subjectName.isBlank() ? "未知科目" : subjectName)
                .append("》(课程代码 ").append(subjectCode).append(")试卷的")
                .append(pageLabel).append("。请把这一页上的题目识别出来。\n");

        if (!knownSectionScores.isEmpty()) {
            sb.append("\n【本卷前面各大题已标明的分值(本页题目属于这些大题时,请照样填上 score)】\n");
            knownSectionScores.forEach((section, score) ->
                    sb.append("  ").append(section).append(" 每题 ").append(score.stripTrailingZeros())
                            .append(" 分\n"));
        }
        if (!knownPassages.isEmpty()) {
            sb.append("\n【本卷前面已出现的短文(题干需要原文时从这里取)】\n");
            String joined = String.join("\n\n", knownPassages);
            sb.append(joined.length() > MAX_CONTEXT_CHARS
                    ? joined.substring(joined.length() - MAX_CONTEXT_CHARS)
                    : joined);
            sb.append('\n');
        }
        if (!generateAnswers) {
            sb.append("\n注意:本次不要代答,answer 留空。题目仍然要一道不漏地输出。\n");
        }
        return sb.toString();
    }

    // ── 解析返回 ────────────────────────────────────────────

    /** 从模型返回里抠出 JSON:容忍 ```json 代码块、前后寒暄、以及截断。 */
    private JsonNode extractJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String text = content.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", start < 0 ? fence + 3 : start);
            if (start > 0) {
                text = end > start ? text.substring(start + 1, end) : text.substring(start + 1);
            }
        }
        int brace = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (brace < 0 || last <= brace) {
            return null;
        }
        String candidate = text.substring(brace, last + 1);
        try {
            return objectMapper.readTree(candidate);
        } catch (Exception e) {
            log.debug("JSON 解析失败:{}", e.getMessage());
            return null;
        }
    }

    private void collectPassages(JsonNode passages, List<String> known) {
        if (passages == null || !passages.isArray()) {
            return;
        }
        for (JsonNode p : passages) {
            String title = p.path("title").asText("短文");
            String text = p.path("text").asText("");
            if (!text.isBlank()) {
                known.add("【" + title + "】" + text);
            }
        }
    }

    private void collectItems(JsonNode items, List<ImportItemDTO> target, List<String> warnings,
                              String pageLabel, Map<String, Integer> typeCounts,
                              Map<String, Integer> blankNoMissing) {
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode node : items) {
            try {
                String type = node.path("type").asText("").trim().toUpperCase();
                String section = node.path("sectionName").asText("").trim();
                ImportItemDTO item = objectMapper.treeToValue(withoutBlankNo(node), ImportItemDTO.class);
                if (item == null || item.stem() == null || item.stem().isBlank()) {
                    continue;
                }
                if (item.stem().length() > MAX_STEM_CHARS) {
                    warnings.add(pageLabel + ":" + shorten(item.stem())
                            + " 的题干超过 " + MAX_STEM_CHARS + " 字,已截断");
                    item = withStem(item, item.stem().substring(0, MAX_STEM_CHARS));
                }
                target.add(item);
                typeCounts.merge(type, 1, Integer::sum);
                if (BLANK_TYPES.contains(type) && !hasBlankNo(node)) {
                    blankNoMissing.merge(section.isBlank() ? "填空类大题" : section, 1, Integer::sum);
                }
            } catch (Exception e) {
                warnings.add(pageLabel + ":有一条题目结构无法解析,已跳过(" + e.getMessage() + ")");
            }
        }
    }

    /** 记住本页新认出来的「大题 → 每题分值」,供后面的页参考。 */
    private void rememberSectionScores(List<ImportItemDTO> items, int from,
                                       Map<String, BigDecimal> known) {
        for (int i = from; i < items.size(); i++) {
            ImportItemDTO item = items.get(i);
            if (item.score() != null && item.sectionName() != null && !item.sectionName().isBlank()) {
                known.putIfAbsent(item.sectionName().trim(), item.score());
            }
        }
    }

    /** blankNo 只是识别阶段的辅助字段(用来判断有没有漏空),不进 DTO,免得流到导入接口去。 */
    private JsonNode withoutBlankNo(JsonNode node) {
        if (!node.has("blankNo")) {
            return node;
        }
        ObjectNode copy = node.deepCopy();
        copy.remove("blankNo");
        return copy;
    }

    private boolean hasBlankNo(JsonNode node) {
        JsonNode blankNo = node.path("blankNo");
        return blankNo.isNumber() || (blankNo.isTextual() && blankNo.asText().trim().matches("\\d+"));
    }

    /**
     * 识别结果的完整性自检。只往 warnings 里写,不拦截——结果本来就要人工核对,
     * 但「少了一整个大题的一半」这种事必须让人一眼看见,否则会以为录全了。
     */
    /**
     * 模型自报的题号范围 vs 实际输出条数。
     *
     * <p>本次踩过的坑:模型明明看见了第 27、28、29 题,却一条都没输出。让模型**读出题号**
     * 比让它**提取题目**容易得多,所以要求它每页顺带报一个 covered = [起始题号, 结束题号];
     * 只要输出条数对不上,就能立刻发现「看见了但没给」。
     */
    private void checkCoverage(JsonNode covered, int before, int after, String pageLabel,
                               List<String> warnings) {
        if (covered == null || !covered.isArray() || covered.size() < 2) {
            return;
        }
        int from = covered.get(0).asInt(0);
        int to = covered.get(1).asInt(0);
        if (from <= 0 || to < from) {
            return;
        }
        int produced = after - before;
        int expected = to - from + 1;
        if (produced < expected) {
            warnings.add(pageLabel + " 上能看到的题号是 " + from + "—" + to + "(共 " + expected
                    + " 道),但只输出了 " + produced + " 道,请对照原卷补录");
        }
    }

    private void checkCompleteness(Map<String, Integer> typeCounts,
                                   Map<String, Integer> blankNoMissing,
                                   String subjectCode, boolean truncated, List<String> warnings) {
        for (Map.Entry<String, Integer> e : blankNoMissing.entrySet()) {
            warnings.add(e.getKey() + " 有 " + e.getValue() + " 条没标出空号(blankNo),"
                    + "多半是把几个空合并成了一条,请对照原卷拆开");
        }
        if (truncated || !ENGLISH_SUBJECT_CODE.equals(subjectCode)) {
            // 只识别了前几页题数本来就会少;非英语卷的大题结构各不相同,这张表不适用
            return;
        }
        for (Map.Entry<String, Integer> e : STANDARD_COUNT_BY_TYPE.entrySet()) {
            int actual = typeCounts.getOrDefault(e.getKey(), 0);
            if (actual == 0) {
                warnings.add("本卷应有「" + e.getKey() + "」的题目,但一条都没识别出来,请检查对应页面");
            } else if (actual < e.getValue()) {
                warnings.add(e.getKey() + " 本卷应有 " + e.getValue() + " 题,实际只识别出 "
                        + actual + " 题,可能漏识或被合并");
            } else if (actual > e.getValue()) {
                warnings.add(e.getKey() + " 本卷应有 " + e.getValue() + " 题,实际识别出 "
                        + actual + " 题,多出来的通常是跨页重复,或只有原文、没答案的空条目,导入前请核对");
            }
        }
    }

    private ImportItemDTO withStem(ImportItemDTO item, String stem) {
        return new ImportItemDTO(item.sectionName(), item.type(), item.chapterName(), stem,
                item.options(), item.answer(), item.answerPoints(), item.analysis(),
                item.sourceYear(), item.score(), item.difficulty(), item.status());
    }

    private String shorten(String stem) {
        return stem.length() > 20 ? stem.substring(0, 20) + "…" : stem;
    }
}