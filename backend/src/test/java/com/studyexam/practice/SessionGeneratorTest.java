package com.studyexam.practice;

import com.studyexam.common.BusinessException;
import com.studyexam.paper.Paper;
import com.studyexam.paper.PaperQuestion;
import com.studyexam.paper.PaperQuestionRepository;
import com.studyexam.paper.PaperRepository;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.review.ReviewItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组题策略测试。五种模式的抽题规则见 01-architecture.md 第三节。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionGeneratorTest {

    @Mock QuestionRepository questionRepository;
    @Mock ReviewItemRepository reviewItemRepository;
    @Mock PaperRepository paperRepository;
    @Mock PaperQuestionRepository paperQuestionRepository;

    SessionGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SessionGenerator(questionRepository, reviewItemRepository,
                paperRepository, paperQuestionRepository);
    }

    private Question q(long id) {
        Question question = new Question();
        question.setId(id);
        question.setSubjectId(1L);
        question.setType("SINGLE_CHOICE");
        question.setStem("题干" + id);
        question.setAnswer("A");
        question.setScore(new BigDecimal("2.0"));
        return question;
    }

    @Test
    @DisplayName("DAILY:到期复习题优先,差额用新题补齐")
    void dailyPutsDueFirstThenFillsWithNew() {
        when(reviewItemRepository.findDueQuestionIds(eq(1L), any(), any(), anyInt()))
                .thenReturn(List.of(10L, 11L));
        when(questionRepository.findAllById(anyIterable()))
                .thenReturn(List.of(q(10), q(11)));
        when(questionRepository.pickNew(any(), any(), anyList(), anyInt()))
                .thenReturn(List.of(q(20)));

        SessionGenerator.Picked picked = generator.generate("DAILY", null, null, 3, null, null, null);

        assertThat(picked.questions()).hasSize(3);
        assertThat(picked.questions().get(0).review()).isTrue();
        assertThat(picked.questions().get(1).review()).isTrue();
        assertThat(picked.questions().get(2).review()).isFalse();
        assertThat(picked.questions()).extracting(p -> p.question().getId())
                .containsExactly(10L, 11L, 20L);
    }

    @Test
    @DisplayName("DAILY:没有到期题也没有新题时退回随机抽,不空转")
    void dailyFallsBackToRandomWhenPoolEmpty() {
        when(reviewItemRepository.findDueQuestionIds(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(questionRepository.pickNew(any(), any(), anyList(), anyInt())).thenReturn(List.of());
        when(questionRepository.pickRandom(any(), any(), anyBoolean(), anyList(), anyList(), anyInt()))
                .thenReturn(List.of(q(30), q(31)));

        SessionGenerator.Picked picked = generator.generate("DAILY", null, null, 2, null, null, null);

        assertThat(picked.questions()).hasSize(2);
        assertThat(picked.questions()).allMatch(p -> !p.review());
    }

    @Test
    @DisplayName("RANDOM:按科目与章节范围随机,不看排期")
    void randomIgnoresSchedule() {
        when(questionRepository.pickRandom(eq(1L), eq(3L), anyBoolean(), anyList(), anyList(), eq(5)))
                .thenReturn(List.of(q(1), q(2)));

        SessionGenerator.Picked picked = generator.generate("RANDOM", 1L, 3L, 5, null, null, null);

        assertThat(picked.questions()).hasSize(2);
        assertThat(picked.questions()).allMatch(p -> !p.review());
    }

    @Test
    @DisplayName("RANDOM:传了题型就按题型过滤,并把 typeFilter 打开")
    void randomAppliesTypeFilter() {
        when(questionRepository.pickRandom(eq(1L), any(), eq(true),
                eq(List.of("SHORT_ANSWER")), anyList(), eq(5)))
                .thenReturn(List.of(q(7)));

        SessionGenerator.Picked picked = generator.generate("RANDOM", 1L, null, 5, null, null,
                List.of("SHORT_ANSWER"));

        assertThat(picked.questions()).hasSize(1);
        verify(questionRepository).pickRandom(eq(1L), any(), eq(true),
                eq(List.of("SHORT_ANSWER")), anyList(), eq(5));
    }

    @Test
    @DisplayName("RANDOM:空白题型视为不筛选")
    void blankTypesMeanNoFilter() {
        when(questionRepository.pickRandom(any(), any(), eq(false), anyList(), anyList(), anyInt()))
                .thenReturn(List.of(q(8)));

        generator.generate("RANDOM", 1L, null, 5, null, null, List.of("  "));

        verify(questionRepository).pickRandom(any(), any(), eq(false), anyList(), anyList(), anyInt());
    }

    @Test
    @DisplayName("RANDOM:未知题型直接报错,不静默忽略")
    void unknownTypeRejected() {
        assertThatThrownBy(() ->
                generator.generate("RANDOM", 1L, null, 5, null, null, List.of("NOT_A_TYPE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知题型");
    }

    @Test
    @DisplayName("CHAPTER:必须指定章节")
    void chapterRequiresChapterId() {
        assertThatThrownBy(() -> generator.generate("CHAPTER", 1L, null, 10, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("章节练习必须指定章节");
    }

    @Test
    @DisplayName("MOCK:按试卷原始顺序出题,并带上大题名")
    void mockFollowsPaperOrder() {
        Paper paper = new Paper();
        paper.setId(9L);
        paper.setName("2024年10月真题");
        when(paperRepository.findById(9L)).thenReturn(Optional.of(paper));

        PaperQuestion first = new PaperQuestion();
        first.setPaperId(9L);
        first.setQuestionId(11L);
        first.setSectionName("一、单项选择题");
        first.setSortOrder(1);
        PaperQuestion second = new PaperQuestion();
        second.setPaperId(9L);
        second.setQuestionId(12L);
        second.setSectionName("二、简答题");
        second.setSortOrder(2);
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(9L))
                .thenReturn(List.of(first, second));
        when(questionRepository.findAllById(anyIterable())).thenReturn(List.of(q(12), q(11)));

        SessionGenerator.Picked picked = generator.generate("MOCK", null, null, null, 9L, null, null);

        assertThat(picked.paperId()).isEqualTo(9L);
        assertThat(picked.questions()).extracting(p -> p.question().getId())
                .containsExactly(11L, 12L);   // 顺序由试卷决定,不受查询返回顺序影响
        assertThat(picked.questions().get(0).sectionName()).isEqualTo("一、单项选择题");
        assertThat(picked.questions().get(1).sectionName()).isEqualTo("二、简答题");
    }

    @Test
    @DisplayName("MOCK:未指定试卷或试卷不存在时报错")
    void mockRequiresPaper() {
        assertThatThrownBy(() -> generator.generate("MOCK", null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须指定试卷");

        when(paperRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> generator.generate("MOCK", null, null, null, 404L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("试卷不存在");
    }

    @Test
    @DisplayName("REVIEW:只抽到期的题;一道都没有时给出可读提示")
    void reviewOnlyDue() {
        when(reviewItemRepository.findDueQuestionIds(any(), isNull(), any(), anyInt()))
                .thenReturn(List.of(41L));
        when(questionRepository.findAllById(anyIterable())).thenReturn(List.of(q(41)));

        SessionGenerator.Picked picked = generator.generate("REVIEW", null, null, 10, null, null, null);
        assertThat(picked.questions()).hasSize(1);
        assertThat(picked.questions().get(0).review()).isTrue();

        when(reviewItemRepository.findDueQuestionIds(any(), isNull(), any(), anyInt())).thenReturn(List.of());
        assertThatThrownBy(() -> generator.generate("REVIEW", null, null, 10, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有到期复习");
    }

    @Test
    @DisplayName("未知模式被拒绝,提示里列出合法取值")
    void unknownModeRejected() {
        assertThatThrownBy(() -> generator.generate("EXAM", null, null, 10, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DAILY");
    }

    @Test
    @DisplayName("题量缺省 10,并封顶 100")
    void countDefaultsAndCaps() {
        when(questionRepository.pickRandom(any(), any(), anyBoolean(), anyList(), anyList(), eq(10)))
                .thenReturn(List.of(q(1)));
        assertThat(generator.generate("RANDOM", null, null, null, null, null, null).questions()).hasSize(1);

        when(questionRepository.pickRandom(any(), any(), anyBoolean(), anyList(), anyList(), eq(100)))
                .thenReturn(List.of(q(1)));
        assertThat(generator.generate("RANDOM", null, null, 9999, null, null, null).questions()).hasSize(1);
    }

    @Test
    @DisplayName("题库存量为空时给出「去导入题目」的可读提示")
    void emptyPoolFriendlyMessage() {
        when(questionRepository.pickRandom(any(), any(), anyBoolean(), anyList(), anyList(), anyInt())).thenReturn(List.of());
        assertThatThrownBy(() -> generator.generate("RANDOM", 1L, null, 5, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可练习的题目");
    }

    @Test
    @DisplayName("MOCK:试卷下没有题目时报错而不是返回空场次")
    void mockEmptyPaperRejected() {
        Paper paper = new Paper();
        paper.setId(9L);
        paper.setName("空卷");
        when(paperRepository.findById(9L)).thenReturn(Optional.of(paper));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(9L)).thenReturn(List.of());

        assertThatThrownBy(() -> generator.generate("MOCK", null, null, null, 9L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还没有题目");
    }

}
