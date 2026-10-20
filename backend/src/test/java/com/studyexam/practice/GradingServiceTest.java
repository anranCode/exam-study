package com.studyexam.practice;

import com.studyexam.question.OptionItem;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 判分规则测试。客观题比对必须容忍大小写与多选题的字母顺序——
 * 这是登录试卷最常见的两种书写差异。
 */
class GradingServiceTest {

    private final GradingService grading = new GradingService();

    private Question question(String type, String answer) {
        Question q = new Question();
        q.setType(type);
        q.setAnswer(answer);
        q.setStem("题干");
        return q;
    }

    @Test
    @DisplayName("多选题:DBA 与标准答案 ABD 判为正确(顺序无关)")
    void multiChoiceIgnoresOrder() {
        Question q = question("MULTIPLE_CHOICE", "ABD");
        assertThat(grading.grade(q, "DBA")).isTrue();
        assertThat(grading.grade(q, "abd")).isTrue();
        assertThat(grading.grade(q, "A,B,D")).isTrue();
        assertThat(grading.grade(q, "AB")).isFalse();
        assertThat(grading.grade(q, "ABCD")).isFalse();
    }

    @Test
    @DisplayName("单选题:大小写无关,选错项判错")
    void singleChoice() {
        Question q = question("SINGLE_CHOICE", "B");
        assertThat(grading.grade(q, "b")).isTrue();
        assertThat(grading.grade(q, "B ")).isTrue();
        assertThat(grading.grade(q, "C")).isFalse();
        assertThat(grading.grade(q, "")).isFalse();
        assertThat(grading.grade(q, null)).isFalse();
    }

    @Test
    @DisplayName("判断题:接受中文写法「对/正确/错/错误」")
    void trueFalseAcceptsChinese() {
        Question q = question("TRUE_FALSE", "T");
        assertThat(grading.grade(q, "对")).isTrue();
        assertThat(grading.grade(q, "正确")).isTrue();
        assertThat(grading.grade(q, "t")).isTrue();
        assertThat(grading.grade(q, "错")).isFalse();

        Question wrong = question("TRUE_FALSE", "F");
        assertThat(grading.grade(wrong, "错误")).isTrue();
    }

    @Test
    @DisplayName("阅读判断:T / F / NG 三态,NG 不会被当成字母排序处理")
    void readingJudgeKeepsNgToken() {
        Question q = question("READING_JUDGE", "NG");
        assertThat(grading.grade(q, "ng")).isTrue();
        assertThat(grading.grade(q, "NG")).isTrue();
        assertThat(grading.grade(q, "T")).isFalse();
        // 规范化不会把 NG 排成 GN
        assertThat(grading.normalize("ng", QuestionType.READING_JUDGE)).isEqualTo("NG");
    }

    @Test
    @DisplayName("主观题不由后端判分,返回 null 表示交给自评")
    void subjectiveNotAutoGraded() {
        Question q = question("SHORT_ANSWER", null);
        assertThat(grading.isAutoGraded(q)).isFalse();
        assertThat(grading.grade(q, "我的作答")).isNull();
    }

    @Test
    @DisplayName("缺少标准答案的客观题:有作答时不判分,空作答仍按错处理(保证「不知道」能进错题本)")
    void missingStandardAnswerDoesNotGrade() {
        Question q = question("SINGLE_CHOICE", null);
        assertThat(grading.grade(q, "A")).isNull();
        assertThat(grading.grade(q, null)).isFalse();
        assertThat(grading.grade(q, "  ")).isFalse();
    }

    @Test
    @DisplayName("字母排序规范化:去重 + 升序")
    void sortLetters() {
        assertThat(grading.sortLetters("DBA")).isEqualTo("ABD");
        assertThat(grading.sortLetters("AAB")).isEqualTo("AB");
        assertThat(grading.sortLetters("c")).isEqualTo("c");
    }

    @Test
    @DisplayName("英语客观题型(填词补文等)按自由文本比较,忽略空白与标点")
    void freeTextTypesComparedLoosely() {
        Question q = question("WORD_FILL", "valuable");
        assertThat(grading.grade(q, "Valuable")).isTrue();
        assertThat(grading.grade(q, " valuable ")).isTrue();
        assertThat(grading.grade(q, "valueless")).isFalse();
    }
}