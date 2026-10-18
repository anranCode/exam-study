package com.studyexam.question;

import com.studyexam.common.BusinessException;
import com.studyexam.question.dto.QuestionUpsertDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 题型相关校验的单元测试。这些规则错了会直接影响能不能录题,属于「算错了会影响学习效果」的逻辑。
 */
class QuestionValidatorTest {

    private static QuestionUpsertDTO singleChoice(List<OptionItem> options, String answer) {
        return new QuestionUpsertDTO(1L, null, "SINGLE_CHOICE", "题干", options, answer, null,
                null, 1, null, null, new BigDecimal("2.0"), "PUBLISHED");
    }

    private static QuestionUpsertDTO subjective(String type, String answer, List<String> points) {
        return new QuestionUpsertDTO(1L, null, type, "题干", null, answer, points,
                null, 2, null, null, new BigDecimal("6.0"), "PUBLISHED");
    }

    private static List<OptionItem> abcd() {
        return List.of(
                new OptionItem("A", "甲"),
                new OptionItem("B", "乙"),
                new OptionItem("C", "丙"),
                new OptionItem("D", "丁"));
    }

    @Test
    @DisplayName("单选题:合法输入通过,并返回规范化题型")
    void singleChoice_ok() {
        QuestionValidator.Normalized n = QuestionValidator.validateAndNormalize(singleChoice(abcd(), "B"));
        assertThat(n.type()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(n.answer()).isEqualTo("B");
    }

    @Test
    @DisplayName("单选题:options 为空数组被拒绝,且提示指向 options")
    void singleChoice_emptyOptions() {
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(singleChoice(List.of(), "B")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("options");
    }

    @Test
    @DisplayName("单选题:answer 不是单个字母被拒绝")
    void singleChoice_badAnswerShape() {
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(singleChoice(abcd(), "AB")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("answer");
    }

    @Test
    @DisplayName("单选题:答案不在选项中时被拒绝")
    void singleChoice_answerNotInOptions() {
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(singleChoice(abcd(), "E")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在选项");
    }

    @Test
    @DisplayName("多选题:DBA 被规范化为 ABD")
    void multiChoice_normalizesToAscending() {
        QuestionUpsertDTO dto = new QuestionUpsertDTO(1L, null, "MULTIPLE_CHOICE", "题干", abcd(), "DBA",
                null, null, 2, null, null, new BigDecimal("2.0"), "PUBLISHED");
        QuestionValidator.Normalized n = QuestionValidator.validateAndNormalize(dto);
        assertThat(n.answer()).isEqualTo("ABD");
    }

    @Test
    @DisplayName("多选题:答案有重复字母时被拒绝")
    void multiChoice_duplicateLetters() {
        QuestionUpsertDTO dto = new QuestionUpsertDTO(1L, null, "MULTIPLE_CHOICE", "题干", abcd(), "AAB",
                null, null, 2, null, null, new BigDecimal("2.0"), "PUBLISHED");
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    @Test
    @DisplayName("简答题:传了 answer 被拒绝")
    void subjective_withAnswer() {
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(
                subjective("SHORT_ANSWER", "B", List.of("要点一"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能传 answer");
    }

    @Test
    @DisplayName("简答题:answerPoints 为空被拒绝")
    void subjective_emptyPoints() {
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(
                subjective("SHORT_ANSWER", null, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("answerPoints");
    }

    @Test
    @DisplayName("简答题:合法输入通过,纯空白要点被丢弃")
    void subjective_ok() {
        QuestionValidator.Normalized n = QuestionValidator.validateAndNormalize(
                subjective("SHORT_ANSWER", null, List.of("要点一", "   ", "要点二")));
        assertThat(n.type().isSubjective()).isTrue();
        assertThat(n.answerPoints()).containsExactly("要点一", "要点二");
    }

    @Test
    @DisplayName("客观题:传了 answerPoints 被拒绝")
    void objective_withPoints() {
        QuestionUpsertDTO dto = new QuestionUpsertDTO(1L, null, "SINGLE_CHOICE", "题干", abcd(), "B",
                List.of("要点一"), null, 2, null, null, new BigDecimal("2.0"), "PUBLISHED");
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("answerPoints");
    }

    @Test
    @DisplayName("判断题:正确/错误的中文被规范化为 T / F")
    void trueFalse_chineseNormalization() {
        QuestionUpsertDTO dto = new QuestionUpsertDTO(1L, null, "TRUE_FALSE", "题干",
                List.of(new OptionItem("T", "正确"), new OptionItem("F", "错误")),
                "正确", null, null, 1, null, null, new BigDecimal("1.0"), "PUBLISHED");
        assertThat(QuestionValidator.validateAndNormalize(dto).answer()).isEqualTo("T");
    }

    @Test
    @DisplayName("未知题型被拒绝,提示里列出合法取值")
    void unknownType() {
        QuestionUpsertDTO dto = new QuestionUpsertDTO(1L, null, "FILL_BLANK", "题干", abcd(), "B",
                null, null, 1, null, null, new BigDecimal("2.0"), "PUBLISHED");
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知题型");
    }

    @Test
    @DisplayName("主观题与客观题的题型分类与文档一致")
    void objectiveClassification() {
        assertThat(QuestionType.SINGLE_CHOICE.isObjective()).isTrue();
        assertThat(QuestionType.READING_JUDGE.isObjective()).isTrue();
        assertThat(QuestionType.CLOZE_TRANSFORM.isObjective()).isTrue();
        assertThat(QuestionType.SHORT_ANSWER.isObjective()).isFalse();
        assertThat(QuestionType.ESSAY.isObjective()).isFalse();
        assertThat(QuestionType.MATERIAL_ANALYSIS.isObjective()).isFalse();
        assertThat(QuestionType.WRITING.isObjective()).isFalse();
    }

    /* ---------- 卷面没印答案:待校对可以没答案,已发布不行 ---------- */

    private static QuestionUpsertDTO singleChoice(String status, List<OptionItem> options) {
        return new QuestionUpsertDTO(1L, null, "SINGLE_CHOICE", "题干", options, null,
                null, null, 1, null, null, new BigDecimal("2.0"), status);
    }

    @Test
    @DisplayName("客观题 + 空答案 + 已发布:被拒绝,并提示改存待校对")
    void objectiveEmptyAnswer_publishedRejected() {
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(singleChoice("PUBLISHED", abcd())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待校对");
    }

    @Test
    @DisplayName("客观题 + 空答案 + 待校对:放行,答案保持为空")
    void objectiveEmptyAnswer_draftAllowed() {
        QuestionValidator.Normalized n = QuestionValidator.validateAndNormalize(singleChoice("DRAFT", abcd()));
        assertThat(n.answer()).isNull();
        assertThat(n.options()).hasSize(4);
    }

    @Test
    @DisplayName("待校对放行空答案,但不放过坏选项——选项内容为空仍然被拒")
    void objectiveEmptyAnswer_draftStillChecksOptions() {
        List<OptionItem> bad = List.of(new OptionItem("A", "甲"), new OptionItem("B", "  "));
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(singleChoice("DRAFT", bad)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("options");
    }

    @Test
    @DisplayName("主观题 + 空要点 + 待校对:放行;已发布仍然拒绝")
    void subjectiveEmptyPoints_draftOnly() {
        QuestionUpsertDTO draft = new QuestionUpsertDTO(1L, null, "SHORT_ANSWER", "简述……", null, null,
                null, null, 2, null, null, new BigDecimal("7.0"), "DRAFT");
        assertThat(QuestionValidator.validateAndNormalize(draft).answerPoints()).isNull();

        QuestionUpsertDTO published = new QuestionUpsertDTO(1L, null, "SHORT_ANSWER", "简述……", null, null,
                null, null, 2, null, null, new BigDecimal("7.0"), "PUBLISHED");
        assertThatThrownBy(() -> QuestionValidator.validateAndNormalize(published))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待校对");
    }
}