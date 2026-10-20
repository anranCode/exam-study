package com.studyexam.practice;

import com.studyexam.chapter.Chapter;
import com.studyexam.chapter.ChapterRepository;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.common.PageResult;
import com.studyexam.practice.dto.AnswerProgressVO;
import com.studyexam.practice.dto.AnswerResultVO;
import com.studyexam.practice.dto.AnswerSubmitDTO;
import com.studyexam.practice.dto.OngoingSessionVO;
import com.studyexam.practice.dto.PracticeQuestionVO;
import com.studyexam.practice.dto.PracticeSessionCreateDTO;
import com.studyexam.practice.dto.PracticeSessionDetailVO;
import com.studyexam.practice.dto.PracticeSessionListItemVO;
import com.studyexam.practice.dto.PracticeSessionVO;
import com.studyexam.practice.dto.SelfScoreDTO;
import com.studyexam.practice.dto.SessionAnswerVO;
import com.studyexam.practice.dto.SessionSummaryVO;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.question.QuestionType;
import com.studyexam.review.ReviewItem;
import com.studyexam.review.ReviewItemRepository;
import com.studyexam.review.Sm2Scheduler;
import com.studyexam.stats.StudyLog;
import com.studyexam.stats.StudyLogRepository;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 练习与判分。
 *
 * <p>关键约定(见 docs/study-exam/backend/01-architecture.md 第三节、03-api-spec.md 第四节):
 * <ul>
 *   <li>取题响应**不含答案与解析**——防作弊,也防前端逻辑出错提前暴露答案;</li>
 *   <li>作答按 {@code sessionId + questionId} 唯一,重复提交返回已有记录而不新建(幂等);</li>
 *   <li>主观题后端不判分:作答时先占位,等自评提交时按自评比例定信心度并推进排期;
 *       若一直没自评,结束练习时按 UNSURE 兜底入队,保证错题不会漏进复习计划;</li>
 *   <li>答错一律记 {@code confidence = WRONG},即使用户点的是「确认」——排期准确性靠它。</li>
 * </ul>
 */
@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);
    private static final Long USER_ID = 1L;

    private final PracticeSessionRepository sessionRepository;
    private final AnswerRecordRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;
    private final ReviewItemRepository reviewItemRepository;
    private final StudyLogRepository studyLogRepository;
    private final SessionGenerator sessionGenerator;
    private final GradingService gradingService;

    public PracticeService(PracticeSessionRepository sessionRepository,
                           AnswerRecordRepository answerRepository,
                           QuestionRepository questionRepository,
                           SubjectRepository subjectRepository,
                           ChapterRepository chapterRepository,
                           ReviewItemRepository reviewItemRepository,
                           StudyLogRepository studyLogRepository,
                           SessionGenerator sessionGenerator,
                           GradingService gradingService) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
        this.chapterRepository = chapterRepository;
        this.reviewItemRepository = reviewItemRepository;
        this.studyLogRepository = studyLogRepository;
        this.sessionGenerator = sessionGenerator;
        this.gradingService = gradingService;
    }

    // ── 开始练习 ────────────────────────────────────────────

    @Transactional
    public PracticeSessionVO createSession(PracticeSessionCreateDTO dto) {
        if (dto == null || dto.mode() == null || dto.mode().isBlank()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "mode: 组题模式不能为空");
        }
        LocalDateTime now = LocalDateTime.now();

        // 同一时间只保留一个进行中的场次:旧的置为放弃,并把 id 告知前端
        Long previousOngoingId = null;
        for (PracticeSession ongoing : sessionRepository.findByUserIdAndStatus(USER_ID, "ONGOING")) {
            ongoing.setStatus("ABANDONED");
            ongoing.setFinishedAt(now);
            previousOngoingId = ongoing.getId();
        }

        SessionGenerator.Picked picked = sessionGenerator.generate(
                dto.mode(), dto.subjectId(), dto.chapterId(), dto.count(), dto.paperId(),
                dto.questionIds(), dto.types());

        PracticeSession session = new PracticeSession();
        session.setUserId(USER_ID);
        session.setMode(dto.mode().trim().toUpperCase());
        session.setSubjectId(dto.subjectId());
        session.setPaperId(picked.paperId());
        session.setStatus("ONGOING");
        session.setTotalCount(picked.questions().size());
        session.setScore(BigDecimal.ZERO);
        PracticeSession saved = sessionRepository.save(session);

        List<PracticeQuestionVO> questions = toQuestionVOs(picked.questions());
        BigDecimal totalScore = questions.stream()
                .map(PracticeQuestionVO::score)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("创建练习场次 id={} mode={} 题数={}", saved.getId(), saved.getMode(), questions.size());
        return new PracticeSessionVO(saved.getId(), saved.getMode(), questions.size(),
                totalScore, previousOngoingId, questions);
    }

    // ── 提交单题作答 ────────────────────────────────────────

    @Transactional
    public AnswerResultVO submitAnswer(Long sessionId, AnswerSubmitDTO dto) {
        PracticeSession session = loadForAnswering(sessionId);
        if (dto == null || dto.questionId() == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "questionId: 题目不能为空");
        }
        Question question = questionRepository.findById(dto.questionId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.QUESTION_NOT_FOUND,
                        "题目不存在:id=" + dto.questionId()));

        LocalDateTime now = LocalDateTime.now();
        Optional<AnswerRecord> existing = answerRepository
                .findBySessionIdAndQuestionId(sessionId, question.getId());
        boolean isReview = isFromReviewQueue(question.getId(), session);

        if (existing.isPresent()) {
            // 幂等:重复提交返回已有记录,不新建、不重复推进排期
            return toResultVO(session, question, existing.get(), isReview);
        }

        Boolean correct = gradingService.grade(question, dto.userAnswer());
        AnswerRecord record = new AnswerRecord();
        record.setSessionId(sessionId);
        record.setQuestionId(question.getId());
        record.setUserAnswer(trimToNull(dto.userAnswer()));
        record.setSpentSeconds(dto.spentSeconds() == null ? 0 : Math.max(0, dto.spentSeconds()));

        if (correct == null) {
            // 主观题:等自评时再定信心度(见类注释)
            record.setConfidence("UNSURE");
            record.setIsCorrect(null);
        } else {
            String confidence = correct ? declaredConfidence(dto.confidence()) : "WRONG";
            record.setConfidence(confidence);
            record.setIsCorrect(correct);
            advanceSchedule(question.getId(), confidence, now);
        }
        AnswerRecord savedRecord = answerRepository.save(record);

        session.setAnsweredCount(session.getAnsweredCount() + 1);
        if (Boolean.TRUE.equals(correct)) {
            session.setCorrectCount(session.getCorrectCount() + 1);
        }
        sessionRepository.save(session);

        return toResultVO(session, question, savedRecord, isReview);
    }

    // ── 主观题自评 ──────────────────────────────────────────

    @Transactional
    public AnswerResultVO submitSelfScore(Long sessionId, Long questionId, SelfScoreDTO dto) {
        PracticeSession session = loadForAnswering(sessionId);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.QUESTION_NOT_FOUND,
                        "题目不存在:id=" + questionId));
        if (gradingService.isAutoGraded(question)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "该题是客观题,由后端自动判分,不需要自评");
        }
        AnswerRecord record = answerRepository.findBySessionIdAndQuestionId(sessionId, questionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID,
                        "该题还没有提交作答,不能自评"));

        int total = question.getAnswerPoints() == null ? 0 : question.getAnswerPoints().size();
        if (total == 0) {
            total = dto == null || dto.totalPoints() == null ? 0 : dto.totalPoints();
        }
        int score = dto == null || dto.selfScore() == null ? 0 : dto.selfScore();
        score = Math.max(0, Math.min(score, total));

        // 注意:这个标记必须在写入自评之前取,否则永远是 false(曾经踩过)
        boolean firstScoring = record.getSelfScore() == null;

        record.setSelfScore(score);
        record.setTotalPoints(total);

        // 自评结果映射为信心度:全对 → SURE;一点没答到 → WRONG;中间 → UNSURE(明天再练)
        String confidence = total > 0 && score >= total ? "SURE" : (score == 0 ? "WRONG" : "UNSURE");
        record.setConfidence(confidence);
        answerRepository.save(record);

        // 主观题在作答时不推进排期,这里首次推进;重复自评只改分数,不再动排期(否则阶段会重复累加)
        if (firstScoring) {
            advanceSchedule(question.getId(), confidence, LocalDateTime.now());
        }

        return toResultVO(session, question, record, isFromReviewQueue(question.getId(), session));
    }

    // ── 结束本轮 ────────────────────────────────────────────

    @Transactional
    public SessionSummaryVO finish(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SESSION_NOT_FOUND,
                        "练习场次不存在:id=" + sessionId));

        List<AnswerRecord> answers = answerRepository.findBySessionIdOrderByIdAsc(sessionId);
        LocalDateTime now = LocalDateTime.now();

        // 兜底:主观题一直没自评的,按 UNSURE 入队,避免错题漏进复习计划
        LocalDateTime sessionStart = session.getStartedAt() == null ? now : session.getStartedAt();
        for (AnswerRecord a : answers) {
            if (a.getIsCorrect() == null && a.getSelfScore() == null
                    && !hasScheduled(a.getQuestionId(), session)) {
                advanceSchedule(a.getQuestionId(), "UNSURE", now);
            }
        }

        if (!"FINISHED".equals(session.getStatus())) {
            session.setStatus("FINISHED");
            session.setFinishedAt(now);
            int duration = (int) java.time.Duration.between(sessionStart, now).getSeconds();
            session.setDurationSeconds(Math.max(duration, 0));
            session.setAnsweredCount(answers.size());
            session.setCorrectCount((int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count());
            sessionRepository.save(session);
        }

        SessionSummaryVO summary = buildSummary(session, answers, sessionStart, now);
        session.setScore(summary.score());
        sessionRepository.save(session);

        accrueStudyLog(session, answers, sessionStart, now);
        log.info("结束练习 id={} 作答={}/{} 得分={}", sessionId, summary.answeredCount(),
                summary.totalCount(), summary.score());
        return summary;
    }

    // ── 记录查询 ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResult<PracticeSessionListItemVO> list(int page, int size) {
        int p = Math.max(page, 1);
        int s = size <= 0 ? 20 : Math.min(size, 200);
        Page<PracticeSession> result = sessionRepository
                .findByUserIdOrderByStartedAtDesc(USER_ID, PageRequest.of(p - 1, s));

        Map<Long, Subject> subjects = loadSubjects(result.getContent().stream()
                .map(PracticeSession::getSubjectId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<PracticeSessionListItemVO> list = result.getContent().stream()
                .map(session -> toListItem(session, subjects))
                .toList();
        return PageResult.of(list, result.getTotalElements(), p, s);
    }

    @Transactional(readOnly = true)
    public PracticeSessionDetailVO detail(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SESSION_NOT_FOUND,
                        "练习场次不存在:id=" + sessionId));
        List<AnswerRecord> answers = answerRepository.findBySessionIdOrderByIdAsc(sessionId);
        Map<Long, Question> questions = loadQuestions(answers.stream()
                .map(AnswerRecord::getQuestionId).toList());
        java.util.Set<Long> subjectIds = questions.values().stream()
                .map(Question::getSubjectId).collect(Collectors.toSet());
        if (session.getSubjectId() != null) {
            subjectIds.add(session.getSubjectId());
        }
        Map<Long, Subject> subjects = loadSubjects(subjectIds);
        Map<Long, Chapter> chapters = loadChapters(questions.values().stream()
                .map(Question::getChapterId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<SessionAnswerVO> answerVOs = new ArrayList<>();
        for (AnswerRecord a : answers) {
            Question q = questions.get(a.getQuestionId());
            if (q == null) {
                continue;
            }
            Subject subject = subjects.get(q.getSubjectId());
            Chapter chapter = q.getChapterId() == null ? null : chapters.get(q.getChapterId());
            answerVOs.add(new SessionAnswerVO(
                    q.getId(), q.getStem(), q.getType(), typeLabel(q),
                    subject == null ? null : subject.getShortName(),
                    chapter == null ? null : chapter.getName(),
                    a.getUserAnswer(), a.getIsCorrect(), a.getConfidence(),
                    a.getSelfScore(), a.getTotalPoints(),
                    q.getAnswer(), q.getAnswerPoints(), q.getAnalysis(), a.getAnsweredAt()));
        }

        Subject sessionSubject = session.getSubjectId() == null ? null : subjects.get(session.getSubjectId());
        return new PracticeSessionDetailVO(
                session.getId(), session.getMode(), session.getStatus(),
                sessionSubject == null ? null : sessionSubject.getShortName(),
                session.getTotalCount(), session.getAnsweredCount(), session.getCorrectCount(),
                session.getScore(), null, accuracy(answers),
                session.getStartedAt(), session.getFinishedAt(), session.getDurationSeconds(),
                answerVOs);
    }

    @Transactional(readOnly = true)
    public OngoingSessionVO ongoing() {
        return sessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ONGOING")
                .map(s -> new OngoingSessionVO(s.getId(), s.getMode(),
                        s.getAnsweredCount(), s.getTotalCount(), s.getStartedAt()))
                .orElse(null);
    }

    /**
     * 删除练习记录。同步回滚这次练习造成的两类副作用:
     * ① 复习队列——本次练习是最后触碰它的(最后复习时间落在本次练习时间窗内)才删除该条目;
     * ② 学习日志——把本次练习贡献的场次/题数/时长从当天日志里减掉(下限 0)。
     */
    @Transactional
    public void delete(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SESSION_NOT_FOUND,
                        "练习场次不存在:id=" + sessionId));
        List<AnswerRecord> answers = answerRepository.findBySessionIdOrderByIdAsc(sessionId);
        LocalDateTime from = session.getStartedAt();
        LocalDateTime to = session.getFinishedAt() == null ? LocalDateTime.now() : session.getFinishedAt();

        int rolledBackItems = 0;
        for (AnswerRecord a : answers) {
            ReviewItem item = reviewItemRepository
                    .findByUserIdAndQuestionId(USER_ID, a.getQuestionId()).orElse(null);
            if (item == null || item.getLastReviewAt() == null || from == null) {
                continue;
            }
            if (!item.getLastReviewAt().isBefore(from) && !item.getLastReviewAt().isAfter(to)) {
                reviewItemRepository.delete(item);
                rolledBackItems++;
            }
        }

        LocalDate logDate = (session.getFinishedAt() == null ? LocalDateTime.now() : session.getFinishedAt())
                .toLocalDate();
        studyLogRepository.findByUserIdAndLogDate(USER_ID, logDate).ifPresent(log -> {
            log.setSessionCount(Math.max(0, log.getSessionCount() - 1));
            log.setQuestionCount(Math.max(0, log.getQuestionCount() - answers.size()));
            int correct = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
            log.setCorrectCount(Math.max(0, log.getCorrectCount() - correct));
            log.setStudySeconds(Math.max(0, log.getStudySeconds()
                    - (session.getDurationSeconds() == null ? 0 : session.getDurationSeconds())));
            log.setReviewDone(Math.max(0, log.getReviewDone()
                    - (int) answerRepository.countReviewDoneInSession(sessionId, session.getStartedAt())));
            // 全部归零说明当天已经没有任何练习贡献,留着这行会让「今日是否打卡」误判
            boolean empty = log.getSessionCount() == 0 && log.getQuestionCount() == 0
                    && log.getStudySeconds() == 0 && log.getReviewDone() == 0;
            if (empty) {
                studyLogRepository.delete(log);
            } else {
                studyLogRepository.save(log);
            }
        });

        sessionRepository.delete(session);   // answer_record 由外键 ON DELETE CASCADE 连带清理
        log.info("删除练习记录 id={} 连带回滚复习条目 {} 条", sessionId, rolledBackItems);
    }

    // ── 内部:排期与统计 ────────────────────────────────────

    /** 按信心度推进该题的复习排期(不存在则新建条目)。 */
    private void advanceSchedule(Long questionId, String confidence, LocalDateTime now) {
        ReviewItem item = reviewItemRepository.findByUserIdAndQuestionId(USER_ID, questionId)
                .orElseGet(() -> Sm2Scheduler.newItem(questionId, now));
        Sm2Scheduler.apply(item, confidence, now);
        reviewItemRepository.save(item);
    }

    /** 该题是否已经进入复习队列(用于主观题避免重复推进)。 */
    private boolean hasScheduled(Long questionId, PracticeSession session) {
        return reviewItemRepository.findByUserIdAndQuestionId(USER_ID, questionId)
                .map(item -> item.getLastReviewAt() != null
                        && session.getStartedAt() != null
                        && !item.getLastReviewAt().isBefore(session.getStartedAt()))
                .orElse(false);
    }

    private SessionSummaryVO buildSummary(PracticeSession session, List<AnswerRecord> answers,
                                          LocalDateTime from, LocalDateTime to) {
        Map<Long, Question> questions = loadQuestions(answers.stream()
                .map(AnswerRecord::getQuestionId).toList());
        Map<Long, Subject> subjects = loadSubjects(questions.values().stream()
                .map(Question::getSubjectId).collect(Collectors.toSet()));

        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal totalScore = BigDecimal.ZERO;
        int objectiveAnswered = 0;
        int correct = 0;
        List<SessionSummaryVO.WrongQuestion> wrong = new ArrayList<>();

        for (AnswerRecord a : answers) {
            Question q = questions.get(a.getQuestionId());
            if (q == null) {
                continue;
            }
            BigDecimal qScore = q.getScore() == null ? BigDecimal.ZERO : q.getScore();
            totalScore = totalScore.add(qScore);
            Subject subject = subjects.get(q.getSubjectId());

            if (a.getIsCorrect() != null) {
                objectiveAnswered++;
                if (a.getIsCorrect()) {
                    correct++;
                    earned = earned.add(qScore);
                } else {
                    wrong.add(wrongQuestion(q, subject, a));
                }
            } else if (a.getSelfScore() != null && a.getTotalPoints() != null && a.getTotalPoints() > 0) {
                // 主观题按自评比例计分
                BigDecimal ratio = BigDecimal.valueOf(a.getSelfScore())
                        .divide(BigDecimal.valueOf(a.getTotalPoints()), 4, RoundingMode.HALF_UP);
                earned = earned.add(qScore.multiply(ratio));
                if (a.getSelfScore() < a.getTotalPoints()) {
                    wrong.add(wrongQuestion(q, subject, a));
                }
            } else {
                wrong.add(wrongQuestion(q, subject, a));
            }
        }

        int duration = (int) java.time.Duration.between(from, to).getSeconds();
        Long sessionId = session.getId();
        int reviewCreated = (int) reviewItemRepository.countCreatedSince(USER_ID, from);
        int reviewUpdated = (int) reviewItemRepository.countUpdatedSince(USER_ID, from);

        return new SessionSummaryVO(
                sessionId, session.getMode(), session.getTotalCount(), answers.size(),
                objectiveAnswered, correct,
                earned.setScale(1, RoundingMode.HALF_UP),
                totalScore.setScale(1, RoundingMode.HALF_UP),
                objectiveAnswered == 0 ? null
                        : BigDecimal.valueOf(correct)
                            .divide(BigDecimal.valueOf(objectiveAnswered), 4, RoundingMode.HALF_UP)
                            .doubleValue(),
                Math.max(duration, 0), reviewCreated, reviewUpdated, wrong);
    }

    private SessionSummaryVO.WrongQuestion wrongQuestion(Question q, Subject subject, AnswerRecord a) {
        return new SessionSummaryVO.WrongQuestion(
                q.getId(), q.getStem(), q.getType(), typeLabel(q),
                subject == null ? null : subject.getShortName(),
                a.getUserAnswer(), q.getAnswer(), false);
    }

    private void accrueStudyLog(PracticeSession session, List<AnswerRecord> answers,
                                LocalDateTime from, LocalDateTime to) {
        LocalDate date = to.toLocalDate();
        StudyLog log = studyLogRepository.findByUserIdAndLogDate(USER_ID, date)
                .orElseGet(() -> {
                    StudyLog created = new StudyLog();
                    created.setUserId(USER_ID);
                    created.setLogDate(date);
                    return created;
                });
        log.setSessionCount(log.getSessionCount() + 1);
        log.setQuestionCount(log.getQuestionCount() + answers.size());
        int correct = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
        log.setCorrectCount(log.getCorrectCount() + correct);
        log.setStudySeconds(log.getStudySeconds()
                + (session.getDurationSeconds() == null ? 0 : session.getDurationSeconds()));
        log.setReviewDone(log.getReviewDone()
                + (int) answerRepository.countReviewDoneInSession(session.getId(), from));
        studyLogRepository.save(log);
    }

    // ── 内部:装配与工具 ────────────────────────────────────

    private PracticeSession loadForAnswering(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SESSION_NOT_FOUND,
                        "练习场次不存在:id=" + sessionId));
        if ("FINISHED".equals(session.getStatus())) {
            throw BusinessException.of(ErrorCode.SESSION_FINISHED, "该场次已结束,不能再作答");
        }
        // ABANDONED(被新场次顶替)仍允许继续作答,并恢复为进行中——避免 localStorage 恢复后死路
        if ("ABANDONED".equals(session.getStatus())) {
            session.setStatus("ONGOING");
            session.setFinishedAt(null);
            sessionRepository.save(session);
        }
        return session;
    }

    private boolean isFromReviewQueue(Long questionId, PracticeSession session) {
        return reviewItemRepository.findByUserIdAndQuestionId(USER_ID, questionId)
                .map(item -> item.getCreatedAt() != null && session.getStartedAt() != null
                        && item.getCreatedAt().isBefore(session.getStartedAt()))
                .orElse(false);
    }

    private String declaredConfidence(String raw) {
        if (raw == null) {
            return "SURE";
        }
        String c = raw.trim().toUpperCase();
        return "UNSURE".equals(c) ? "UNSURE" : "SURE";
    }

    private AnswerResultVO toResultVO(PracticeSession session, Question question,
                                      AnswerRecord record, boolean isReview) {
        int answered = session.getAnsweredCount();
        int total = session.getTotalCount();
        AnswerProgressVO progress = new AnswerProgressVO(answered, session.getCorrectCount(), total);
        ReviewItem item = reviewItemRepository
                .findByUserIdAndQuestionId(USER_ID, question.getId()).orElse(null);
        LocalDateTime nextReviewAt = item == null ? null : item.getNextReviewAt();
        int wrongCount = item == null ? 0 : item.getWrongCount();
        int correctStreak = item == null ? 0 : item.getCorrectStreak();
        // 「本次作答让这道题移出了错题本」= 答对了且当前处于已掌握状态
        boolean mastered = item != null && "MASTERED".equals(item.getStatus())
                && Boolean.TRUE.equals(record.getIsCorrect());

        return new AnswerResultVO(
                question.getId(),
                record.getIsCorrect(),
                question.getAnswer(),
                question.getAnswerPoints(),
                question.getAnalysis(),
                nextReviewAt,
                isReview,
                record.getSelfScore(),
                record.getTotalPoints(),
                wrongCount,
                correctStreak,
                mastered,
                progress);
    }

    private List<PracticeQuestionVO> toQuestionVOs(List<SessionGenerator.PickedQuestion> picked) {
        Map<Long, Question> questions = loadQuestions(picked.stream()
                .map(p -> p.question().getId()).toList());
        Map<Long, Subject> subjects = loadSubjects(questions.values().stream()
                .map(Question::getSubjectId).collect(Collectors.toSet()));
        Map<Long, Chapter> chapters = loadChapters(questions.values().stream()
                .map(Question::getChapterId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<PracticeQuestionVO> list = new ArrayList<>(picked.size());
        for (SessionGenerator.PickedQuestion p : picked) {
            Question q = questions.get(p.question().getId());
            if (q == null) {
                continue;
            }
            Subject subject = subjects.get(q.getSubjectId());
            Chapter chapter = q.getChapterId() == null ? null : chapters.get(q.getChapterId());
            list.add(new PracticeQuestionVO(
                    q.getId(), q.getSubjectId(),
                    subject == null ? null : subject.getShortName(),
                    subject == null ? null : subject.getColor(),
                    q.getChapterId(), chapter == null ? null : chapter.getName(),
                    q.getType(), typeLabel(q), q.getStem(), q.getOptions(), q.getScore(),
                    p.review(), p.sectionName()));
        }
        return list;
    }

    private PracticeSessionListItemVO toListItem(PracticeSession session, Map<Long, Subject> subjects) {
        Subject subject = session.getSubjectId() == null ? null : subjects.get(session.getSubjectId());
        List<AnswerRecord> answers = answerRepository.findBySessionIdOrderByIdAsc(session.getId());
        BigDecimal totalScore = BigDecimal.ZERO;
        Map<Long, Question> questions = loadQuestions(answers.stream()
                .map(AnswerRecord::getQuestionId).toList());
        for (AnswerRecord a : answers) {
            Question q = questions.get(a.getQuestionId());
            if (q != null && q.getScore() != null) {
                totalScore = totalScore.add(q.getScore());
            }
        }
        return new PracticeSessionListItemVO(
                session.getId(), session.getMode(), session.getSubjectId(),
                subject == null ? null : subject.getShortName(),
                session.getStatus(), session.getTotalCount(), session.getAnsweredCount(),
                session.getCorrectCount(), session.getScore(),
                totalScore.compareTo(BigDecimal.ZERO) == 0 ? null : totalScore.setScale(1, RoundingMode.HALF_UP),
                accuracy(answers),
                session.getStartedAt(), session.getFinishedAt(), session.getDurationSeconds());
    }

    private Double accuracy(List<AnswerRecord> answers) {
        int objective = 0;
        int correct = 0;
        for (AnswerRecord a : answers) {
            if (a.getIsCorrect() != null) {
                objective++;
                if (a.getIsCorrect()) {
                    correct++;
                }
            }
        }
        return objective == 0 ? null
                : BigDecimal.valueOf(correct)
                    .divide(BigDecimal.valueOf(objective), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private Map<Long, Question> loadQuestions(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Question> map = new HashMap<>();
        questionRepository.findAllById(new LinkedHashSet<>(ids)).forEach(q -> map.put(q.getId(), q));
        return map;
    }

    private Map<Long, Subject> loadSubjects(java.util.Collection<Long> ids) {
        Map<Long, Subject> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        subjectRepository.findAllById(ids).forEach(s -> map.put(s.getId(), s));
        return map;
    }

    private Map<Long, Chapter> loadChapters(java.util.Collection<Long> ids) {
        Map<Long, Chapter> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        chapterRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c));
        return map;
    }

    private String typeLabel(Question q) {
        return QuestionType.find(q.getType()).map(QuestionType::getLabel).orElse(q.getType());
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
