package com.studyexam.question;

import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.question.dto.QuestionUpsertDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 题型相关的校验与规范化。
 *
 * <p>这些规则依赖 {@code type} 的取值,声明式注解表达不了,因此手写。校验失败一律抛
 * {@link ErrorCode#PARAM_INVALID},文案必须**明确指出是哪一项不合法**,前端直接展示。
 *
 * <p>规则来源:docs/study-exam/backend/03-api-spec.md 第三节「校验规则」。
 */
public final class QuestionValidator {

    private static final Pattern SINGLE_LETTER = Pattern.compile("^[A-Z]$");

    private QuestionValidator() {
    }

    /** 规范化后的题目内容:题型确定、答案统一大写且符合题型要求。 */
    public record Normalized(
            QuestionType type,
            String answer,
            List<OptionItem> options,
            List<String> answerPoints
    ) {
    }

    /**
     * 校验并规范化。会修正的部分:
     * <ul>
     *   <li>多选题答案 {@code "DBA"} → {@code "ABD"}(去重、升序)</li>
     *   <li>判断题答案统一为大写 {@code "T"} / {@code "F"}</li>
     *   <li>选项字母统一为大写</li>
     *   <li>主观题要点去掉纯空白项</li>
     * </ul>
     */
    public static Normalized validateAndNormalize(QuestionUpsertDTO dto) {
        QuestionType type = QuestionType.find(dto.type())
                .orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID,
                        "type: 未知题型 \"" + dto.type() + "\",取值必须是 " + allowedTypes()));

        List<OptionItem> options = normalizeOptions(dto.options());
        List<String> answerPoints = normalizePoints(dto.answerPoints());
        String answer = trimToNull(dto.answer());

        // 客观题的答案先规范化(大写、去重、升序),再按题型校验形态与选项对应关系
        if (type.isObjective() && answer != null) {
            answer = normalizeAnswer(type, answer, options);
        }

        if (type.isObjective()) {
            validateObjective(type, answer, options, answerPoints, isDraft(dto.status()));
        } else {
            validateSubjective(type, answer, answerPoints, options, isDraft(dto.status()));
        }

        return new Normalized(type, answer, options, answerPoints);
    }

    /**
     * 「待校对」的题可以没答案。
     *
     * <p>真题卷面常常没印答案,扫描件识别时也只能先把题目录进来、答案后补。所以这里给
     * DRAFT 开一个口子:**不是**放宽「已发布」——已发布的客观题仍然必须有答案,
     * 因为练习只会抽已发布的题(见 {@code QuestionRepository.pickRandom}),
     * 有答案才有得判分。
     */
    private static boolean isDraft(String status) {
        return status != null && "DRAFT".equalsIgnoreCase(status.trim());
    }

    private static void validateObjective(QuestionType type, String answer, List<OptionItem> options,
                                          List<String> answerPoints, boolean draft) {
        // 完形补文(CLOZE_TRANSFORM)是「括号里给词根、填出正确形式」,卷面上本来就没有选项,
        // 因此只有它允许 options 为空——其余客观题必须有选项可点。
        boolean optionsRequired = type != QuestionType.CLOZE_TRANSFORM;
        if (optionsRequired && (options == null || options.isEmpty())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "options: " + type.getLabel() + "是客观题,options 不能为空");
        }
        if (answer == null && !draft) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "answer: " + type.getLabel() + "是客观题,「已发布」时必须填 answer;"
                            + "答案还没确定就先存成「待校对」");
        }
        if (answerPoints != null && !answerPoints.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "answerPoints: " + type.getLabel() + "是客观题,不能传 answerPoints");
        }

        Set<String> optionKeys = new LinkedHashSet<>();
        for (OptionItem o : options == null ? List.<OptionItem>of() : options) {
            if (o.key() == null || o.key().isBlank()) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID, "options: 选项字母不能为空");
            }
            if (o.text() == null || o.text().isBlank()) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID,
                        "options: 选项 " + o.key() + " 的内容不能为空");
            }
            optionKeys.add(o.key());
        }

        if (answer == null) {
            // 待校对且卷面没答案:选项已校验完,剩下都是答案形态的检查,没有答案就到此为止
            return;
        }

        boolean letterBased = type.getAnswerStyle() == QuestionType.AnswerStyle.SINGLE_LETTER
                || type.getAnswerStyle() == QuestionType.AnswerStyle.MULTI_LETTER;
        if (letterBased) {
            for (String letter : answer.split("")) {
                if (!letter.isBlank() && !optionKeys.contains(letter)) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID,
                            "answer: 答案「" + letter + "」不在选项 " + optionKeys + " 中");
                }
            }
        }
    }

    private static void validateSubjective(QuestionType type, String answer, List<String> answerPoints,
                                           List<OptionItem> options, boolean draft) {
        if (answer != null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "answer: " + type.getLabel() + "是主观题,不能传 answer,请改用 answerPoints");
        }
        // 与客观题同一条规矩:参考答案要点也是答案,卷面没给、模型也答不出时,
        // 「待校对」可以留空先入库,「已发布」不行。
        if ((answerPoints == null || answerPoints.isEmpty()) && !draft) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "answerPoints: " + type.getLabel() + "是主观题,answerPoints(参考答案要点)不能为空;"
                            + "要点还没写就先存成「待校对」");
        }
        if (options != null && !options.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "options: " + type.getLabel() + "是主观题,不能传 options");
        }
    }

    /** 按题型把答案规范化为大写、去重、升序,并校验字母个数。 */
    private static String normalizeAnswer(QuestionType type, String raw, List<OptionItem> options) {
        String upper = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");

        return switch (type.getAnswerStyle()) {
            case SINGLE_LETTER -> {
                if (!SINGLE_LETTER.matcher(upper).matches()) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID,
                            "answer: " + type.getLabel() + "的答案必须是 1 个字母(A—Z),当前为 \"" + raw + "\"");
                }
                yield upper;
            }
            case MULTI_LETTER -> {
                if (!upper.matches("^[A-Z]{2,5}$")) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID,
                            "answer: " + type.getLabel() + "的答案必须是 2—5 个字母(A—Z),当前为 \"" + raw + "\"");
                }
                Set<String> unique = new TreeSet<>();
                for (String ch : upper.split("")) {
                    if (!unique.add(ch)) {
                        throw BusinessException.of(ErrorCode.PARAM_INVALID,
                                "answer: " + type.getLabel() + "的答案有重复字母,当前为 \"" + raw + "\"");
                    }
                }
                yield String.join("", unique);   // "DBA" → "ABD"
            }
            case TRUE_FALSE -> {
                String v = upper.equals("对") || upper.equals("正确") ? "T"
                        : upper.equals("错") || upper.equals("错误") ? "F" : upper;
                if (!v.equals("T") && !v.equals("F")) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID,
                            "answer: 判断题的答案只能是 T 或 F,当前为 \"" + raw + "\"");
                }
                yield v;
            }
            case TRUE_FALSE_NG -> {
                if (!upper.matches("^(T|F|NG)$")) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID,
                            "answer: 阅读判断的答案只能是 T / F / NG,当前为 \"" + raw + "\"");
                }
                yield upper;
            }
            // 英语各客观题型:答案是选项字母(填词补文/填句补文)时统一成大写,
            // 是真实单词(完形补文,如 accompanies)时保留原始大小写——
            // 统一转大写会把单词存成 ACCOMPANIES,显示很难看。
            // 判分不受影响:GradingService 比较时两边都会规范化。
            case FREE -> {
                String trimmed = raw.trim();
                boolean isOptionKey = options != null && options.stream()
                        .anyMatch(o -> o.key() != null && o.key().equalsIgnoreCase(trimmed));
                yield isOptionKey ? trimmed.toUpperCase(Locale.ROOT) : trimmed;
            }
            case NONE -> throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "answer: 主观题不应该有 answer");
        };
    }

    private static List<OptionItem> normalizeOptions(List<OptionItem> options) {
        if (options == null) {
            return null;
        }
        List<OptionItem> result = new ArrayList<>(options.size());
        for (OptionItem o : options) {
            String key = o.key() == null ? null : o.key().trim().toUpperCase(Locale.ROOT);
            String text = o.text() == null ? null : o.text().trim();
            result.add(new OptionItem(key, text));
        }
        return result;
    }

    private static List<String> normalizePoints(List<String> points) {
        if (points == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String p : points) {
            if (p != null && !p.isBlank()) {
                result.add(p.trim());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String allowedTypes() {
        List<String> names = new ArrayList<>();
        for (QuestionType t : QuestionType.values()) {
            names.add(t.name());
        }
        return String.join(" / ", names);
    }
}