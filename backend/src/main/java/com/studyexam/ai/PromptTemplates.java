package com.studyexam.ai;

import com.studyexam.question.OptionItem;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionType;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * AI 场景的 Prompt 模板。
 *
 * <p>集中在这里,不在 Service 里拼字符串——既方便快照测试,也避免同一套人设在多个地方写歪。
 *
 * <p>人设与硬约束(来源:docs/software 需求总结 3.6 与 docs/study-exam/backend/01-architecture.md):
 * 你是自考冲刺辅导老师,学员目标是 60 分及格。讲解必须——
 * 先一句话点名考点;用大白话说明正确项为什么对、干扰项错在哪;给一句记忆提示;
 * 若学员选了错误项,指出他混淆在哪里;全文不超过 120 字,不写学术引申,像考前划重点一样讲。
 *
 * <p>本类接的是通义千问 DashScope 的 OpenAI 兼容接口,消息体就是 system + user 两条。
 */
public final class PromptTemplates {

    /** 讲解类输出的字数上限,超过视为不合格输出。 */
    public static final int MAX_EXPLAIN_CHARS = 120;

    private PromptTemplates() {
    }

    /** 共享人设。所有场景的 system 消息都以它开头,保证语气一致。 */
    public static final String ROLE = """
            你是「自考冲刺辅导老师」,学员是在职备考的成年人,目标是 60 分及格,不是高分。
            讲解风格要求:
            1. 先一句话点名考点(如「考点:哲学基本问题」)。
            2. 用大白话说明正确项为什么对、干扰项错在哪,不引用教材原文。
            3. 给一句好记的提示(口诀或类比均可)。
            4. 如果学员选了错误项,直接指出他混淆在哪里。
            5. 全文不超过 120 字,不写学术引申,不说「建议你」「你可以」这类废话。
            6. 只输出讲解正文,不要标题、不要 Markdown 标记、不要换行超过 3 次。
            """;

    /** 场景一:精简解析。命中缓存时不调用模型。 */
    public static String explain(Question q, String userAnswer) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为下面这道自考真题写一段精简解析。\n\n");
        sb.append("【题型】").append(label(q)).append('\n');
        sb.append("【题干】").append(q.getStem()).append('\n');

        if (q.getOptions() != null && !q.getOptions().isEmpty()) {
            sb.append("【选项】\n");
            for (OptionItem o : q.getOptions()) {
                sb.append("  ").append(o.key()).append(". ").append(o.text()).append('\n');
            }
        }
        if (q.getAnswer() != null && !q.getAnswer().isBlank()) {
            sb.append("【正确答案】").append(q.getAnswer()).append('\n');
        }
        if (q.getAnalysis() != null && !q.getAnalysis().isBlank()) {
            sb.append("【原解析(供参考,请改写得更口语)】").append(q.getAnalysis()).append('\n');
        }
        List<String> points = q.getAnswerPoints();
        if (points != null && !points.isEmpty()) {
            sb.append("【参考答案要点】\n");
            sb.append(points.stream().map(p -> "  - " + p).collect(Collectors.joining("\n"))).append('\n');
        }
        if (userAnswer != null && !userAnswer.isBlank() && !userAnswer.equals(q.getAnswer())) {
            sb.append("【学员选了】").append(userAnswer).append("(选错了,请指出他混淆在哪里)\n");
        }
        sb.append("\n请按人设要求输出,不超过 120 字。");
        return sb.toString();
    }

    /** 场景二:根据参考答案要点生成主观题解析(写入 question.analysis)。 */
    public static final String ANALYSIS_ROLE = """
            你是「自考冲刺辅导老师」,学员是在职备考的成年人,目标是 60 分及格。
            现在要根据一道主观题的「参考答案要点」写一段简洁好记、可以直接背的解析,要求:
            1. 第一行一句话点明考点(如「考点:实践与认识的辩证关系」)。
            2. 中间用 ①②③ 分条列出采分点,每条不超过 30 字,写成考场上能直接写的话。
            3. 最后一行给一句好记的口诀或关键词串联。
            4. 全文 120—220 字,大白话,不引用教材原文,不写「建议你」「你可以」这类废话。
            5. 只输出解析正文,不要标题、不要 Markdown 标记、不要代码块。
            """;

    /** 主观题解析:把要点改写成简洁、好记、能直接背的答案。 */
    public static String analysis(QuestionType type, String stem, List<String> points) {
        String pointLines = points == null ? "" : points.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(p -> "  - " + p.trim())
                .collect(Collectors.joining("\n"));
        return """
                请为下面这道自考主观题写一段解析。

                【题型】%s
                【题干】%s
                【参考答案要点】
                %s

                请按人设要求输出,120—220 字;上面每一条要点都必须在解析里覆盖到。
                """.formatted(type.getLabel(), truncate(stem, 800), pointLines);
    }

    /** 场景三:举一反三,生成同考点变式题。不入库,由前端确认后再保存。 */
    public static String variant(Question q) {
        String optionKeys = q.getOptions() == null ? "A/B/C/D" :
                q.getOptions().stream().map(OptionItem::key).collect(Collectors.joining("/"));

        return """
                请针对下面这道自考真题,出一道**考点相同、问法不同**的变式题,难度与原题相当。

                【原题题型】%s
                【原题题干】%s
                【原题正确答案】%s

                要求:
                1. 考点必须与原题一致,不要换知识点。
                2. 只输出一个 JSON 对象,不要任何解释文字、不要 Markdown 代码块标记。
                3. JSON 结构严格如下(字段名不得改动):
                {
                  "type": "%s",
                  "stem": "变式题题干",
                  "options": [{"key":"A","text":"…"}, ...],
                  "answer": "正确答案",
                  "analysis": "一句话解析,不超过 60 字",
                  "difficulty": %d
                }
                4. 选项字母用 %s,必须给全,不要少。
                5. 客观题的 answer 只能是选项字母;主观题把 options 设为空数组并把 answer 设为参考答案要点数组的字符串。
                """.formatted(
                label(q),
                q.getStem(),
                q.getAnswer() == null ? "(主观题)" : q.getAnswer(),
                q.getType(),
                q.getDifficulty() == null ? 2 : q.getDifficulty().intValue(),
                optionKeys);
    }

    /** 场景四:记忆口诀。针对易混知识点,可复制保存。 */
    public static String mnemonic(String knowledgePoint) {
        return """
                考点/知识点:%s

                请为它编一句好记的口诀或顺口溜,帮助在考场上快速回忆。

                要求:
                1. 口诀本身不超过 40 字,朗朗上口,可以用谐音、首字连读、数字串。
                2. 口诀后另起一行,用一句话说明口诀对应什么内容(不超过 30 字)。
                3. 不要解释为什么这样编,不要客套话。
                """.formatted(knowledgePoint == null ? "(未指定)" : knowledgePoint.trim());
    }

    /** 场景五:考前串讲清单。按科目汇总高频考点,每科一份,可覆盖重生成。 */
    public static String digest(String subjectName, List<String> chapterNames, List<String> sampleStems) {
        String chapters = chapterNames == null || chapterNames.isEmpty()
                ? "(该科目尚未录入章节)"
                : chapterNames.stream().map(c -> "  - " + c).collect(Collectors.joining("\n"));

        String samples = "";
        if (sampleStems != null && !sampleStems.isEmpty()) {
            samples = "\n【该科目已录入的部分题干(供判断重点)】\n"
                    + IntStream.range(0, sampleStems.size())
                    .mapToObj(i -> "  " + (i + 1) + ". " + sampleStems.get(i))
                    .collect(Collectors.joining("\n"))
                    + "\n";
        }

        return """
                科目:%s

                【章节结构】
                %s
                %s
                请为这门课生成一份「考前必背清单」,用于考前一天快速过一遍。

                要求:
                1. 输出 8—12 点,每点一行,格式固定为:`N. 【考点名】必背内容`。
                2. 每点不超过 50 字,只写考场上要写的采分点,不解释。
                3. 按重要性从高到低排列,最可能考大题的放前面。
                4. 只输出清单本身,不要开头语、不要总结、不要 Markdown 标题。
                """.formatted(subjectName, chapters, samples);
    }

    /** 题干太长时截断,避免把整个题库塞进 prompt。 */
    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public static String label(Question q) {
        return QuestionType.find(q.getType()).map(QuestionType::getLabel).orElse(q.getType());
    }

}