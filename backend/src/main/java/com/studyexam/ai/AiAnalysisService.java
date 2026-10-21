package com.studyexam.ai;

import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.config.AiProperties;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.question.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用 AI 把主观题的「参考答案要点」改写成一段简洁好记的解析,写入 {@code question.analysis}。
 *
 * <p>适用题型:简答题 / 论述题 / 材料分析 / 短文写作(所有主观题)。客观题不处理 ——
 * 它们本来就有正确答案和简短解析,不需要再生成。
 *
 * <p>两个入口:
 * <ul>
 *   <li>{@link #generate} —— 只生成不写库,给编辑页预览(新建的题还没有 ID);</li>
 *   <li>{@link #generateAndSave} —— 生成后直接写进这道题的 {@code analysis}(题库列表批量用)。</li>
 * </ul>
 *
 * <p>模型与密钥沿用系统里已经配置好的 AI({@code DASHSCOPE_API_KEY});调用失败统一由
 * {@link AiClient} 映射成 6001。
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);
    /** ai_call_log 的场景名,最长 32 字符。 */
    public static final String SCENE = "ANALYSIS";
    private static final int MAX_TOKENS = 640;
    /** 输出兜底上限:提示词要求 120—220 字,模型偶尔会发散,超过就截断。 */
    private static final int MAX_OUTPUT_CHARS = 800;

    private final AiClient aiClient;
    private final AiProperties props;
    private final QuestionRepository questionRepository;

    public AiAnalysisService(AiClient aiClient, AiProperties props,
                             QuestionRepository questionRepository) {
        this.aiClient = aiClient;
        this.props = props;
        this.questionRepository = questionRepository;
    }

    /** 只生成不写库(编辑页预览用)。 */
    public String generate(String type, String stem, List<String> answerPoints) {
        QuestionType questionType = QuestionType.find(type)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID,
                        "未知题型:\"" + type + "\""));
        List<String> points = cleanPoints(answerPoints);
        validate(questionType, stem, points);
        return call(questionType, stem, points, null, null);
    }

    /**
     * 为指定题目生成解析并保存。
     *
     * <p>刻意**不加** {@code @Transactional}:AI 调用可能几十秒,不该在这期间占着数据库连接。
     * 读取与保存各自走 Repository 自己的事务。
     *
     * @return 生成的解析正文
     */
    public String generateAndSave(Long questionId) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.QUESTION_NOT_FOUND,
                        "题目不存在:id=" + questionId));
        QuestionType type = QuestionType.find(q.getType())
                .orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID,
                        "未知题型:\"" + q.getType() + "\""));
        List<String> points = cleanPoints(q.getAnswerPoints());
        validate(type, q.getStem(), points);

        String analysis = call(type, q.getStem(), points, q.getSubjectId(), q.getId());
        q.setAnalysis(analysis);
        questionRepository.save(q);
        log.info("AI 已生成解析 questionId={} type={} 字数={}", questionId, type, analysis.length());
        return analysis;
    }

    private String call(QuestionType type, String stem, List<String> points,
                        Long subjectId, Long questionId) {
        AiClient.AiRequest request = new AiClient.AiRequest(
                SCENE,
                props.getModel(),
                PromptTemplates.ANALYSIS_ROLE,
                PromptTemplates.analysis(type, stem, points),
                List.of(),
                MAX_TOKENS,
                0.4,
                false, // 解析生成不需要长思考,关掉以降低延迟与 token
                subjectId,
                questionId);
        String content = aiClient.chat(request).content();
        return cleanOutput(content);
    }

    private void validate(QuestionType type, String stem, List<String> points) {
        if (type.isObjective()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "只有主观题(简答 / 论述 / 材料分析 / 写作)才能生成解析");
        }
        if (stem == null || stem.isBlank()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "题干为空,无法生成解析");
        }
        if (points.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "这道题没有参考答案要点,无法生成解析");
        }
    }

    private List<String> cleanPoints(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .toList();
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
