package com.studyexam.ai;

import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.config.AiProperties;
import com.studyexam.question.OptionItem;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.question.QuestionType;
import com.studyexam.question.dto.AiAnalysisRequest;
import com.studyexam.question.dto.AiAnalysisVO;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用 AI 按「科目 + 题型」生成具体答案和解析。
 *
 * <p>规则:
 * <ul>
 *   <li>主观题(简答 / 论述 / 材料分析 / 写作):输出【参考答案】(+ 写作的【译文】)+【解析】,
 *       答案写进 {@code question.analysis};写作题必须是英语范文,不能是中文写作指导。</li>
 *   <li>客观题(单选 / 多选 / 判断 / 阅读 / 填空):输出【答案】+【解析】。
 *       已有标准答案时原样保留、绝不覆盖;答案为空时才把 AI 的判断写回 {@code question.answer}。
 *       解析只保留【解析】正文,避免和题目自带的「正确答案」重复。</li>
 *   <li>科目可以配置专属提示词({@code subject.ai_prompt}),追加到系统提示词并优先遵守。</li>
 * </ul>
 *
 * <p>AI 调用可能几十秒,这里刻意不加 {@code @Transactional},读取与保存各走 Repository 自己的事务。
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);
    /** ai_call_log 的场景名,最长 32 字符。 */
    public static final String SCENE = "ANALYSIS";
    /** 短文写作要输出英文范文 + 译文 + 解析,1600 token 才够用。 */
    private static final int MAX_TOKENS = 1600;
    /** 输出兜底上限:写作范文 + 译文 + 解析通常 1000—1400 字,超过 4000 就截断。 */
    private static final int MAX_OUTPUT_CHARS = 4000;

    private final AiClient aiClient;
    private final AiProperties props;
    private final QuestionRepository questionRepository;
    private final SubjectRepository subjectRepository;

    public AiAnalysisService(AiClient aiClient, AiProperties props,
                             QuestionRepository questionRepository,
                             SubjectRepository subjectRepository) {
        this.aiClient = aiClient;
        this.props = props;
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
    }

    /** 只生成不写库(编辑页预览用)。 */
    public AiAnalysisVO generate(AiAnalysisRequest req) {
        Subject subject = requireSubject(req.subjectId());
        QuestionType type = requireType(req.type());
        validate(type, req.stem(), req.options(), req.answer());
        String content = call(type, subject, req.stem(), req.options(), req.answer(),
                req.answerPoints(), null);
        return toResult(type, req.answer(), content);
    }

    /**
     * 为指定题目生成答案/解析并保存。
     *
     * <p>解析一定写回;客观题的答案只在原答案为空时补,已有答案绝不覆盖。
     */
    public AiAnalysisVO generateAndSave(Long questionId) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.QUESTION_NOT_FOUND,
                        "题目不存在:id=" + questionId));
        Subject subject = requireSubject(q.getSubjectId());
        QuestionType type = requireType(q.getType());
        validate(type, q.getStem(), q.getOptions(), q.getAnswer());

        String content = call(type, subject, q.getStem(), q.getOptions(), q.getAnswer(),
                q.getAnswerPoints(), q.getId());
        AiAnalysisVO result = toResult(type, q.getAnswer(), content);

        q.setAnalysis(result.analysis());
        if (type.isObjective()
                && (q.getAnswer() == null || q.getAnswer().isBlank())
                && result.answer() != null) {
            q.setAnswer(result.answer());
            log.info("AI 补全客观题答案 questionId={} answer={}", questionId, result.answer());
        }
        questionRepository.save(q);
        log.info("AI 已生成答案/解析 questionId={} type={} 答案={} 解析字数={}",
                questionId, type, result.answer(), result.analysis().length());
        return result;
    }

    // ── 内部 ────────────────────────────────────────────────

    private String call(QuestionType type, Subject subject, String stem, List<OptionItem> options,
                        String answer, List<String> points, Long questionId) {
        AiClient.AiRequest request = new AiClient.AiRequest(
                SCENE,
                props.getModel(),
                systemPrompt(subject),
                PromptTemplates.analysis(type, subject.getName(), stem, options, answer, points),
                List.of(),
                MAX_TOKENS,
                0.4,
                false, // 答案生成不需要长思考,关掉以降低延迟与 token
                subject.getId(),
                questionId);
        String content = aiClient.chat(request).content();
        return cleanOutput(content);
    }

    /** 通用人设 + 当前科目 + 科目专属提示词(优先遵守)。 */
    private String systemPrompt(Subject subject) {
        StringBuilder sb = new StringBuilder(PromptTemplates.ANALYSIS_ROLE);
        sb.append("\n【当前科目】").append(subject.getName());
        if (subject.getShortName() != null && !subject.getShortName().isBlank()) {
            sb.append("(").append(subject.getShortName()).append(")");
        }
        sb.append('\n');
        if (subject.getAiPrompt() != null && !subject.getAiPrompt().isBlank()) {
            sb.append("【本学科专属要求,优先遵守;但不要改变输出板块结构】\n")
                    .append(subject.getAiPrompt().trim())
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * 客观题从 AI 输出里取【答案】,并把【解析】正文留下;主观题整段保留(板块由前端渲染)。
     */
    private AiAnalysisVO toResult(QuestionType type, String providedAnswer, String content) {
        if (!type.isObjective()) {
            return new AiAnalysisVO(null, content);
        }
        String rawAnswer = providedAnswer != null && !providedAnswer.isBlank()
                ? providedAnswer
                : extractSection(content, "答案");
        if (rawAnswer == null) {
            // 模型偶尔会写成【正确答案】,兼容一下
            rawAnswer = extractSection(content, "正确答案");
        }
        String answer = normalizeAnswer(type, rawAnswer);
        String explanation = extractSection(content, "解析");
        String analysis = explanation != null ? explanation : removeSection(content, "答案");
        if (analysis.isBlank()) {
            analysis = content;
        }
        return new AiAnalysisVO(answer, analysis);
    }

    /**
     * 取【标题】板块的正文;找不到返回 null。
     * 用非贪婪 + 下一个【 或文本结尾作为边界,能处理多板块输出。
     */
    private String extractSection(String text, String title) {
        Matcher m = Pattern.compile("【\\s*" + Pattern.quote(title) + "\\s*】\\s*(.*?)(?=\\n\\s*【|$)",
                Pattern.DOTALL).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    /** 删掉某个板块,保留其余内容。 */
    private String removeSection(String text, String title) {
        return text.replaceAll("【\\s*" + Pattern.quote(title) + "\\s*】[\\s\\S]*?(?=\\n\\s*【|$)", "")
                .trim();
    }

    /** 把 AI 的答案规范成题库使用的格式(多选升序、判断 T/F/NG、填空保留原文)。 */
    private String normalizeAnswer(QuestionType type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim()
                .replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "")
                .replaceFirst("^(答案|正确答案)\\s*[:：]\\s*", "")
                .trim();
        if (s.isEmpty()) {
            return null;
        }
        String upper = s.toUpperCase(Locale.ROOT);
        return switch (type) {
            case MULTIPLE_CHOICE -> {
                // 优先按「单个字母」切分(A、B、D / A B D),否则退化为去除非字母字符
                Matcher m = Pattern.compile("\\b([A-Za-z])\\b").matcher(s);
                StringBuilder letters = new StringBuilder();
                while (m.find()) {
                    letters.append(m.group(1).toUpperCase(Locale.ROOT));
                }
                String rawLetters = letters.length() > 0
                        ? letters.toString()
                        : upper.replaceAll("[^A-Z]", "");
                if (rawLetters.isEmpty()) {
                    yield null;
                }
                yield rawLetters.chars().distinct().sorted()
                        .mapToObj(c -> String.valueOf((char) c))
                        .reduce("", String::concat);
            }
            case SINGLE_CHOICE, READING_CHOICE -> {
                Matcher m = Pattern.compile("\\b([A-Za-z])\\b").matcher(s);
                if (m.find()) {
                    yield m.group(1).toUpperCase(Locale.ROOT);
                }
                Matcher any = Pattern.compile("[A-Za-z]").matcher(s);
                yield any.find() ? any.group().toUpperCase(Locale.ROOT) : null;
            }
            case TRUE_FALSE, READING_JUDGE -> {
                if (upper.contains("NG") || upper.contains("NOT GIVEN") || upper.contains("NOTGIVEN")
                        || s.contains("未提及")) {
                    yield "NG";
                }
                if (s.contains("对") || s.contains("正确") || s.contains("是") || upper.contains("T")) {
                    yield "T";
                }
                if (s.contains("错") || s.contains("否") || upper.contains("F")) {
                    yield "F";
                }
                yield null;
            }
            default -> {
                String cleaned = s.replaceAll("\\s+", " ").trim();
                yield cleaned.isEmpty() ? null : cleaned;
            }
        };
    }

    private void validate(QuestionType type, String stem, List<OptionItem> options, String answer) {
        if (stem == null || stem.isBlank()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "题干为空,无法生成答案");
        }
        boolean choice = type == QuestionType.SINGLE_CHOICE
                || type == QuestionType.MULTIPLE_CHOICE
                || type == QuestionType.READING_CHOICE;
        boolean hasOptions = options != null
                && options.stream().anyMatch(o -> o != null && o.text() != null && !o.text().isBlank());
        if (choice && !hasOptions && (answer == null || answer.isBlank())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "这道选择题还没有选项或标准答案,无法生成");
        }
    }

    private Subject requireSubject(Long subjectId) {
        if (subjectId == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "科目不能为空");
        }
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SUBJECT_NOT_FOUND,
                        "科目不存在:id=" + subjectId));
    }

    private QuestionType requireType(String type) {
        return QuestionType.find(type)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID,
                        "未知题型:\"" + type + "\""));
    }

    /** 去掉模型偶尔包上的 ``` 代码块,压缩多余空行,并兜底截断。 */
    private String cleanOutput(String raw) {
        String text = raw == null ? "" : raw.trim();
        text = text.replaceAll("^```[a-zA-Z]*\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
        if (text.length() > MAX_OUTPUT_CHARS) {
            text = text.substring(0, MAX_OUTPUT_CHARS) + "…";
        }
        return text;
    }
}
