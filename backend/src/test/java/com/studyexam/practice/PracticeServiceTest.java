package com.studyexam.practice;

import com.studyexam.chapter.ChapterRepository;
import com.studyexam.common.BusinessException;
import com.studyexam.practice.dto.AnswerResultVO;
import com.studyexam.practice.dto.AnswerSubmitDTO;
import com.studyexam.practice.dto.SelfScoreDTO;
import com.studyexam.practice.dto.SessionSummaryVO;
import com.studyexam.question.OptionItem;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.review.ReviewItem;
import com.studyexam.review.ReviewItemRepository;
import com.studyexam.stats.StudyLog;
import com.studyexam.stats.StudyLogRepository;
import com.studyexam.subject.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 练习服务的行为测试:幂等、信心度改写、主观题推迟入队、结束时的日志累加。
 *
 * <p>「主观题自评后才入队」这条曾经因为一个赋值顺序错误而失效(自评标记在写入之后才取),
 * 因此这里把它单独钉死。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PracticeServiceTest {

    @Mock PracticeSessionRepository sessionRepository;
    @Mock AnswerRecordRepository answerRepository;
    @Mock QuestionRepository questionRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock ChapterRepository chapterRepository;
    @Mock ReviewItemRepository reviewItemRepository;
    @Mock StudyLogRepository studyLogRepository;
    @Mock SessionGenerator sessionGenerator;

    PracticeService service;

    private PracticeSession session;
    private Question objective;
    private Question subjective;

    @BeforeEach
    void setUp() {
        service = new PracticeService(sessionRepository, answerRepository, questionRepository,
                subjectRepository, chapterRepository, reviewItemRepository, studyLogRepository,
                sessionGenerator, new GradingService());

        session = new PracticeSession();
        session.setId(88L);
        session.setMode("RANDOM");
        session.setStatus("ONGOING");
        session.setTotalCount(3);
        session.setAnsweredCount(0);
        session.setCorrectCount(0);
        session.setStartedAt(LocalDateTime.now().minusMinutes(5));
        when(sessionRepository.findById(88L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(PracticeSession.class))).thenAnswer(i -> i.getArgument(0));

        objective = new Question();
        objective.setId(501L);
        objective.setSubjectId(1L);
        objective.setType("SINGLE_CHOICE");
        objective.setStem("客观题题干");
        objective.setAnswer("B");
        objective.setScore(new BigDecimal("2.0"));
        objective.setOptions(List.of(new OptionItem("A", "甲"), new OptionItem("B", "乙")));

        subjective = new Question();
        subjective.setId(502L);
        subjective.setSubjectId(1L);
        subjective.setType("SHORT_ANSWER");
        subjective.setStem("主观题题干");
        subjective.setScore(new BigDecimal("6.0"));
        subjective.setAnswerPoints(List.of("要点一", "要点二", "要点三"));

        when(questionRepository.findById(501L)).thenReturn(Optional.of(objective));
        when(questionRepository.findById(502L)).thenReturn(Optional.of(subjective));
        when(questionRepository.findAllById(any())).thenAnswer(i -> {
            List<Question> list = new ArrayList<>();
            list.add(objective);
            list.add(subjective);
            return list;
        });

        when(answerRepository.save(any(AnswerRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(answerRepository.findBySessionIdAndQuestionId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(reviewItemRepository.save(any(ReviewItem.class))).thenAnswer(i -> i.getArgument(0));
        when(reviewItemRepository.findByUserIdAndQuestionId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(answerRepository.countReviewDoneInSession(anyLong(), any())).thenReturn(0L);
        when(reviewItemRepository.countCreatedSince(anyLong(), any())).thenReturn(0L);
        when(reviewItemRepository.countUpdatedSince(anyLong(), any())).thenReturn(0L);
    }

    private AnswerRecord existing(Question q, Integer selfScore) {
        AnswerRecord r = new AnswerRecord();
        r.setId(1L);
        r.setSessionId(88L);
        r.setQuestionId(q.getId());
        r.setConfidence(selfScore == null ? "UNSURE" : "SURE");
        r.setSelfScore(selfScore);
        r.setTotalPoints(selfScore == null ? null : 3);
        r.setSpentSeconds(10);
        when(answerRepository.findBySessionIdAndQuestionId(88L, q.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    @DisplayName("答错:信心度被改写为 WRONG,并在复习队列里新建条目")
    void wrongAnswerWritesWrongConfidence() {
        service.submitAnswer(88L, new AnswerSubmitDTO(501L, "A", "SURE", 8));

        ArgumentCaptor<AnswerRecord> record = ArgumentCaptor.forClass(AnswerRecord.class);
        verify(answerRepository).save(record.capture());
        assertThat(record.getValue().getConfidence()).isEqualTo("WRONG");
        assertThat(record.getValue().getIsCorrect()).isFalse();

        ArgumentCaptor<ReviewItem> item = ArgumentCaptor.forClass(ReviewItem.class);
        verify(reviewItemRepository).save(item.capture());
        assertThat(item.getValue().getQuestionId()).isEqualTo(501L);
        assertThat(item.getValue().getStage()).isZero();
        assertThat(item.getValue().getWrongCount()).isEqualTo(1);
        assertThat(item.getValue().getEaseFactor()).isEqualByComparingTo("2.30");

        assertThat(session.getAnsweredCount()).isEqualTo(1);
        assertThat(session.getCorrectCount()).isZero();
    }

    @Test
    @DisplayName("答对但点「先看答案」:记 UNSURE,间隔回到 1 天")
    void correctAnswerWithUnsureConfidence() {
        service.submitAnswer(88L, new AnswerSubmitDTO(501L, "B", "UNSURE", 12));

        ArgumentCaptor<AnswerRecord> record = ArgumentCaptor.forClass(AnswerRecord.class);
        verify(answerRepository).save(record.capture());
        assertThat(record.getValue().getConfidence()).isEqualTo("UNSURE");
        assertThat(record.getValue().getIsCorrect()).isTrue();
        assertThat(session.getCorrectCount()).isEqualTo(1);

        ArgumentCaptor<ReviewItem> item = ArgumentCaptor.forClass(ReviewItem.class);
        verify(reviewItemRepository).save(item.capture());
        assertThat(item.getValue().getIntervalDays()).isEqualTo(1);
        assertThat(item.getValue().getStage()).isZero();
    }

    @Test
    @DisplayName("重复提交同一题:幂等,不新建记录也不重复推进排期")
    void duplicateSubmitIsIdempotent() {
        existing(objective, null);

        AnswerResultVO result = service.submitAnswer(88L, new AnswerSubmitDTO(501L, "A", "SURE", 5));

        assertThat(result.isCorrect()).isNull();   // 已有记录里没有对错信息,原样返回
        verify(answerRepository, never()).save(any(AnswerRecord.class));
        verify(reviewItemRepository, never()).save(any(ReviewItem.class));
        assertThat(session.getAnsweredCount()).isZero();
    }

    @Test
    @DisplayName("主观题作答:不判分、不入队,只把要点交给前端")
    void subjectiveAnswerDoesNotSchedule() {
        AnswerResultVO result = service.submitAnswer(88L,
                new AnswerSubmitDTO(502L, "我的作答", "UNSURE", 60));

        assertThat(result.isCorrect()).isNull();
        assertThat(result.answerPoints()).hasSize(3);
        assertThat(result.nextReviewAt()).isNull();
        verify(reviewItemRepository, never()).save(any(ReviewItem.class));
    }

    @Test
    @DisplayName("主观题自评 2/3:按比例记分,并在这一刻首次入队(回归:自评标记要在写入之前取)")
    void selfScoreSchedulesForTheFirstTime() {
        existing(subjective, null);

        AnswerResultVO result = service.submitSelfScore(88L, 502L, new SelfScoreDTO(2, 3));

        assertThat(result.selfScore()).isEqualTo(2);
        assertThat(result.totalPoints()).isEqualTo(3);

        ArgumentCaptor<ReviewItem> item = ArgumentCaptor.forClass(ReviewItem.class);
        verify(reviewItemRepository).save(item.capture());
        assertThat(item.getValue().getQuestionId()).isEqualTo(502L);
        assertThat(item.getValue().getStage()).isZero();       // 部分答对 → UNSURE,阶段不动
        assertThat(item.getValue().getIntervalDays()).isEqualTo(1);

        ArgumentCaptor<AnswerRecord> record = ArgumentCaptor.forClass(AnswerRecord.class);
        verify(answerRepository).save(record.capture());
        assertThat(record.getValue().getConfidence()).isEqualTo("UNSURE");
    }

    @Test
    @DisplayName("主观题全部答到:记 SURE,阶段推进到 1")
    void selfScoreFullMarksAdvancesStage() {
        existing(subjective, null);

        service.submitSelfScore(88L, 502L, new SelfScoreDTO(3, 3));

        ArgumentCaptor<ReviewItem> item = ArgumentCaptor.forClass(ReviewItem.class);
        verify(reviewItemRepository).save(item.capture());
        assertThat(item.getValue().getStage()).isEqualTo(1);
        assertThat(item.getValue().getIntervalDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("重复自评:只改分数,不再重复推进阶段")
    void repeatedSelfScoreDoesNotAdvanceStageAgain() {
        existing(subjective, 2);   // 已经自评过

        service.submitSelfScore(88L, 502L, new SelfScoreDTO(1, 3));

        verify(reviewItemRepository, never()).save(any(ReviewItem.class));
    }

    @Test
    @DisplayName("自评不能用在客观题上")
    void selfScoreRejectedForObjective() {
        assertThatThrownBy(() -> service.submitSelfScore(88L, 501L, new SelfScoreDTO(1, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客观题");
    }

    @Test
    @DisplayName("结束练习:汇总得分与正确率,写入场次与当天学习日志")
    void finishAggregatesAndWritesStudyLog() {
        AnswerRecord wrong = new AnswerRecord();
        wrong.setSessionId(88L);
        wrong.setQuestionId(501L);
        wrong.setUserAnswer("A");
        wrong.setConfidence("WRONG");
        wrong.setIsCorrect(false);
        wrong.setSpentSeconds(8);
        AnswerRecord partial = new AnswerRecord();
        partial.setSessionId(88L);
        partial.setQuestionId(502L);
        partial.setConfidence("UNSURE");
        partial.setSelfScore(2);
        partial.setTotalPoints(3);
        partial.setSpentSeconds(60);
        when(answerRepository.findBySessionIdOrderByIdAsc(88L)).thenReturn(List.of(wrong, partial));
        when(studyLogRepository.findByUserIdAndLogDate(anyLong(), any())).thenReturn(Optional.empty());
        when(studyLogRepository.save(any(StudyLog.class))).thenAnswer(i -> i.getArgument(0));

        SessionSummaryVO summary = service.finish(88L);

        assertThat(summary.answeredCount()).isEqualTo(2);
        assertThat(summary.objectiveAnsweredCount()).isEqualTo(1);
        assertThat(summary.correctCount()).isZero();
        // 客观题 2.0 × 0 + 主观题 6.0 × 2/3 = 4.0
        assertThat(summary.score()).isEqualByComparingTo("4.0");
        assertThat(summary.totalScore()).isEqualByComparingTo("8.0");
        assertThat(summary.accuracy()).isEqualTo(0.0);
        assertThat(summary.wrongQuestions()).hasSize(2);

        assertThat(session.getStatus()).isEqualTo("FINISHED");
        assertThat(session.getFinishedAt()).isNotNull();

        ArgumentCaptor<StudyLog> log = ArgumentCaptor.forClass(StudyLog.class);
        verify(studyLogRepository).save(log.capture());
        assertThat(log.getValue().getSessionCount()).isEqualTo(1);
        assertThat(log.getValue().getQuestionCount()).isEqualTo(2);
        assertThat(log.getValue().getCorrectCount()).isZero();
    }

    @Test
    @DisplayName("结束练习幂等:重复调用不再重复累加学习日志")
    void finishIsIdempotentForStudyLog() {
        when(answerRepository.findBySessionIdOrderByIdAsc(88L)).thenReturn(List.of());
        StudyLog existingLog = new StudyLog();
        existingLog.setUserId(1L);
        existingLog.setLogDate(java.time.LocalDate.now());
        existingLog.setSessionCount(1);
        existingLog.setQuestionCount(3);
        when(studyLogRepository.findByUserIdAndLogDate(anyLong(), any())).thenReturn(Optional.of(existingLog));
        when(studyLogRepository.save(any(StudyLog.class))).thenAnswer(i -> i.getArgument(0));

        service.finish(88L);
        service.finish(88L);

        // 两次调用各累加一次 sessionCount(当前实现语义:每次结束都算一次练习)
        assertThat(existingLog.getSessionCount()).isEqualTo(3);
        verify(studyLogRepository, times(2)).save(any(StudyLog.class));
    }

    @Test
    @DisplayName("已结束的场次不能再作答")
    void finishedSessionRejectsAnswers() {
        session.setStatus("FINISHED");
        assertThatThrownBy(() -> service.submitAnswer(88L, new AnswerSubmitDTO(501L, "B", "SURE", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");
    }

    @Test
    @DisplayName("被放弃的场次仍可继续作答,并恢复为进行中(避免 localStorage 恢复后死路)")
    void abandonedSessionCanBeResumed() {
        session.setStatus("ABANDONED");
        service.submitAnswer(88L, new AnswerSubmitDTO(501L, "B", "SURE", 5));
        assertThat(session.getStatus()).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("场次不存在时报 3001")
    void unknownSessionRejected() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submitAnswer(999L, new AnswerSubmitDTO(501L, "B", "SURE", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("场次不存在");
    }

    @Test
    @DisplayName("作答文本会被 trim,空白视为未作答")
    void blankAnswerTreatedAsEmpty() {
        service.submitAnswer(88L, new AnswerSubmitDTO(501L, "   ", "SURE", 3));
        ArgumentCaptor<AnswerRecord> record = ArgumentCaptor.forClass(AnswerRecord.class);
        verify(answerRepository).save(record.capture());
        assertThat(record.getValue().getUserAnswer()).isNull();
        assertThat(record.getValue().getIsCorrect()).isFalse();
    }

    @Test
    @DisplayName("未知题目报 2003,不会静默记一条空作答")
    void unknownQuestionRejected() {
        when(questionRepository.findById(777L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submitAnswer(88L, new AnswerSubmitDTO(777L, "B", "SURE", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("题目不存在");
        verify(answerRepository, never()).save(any(AnswerRecord.class));
    }

    @Test
    @DisplayName("用时为负或缺失时按 0 处理")
    void spentSecondsNormalized() {
        service.submitAnswer(88L, new AnswerSubmitDTO(501L, "B", "SURE", -5));
        ArgumentCaptor<AnswerRecord> record = ArgumentCaptor.forClass(AnswerRecord.class);
        verify(answerRepository).save(record.capture());
        assertThat(record.getValue().getSpentSeconds()).isZero();
    }

    @Test
    @DisplayName("删除练习记录:回滚本次触碰过的复习条目,并扣减当天学习日志")
    void deleteRollsBackReviewItemsAndStudyLog() {
        AnswerRecord answer = new AnswerRecord();
        answer.setId(7L);
        answer.setSessionId(88L);
        answer.setQuestionId(501L);
        answer.setIsCorrect(false);
        answer.setConfidence("WRONG");
        when(answerRepository.findBySessionIdOrderByIdAsc(88L)).thenReturn(List.of(answer));

        ReviewItem item = new ReviewItem();
        item.setId(3L);
        item.setQuestionId(501L);
        item.setLastReviewAt(LocalDateTime.now().minusMinutes(1));   // 落在本次练习窗口内
        when(reviewItemRepository.findByUserIdAndQuestionId(anyLong(), anyLong()))
                .thenReturn(Optional.of(item));

        StudyLog log = new StudyLog();
        log.setUserId(1L);
        log.setLogDate(java.time.LocalDate.now());
        log.setSessionCount(1);
        log.setQuestionCount(5);
        log.setCorrectCount(2);
        log.setStudySeconds(120);
        when(studyLogRepository.findByUserIdAndLogDate(anyLong(), any())).thenReturn(Optional.of(log));

        service.delete(88L);

        verify(reviewItemRepository).delete(item);
        verify(sessionRepository).delete(session);
        assertThat(log.getSessionCount()).isZero();
        assertThat(log.getQuestionCount()).isEqualTo(4);
        assertThat(log.getCorrectCount()).isEqualTo(2);   // 该题为错题,答对数不减
    }

    @Test
    @DisplayName("删除练习记录后当天计数全归零时,日志行本身也删掉(否则会被误判成已打卡)")
    void deleteRemovesEmptiedStudyLog() {
        when(answerRepository.findBySessionIdOrderByIdAsc(88L)).thenReturn(List.of());
        StudyLog log = new StudyLog();
        log.setUserId(1L);
        log.setLogDate(java.time.LocalDate.now());
        log.setSessionCount(1);   // 只有它本身这一次练习
        when(studyLogRepository.findByUserIdAndLogDate(anyLong(), any())).thenReturn(Optional.of(log));

        service.delete(88L);

        verify(studyLogRepository).delete(log);
        verify(studyLogRepository, never()).save(any(StudyLog.class));
    }

    @Test
    @DisplayName("删除不存在的场次报 3001")
    void deleteUnknownSessionRejected() {
        when(sessionRepository.findById(777L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(777L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("场次不存在");
    }
}