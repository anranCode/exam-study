package com.studyexam.review;

import com.studyexam.chapter.Chapter;
import com.studyexam.chapter.ChapterRepository;
import com.studyexam.common.BusinessException;
import com.studyexam.common.PageResult;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.review.dto.ReviewItemVO;
import com.studyexam.setting.TableAvailability;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 错题本的读写行为:列表装配、三个筛选、标记已掌握的幂等、移出。
 *
 * <p>排期推演不在这里 —— 那是 {@link Sm2SchedulerTest} 的事,本类只管队列的增删查。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock SubjectRepository subjectRepository;
    @Mock ReviewStatsProvider reviewStats;
    @Mock TableAvailability tables;
    @Mock ReviewItemRepository reviewItemRepository;
    @Mock QuestionRepository questionRepository;
    @Mock ChapterRepository chapterRepository;

    ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(subjectRepository, reviewStats, tables,
                reviewItemRepository, questionRepository, chapterRepository);
    }

    private ReviewItem item(long id, long questionId, String status, int stage, LocalDateTime next) {
        ReviewItem ri = new ReviewItem();
        ri.setId(id);
        ri.setUserId(1L);
        ri.setQuestionId(questionId);
        ri.setStatus(status);
        ri.setStage(stage);
        ri.setIntervalDays(3);
        ri.setEaseFactor(new BigDecimal("2.50"));
        ri.setWrongCount(2);
        ri.setCorrectStreak(1);
        ri.setNextReviewAt(next);
        return ri;
    }

    private Question question(long id, long subjectId, Long chapterId) {
        Question q = new Question();
        q.setId(id);
        q.setSubjectId(subjectId);
        q.setChapterId(chapterId);
        q.setType("SINGLE_CHOICE");
        q.setStem("题干");
        q.setAnswer("B");
        return q;
    }

    private void stubLookups(ReviewItem ri, Question q) {
        when(reviewItemRepository.findAllById(any())).thenReturn(List.of(ri));
        when(questionRepository.findAllById(any())).thenReturn(List.of(q));

        Subject s = new Subject();
        s.setId(q.getSubjectId());
        s.setShortName("马原");
        s.setColor("#E5484D");
        when(subjectRepository.findAllById(any())).thenReturn(List.of(s));

        Chapter c = new Chapter();
        c.setId(7L);
        c.setName("第一章 物质世界及其发展规律");
        when(chapterRepository.findAllById(any())).thenReturn(List.of(c));
    }

    // ── 列表 ────────────────────────────────────────────────

    @Test
    @DisplayName("列表:装配出科目、章节、题型中文名,并按 next_review_at 判断是否到期")
    void listItems_assemblesVO() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        ReviewItem ri = item(5L, 36L, "LEARNING", 1, past);
        stubLookups(ri, question(36L, 1L, 7L));
        when(reviewItemRepository.countForList(any(), any(), any(), anyBoolean(), any())).thenReturn(1L);
        when(reviewItemRepository.findIdsForList(any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(5L));

        PageResult<ReviewItemVO> page = service.listItems(null, null, null, null, null);

        assertThat(page.total()).isEqualTo(1);
        ReviewItemVO vo = page.list().get(0);
        assertThat(vo.questionId()).isEqualTo(36L);
        assertThat(vo.subjectShortName()).isEqualTo("马原");
        assertThat(vo.chapterName()).isEqualTo("第一章 物质世界及其发展规律");
        assertThat(vo.typeLabel()).isEqualTo("单项选择题");
        assertThat(vo.wrongCount()).isEqualTo(2);
        assertThat(vo.masteryStage()).isEqualTo(ReviewItemVO.STAGE_LEARNING);
        assertThat(vo.masteryLabel()).isEqualTo("复习中");
        assertThat(vo.due()).isTrue();
        // 题干在,但不带答案——错题本是入口不是答案册
        assertThat(vo.stem()).isEqualTo("题干");
    }

    @Test
    @DisplayName("列表:没到期的条目 due 为 false")
    void listItems_notDueYet() {
        ReviewItem ri = item(5L, 36L, "LEARNING", 1, LocalDateTime.now().plusDays(2));
        stubLookups(ri, question(36L, 1L, null));
        when(reviewItemRepository.countForList(any(), any(), any(), anyBoolean(), any())).thenReturn(1L);
        when(reviewItemRepository.findIdsForList(any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(5L));

        assertThat(service.listItems(null, null, null, 1, 20).list().get(0).due()).isFalse();
    }

    @Test
    @DisplayName("列表:总数为 0 时直接返回空页,不再查 id")
    void listItems_emptyShortCircuits() {
        when(reviewItemRepository.countForList(any(), any(), any(), anyBoolean(), any())).thenReturn(0L);

        PageResult<ReviewItemVO> page = service.listItems(1L, null, null, 1, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.total()).isZero();
        verify(reviewItemRepository, never()).findIdsForList(any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("列表:status 只认 LEARNING / MASTERED,写错就报错而不是静默返回空")
    void listItems_badStatus() {
        assertThatThrownBy(() -> service.listItems(null, "DONE", null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("列表:筛选参数原样传给查询,空 status 传 null 表示不限")
    void listItems_passesFilters() {
        when(reviewItemRepository.countForList(any(), any(), any(), anyBoolean(), any())).thenReturn(0L);

        service.listItems(1L, "  ", true, 2, 200);

        verify(reviewItemRepository).countForList(
                eq(1L), eq(1L), isNull(), eq(true), any(LocalDateTime.class));
    }

    // ── 掌握阶段的三档 ──────────────────────────────────────

    @Test
    @DisplayName("掌握阶段:全新错题未掌握、答对过一两次复习中、出队即已掌握")
    void masteryStage_threeBuckets() {
        assertThat(ReviewItemVO.masteryStageOf("LEARNING", 0)).isEqualTo(ReviewItemVO.STAGE_NOT_STARTED);
        assertThat(ReviewItemVO.masteryStageOf("LEARNING", 2)).isEqualTo(ReviewItemVO.STAGE_LEARNING);
        assertThat(ReviewItemVO.masteryStageOf("MASTERED", 4)).isEqualTo(ReviewItemVO.STAGE_MASTERED);

        assertThat(ReviewItemVO.masteryLabelOf(ReviewItemVO.STAGE_NOT_STARTED)).isEqualTo("未掌握");
        assertThat(ReviewItemVO.masteryLabelOf(ReviewItemVO.STAGE_LEARNING)).isEqualTo("复习中");
        assertThat(ReviewItemVO.masteryLabelOf(ReviewItemVO.STAGE_MASTERED)).isEqualTo("已掌握");
    }

    // ── 标记已掌握 ──────────────────────────────────────────

    @Test
    @DisplayName("标记已掌握:置为 MASTERED 并落库")
    void markMastered_setsStatus() {
        ReviewItem ri = item(5L, 36L, "LEARNING", 1, LocalDateTime.now());
        when(reviewItemRepository.findByUserIdAndQuestionId(1L, 36L)).thenReturn(java.util.Optional.of(ri));

        service.markMastered(36L);

        assertThat(ri.getStatus()).isEqualTo("MASTERED");
        verify(reviewItemRepository).save(ri);
    }

    @Test
    @DisplayName("标记已掌握:已经是 MASTERED 时幂等,不再写库")
    void markMastered_idempotent() {
        ReviewItem ri = item(5L, 36L, "MASTERED", 4, LocalDateTime.now());
        when(reviewItemRepository.findByUserIdAndQuestionId(1L, 36L)).thenReturn(java.util.Optional.of(ri));

        service.markMastered(36L);

        verify(reviewItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("标记已掌握:不在错题本里报 2003,不静默成功")
    void markMastered_missing() {
        when(reviewItemRepository.findByUserIdAndQuestionId(anyLong(), anyLong()))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.markMastered(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在错题本里");
    }

    // ── 移出 ────────────────────────────────────────────────

    @Test
    @DisplayName("移出:删掉排期记录")
    void remove_deletesItem() {
        ReviewItem ri = item(5L, 36L, "LEARNING", 1, LocalDateTime.now());
        when(reviewItemRepository.findByUserIdAndQuestionId(1L, 36L)).thenReturn(java.util.Optional.of(ri));

        service.remove(36L);

        verify(reviewItemRepository).delete(ri);
    }

    @Test
    @DisplayName("移出:不在错题本里报 2003")
    void remove_missing() {
        when(reviewItemRepository.findByUserIdAndQuestionId(anyLong(), anyLong()))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.remove(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在错题本里");
    }
}
