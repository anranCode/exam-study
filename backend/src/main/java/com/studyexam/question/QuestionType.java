package com.studyexam.question;

import java.util.Arrays;
import java.util.Optional;

/**
 * 题型。取值与 docs/study-exam/backend/02-data-model.md 的 question.type 取值表一致,
 * 前端 utils/constants.ts 的中文名与本枚举必须同步。
 */
public enum QuestionType {

    // ── 客观题:后端自动判分 ──────────────────────────────
    SINGLE_CHOICE("单项选择题", true, AnswerStyle.SINGLE_LETTER),
    MULTIPLE_CHOICE("多项选择题", true, AnswerStyle.MULTI_LETTER),
    TRUE_FALSE("判断题", true, AnswerStyle.TRUE_FALSE),
    READING_JUDGE("阅读判断", true, AnswerStyle.TRUE_FALSE_NG),
    READING_CHOICE("阅读选择", true, AnswerStyle.SINGLE_LETTER),
    SECTION_MATCH("概括段落大意和补全句子", true, AnswerStyle.FREE),
    SENTENCE_FILL("填句补文", true, AnswerStyle.FREE),
    WORD_FILL("填词补文", true, AnswerStyle.FREE),
    CLOZE_TRANSFORM("完形补文", true, AnswerStyle.FREE),

    // ── 主观题:后端不判分,前端逐条勾选自评 ────────────────
    SHORT_ANSWER("简答题", false, AnswerStyle.NONE),
    ESSAY("论述题", false, AnswerStyle.NONE),
    MATERIAL_ANALYSIS("材料分析题", false, AnswerStyle.NONE),
    WRITING("短文写作", false, AnswerStyle.NONE);

    /** 客观题答案的形态,决定导入与新建时的规范化与校验规则。 */
    public enum AnswerStyle {
        /** 单个字母,如 "B"。 */
        SINGLE_LETTER,
        /** 2—5 个不重复字母、升序,如 "ABD"。 */
        MULTI_LETTER,
        /** "T" / "F"。 */
        TRUE_FALSE,
        /** "T" / "F" / "NG"。 */
        TRUE_FALSE_NG,
        /** 英语各客观题型:词或短语填入,不强约束形态。 */
        FREE,
        /** 主观题,没有 answer。 */
        NONE
    }

    private final String label;
    private final boolean objective;
    private final AnswerStyle answerStyle;

    QuestionType(String label, boolean objective, AnswerStyle answerStyle) {
        this.label = label;
        this.objective = objective;
        this.answerStyle = answerStyle;
    }

    /** 中文名,用于报错文案与接口描述。 */
    public String getLabel() {
        return label;
    }

    /** 是否客观题(后端自动判分)。 */
    public boolean isObjective() {
        return objective;
    }

    /** 是否主观题(需要自评)。 */
    public boolean isSubjective() {
        return !objective;
    }

    public AnswerStyle getAnswerStyle() {
        return answerStyle;
    }

    public static Optional<QuestionType> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.trim().toUpperCase();
        return Arrays.stream(values()).filter(t -> t.name().equals(v)).findFirst();
    }

    public static boolean isObjectiveType(String value) {
        return find(value).map(QuestionType::isObjective).orElse(false);
    }
}