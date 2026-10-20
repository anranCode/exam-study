package com.studyexam.review;

import com.studyexam.chapter.Chapter;
import com.studyexam.chapter.ChapterRepository;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.common.PageResult;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.question.QuestionType;
import com.studyexam.review.dto.DueSummaryVO;
import com.studyexam.review.dto.ReviewItemVO;
import com.studyexam.review.dto.ReviewTypeCountVO;
import com.studyexam.setting.TableAvailability;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 复习队列(错题本)的读写。
 *
 * <p>{@code review_item} / {@code study_log} 由 V4 迁移建立;
 * 每日题量来自 {@code user_setting}(阶段 7 建立),表不存在时用默认值 10。
 *
 * <p>排期推演**不在这里** —— 那是 {@link Sm2Scheduler} 的事。本类只负责查询队列、
 * 以及两个人工干预动作(标记已掌握、移出错题本)。
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private static final int DEFAULT_DAILY_QUESTION_COUNT = 10;
    private static final Long USER_ID = 1L;
    /** 分页上限,与 03-api-spec.md「size 最大 200」一致。 */
    private static final int MAX_PAGE_SIZE = 200;
    private static final String STATUS_LEARNING = "LEARNING";
    private static final String STATUS_MASTERED = "MASTERED";
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SubjectRepository subjectRepository;
    private final ReviewStatsProvider reviewStats;
    private final TableAvailability tables;
    private final ReviewItemRepository reviewItemRepository;
    private final QuestionRepository questionRepository;
    private final ChapterRepository chapterRepository;

    @PersistenceContext
    private EntityManager em;

    public ReviewService(SubjectRepository subjectRepository,
                         ReviewStatsProvider reviewStats,
                         TableAvailability tables,
                         ReviewItemRepository reviewItemRepository,
                         QuestionRepository questionRepository,
                         ChapterRepository chapterRepository) {
        this.subjectRepository = subjectRepository;
        this.reviewStats = reviewStats;
        this.tables = tables;
        this.reviewItemRepository = reviewItemRepository;
        this.questionRepository = questionRepository;
        this.chapterRepository = chapterRepository;
    }

    @Transactional(readOnly = true)
    public DueSummaryVO dueSummary() {
        LocalDate today = LocalDate.now();
        List<Subject> subjects = subjectRepository.findAllByOrderBySortOrderAscIdAsc();

        List<DueSummaryVO.SubjectDue> bySubject = subjects.stream()
                .map(s -> new DueSummaryVO.SubjectDue(s.getId(), s.getShortName(), s.getColor(),
                        reviewStats.dueCount(s.getId())))
                .filter(d -> d.dueCount() > 0)
                .toList();
        long totalDue = bySubject.stream().mapToLong(DueSummaryVO.SubjectDue::dueCount).sum();

        return new DueSummaryVO(today, totalDue, bySubject, dailyQuestionQuota(), checkedInToday(today));
    }

    // ── 错题本 ──────────────────────────────────────────────

    /**
     * 错题本列表。四个筛选都可选:科目、题型、队列状态、是否只要到期的。
     *
     * <p>{@code due} 是文档之外补的参数:前端要按「是否到期」筛,而只有 {@code status + subjectId}
     * 时这个筛选只能在前端本地做,一分页就不准了。{@code type} 同理,用于「只复习某一种题型」。
     *
     * <p>排序由 {@link ReviewItemRepository#findIdsForList} 决定:答错次数多的排最前面。
     */
    @Transactional(readOnly = true)
    public PageResult<ReviewItemVO> listItems(Long subjectId, String type, String status, Boolean due,
                                              Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        boolean dueOnly = Boolean.TRUE.equals(due);
        String queueStatus = normalizeStatus(status);
        String questionType = normalizeType(type);
        LocalDateTime now = LocalDateTime.now();

        long total = reviewItemRepository.countForList(USER_ID, subjectId, questionType,
                queueStatus, dueOnly, now);
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        List<Long> ids = reviewItemRepository.findIdsForList(
                USER_ID, subjectId, questionType, queueStatus, dueOnly, now, s, (p - 1) * s);
        return PageResult.of(assemble(ids, now), total, p, s);
    }

    /** 题型只认已知取值;空表示不限。写错就报错,避免「以为筛了其实全选」。 */
    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String t = type.trim().toUpperCase();
        QuestionType.find(t).orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID,
                "type: 未知题型 \"" + type + "\""));
        return t;
    }

    /** 状态取值只认 LEARNING / MASTERED;空表示不限。写错就报错,别静默返回空列表。 */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase();
        if (!STATUS_LEARNING.equals(s) && !STATUS_MASTERED.equals(s)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "status: 只能是 LEARNING 或 MASTERED,当前为 \"" + status + "\"");
        }
        return s;
    }

    /** 按 id 批量装配:题目、科目、章节各查一次,避免逐条 N+1。 */
    private List<ReviewItemVO> assemble(List<Long> ids, LocalDateTime now) {
        Map<Long, ReviewItem> items = reviewItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ReviewItem::getId, Function.identity()));

        List<Long> questionIds = ids.stream().map(items::get).filter(Objects::nonNull)
                .map(ReviewItem::getQuestionId).distinct().toList();
        Map<Long, Question> questions = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        Map<Long, Subject> subjects = subjectRepository
                .findAllById(questions.values().stream().map(Question::getSubjectId).distinct().toList())
                .stream().collect(Collectors.toMap(Subject::getId, Function.identity()));

        List<Long> chapterIds = questions.values().stream().map(Question::getChapterId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Chapter> chapters = chapterIds.isEmpty() ? Map.of()
                : chapterRepository.findAllById(chapterIds).stream()
                        .collect(Collectors.toMap(Chapter::getId, Function.identity()));

        return ids.stream()
                .map(items::get)
                .filter(Objects::nonNull)
                .filter(ri -> questions.containsKey(ri.getQuestionId()))   // 题目已被删则跳过
                .map(ri -> toVO(ri, questions.get(ri.getQuestionId()), subjects, chapters, now))
                .toList();
    }

    private ReviewItemVO toVO(ReviewItem item, Question q, Map<Long, Subject> subjects,
                              Map<Long, Chapter> chapters, LocalDateTime now) {
        Subject subject = subjects.get(q.getSubjectId());
        Chapter chapter = q.getChapterId() == null ? null : chapters.get(q.getChapterId());
        String masteryStage = ReviewItemVO.masteryStageOf(item.getStatus(), item.getStage());
        return new ReviewItemVO(
                q.getId(),
                q.getSubjectId(),
                subject == null ? null : subject.getShortName(),
                subject == null ? null : subject.getColor(),
                q.getChapterId(),
                chapter == null ? null : chapter.getName(),
                q.getType(),
                QuestionType.find(q.getType()).map(QuestionType::getLabel).orElse(q.getType()),
                q.getStem(),
                item.getWrongCount(),
                item.getCorrectStreak(),
                item.getStage(),
                item.getIntervalDays(),
                item.getEaseFactor(),
                masteryStage,
                ReviewItemVO.masteryLabelOf(masteryStage),
                item.getStatus(),
                item.getLastReviewAt(),
                item.getNextReviewAt(),
                item.getNextReviewAt() != null && !item.getNextReviewAt().isAfter(now));
    }

    /**
     * 错题本可选的题型清单(只列当前筛选范围内真有错题的题型)。
     *
     * <p>给前端「按题型复习」的下拉用:不传科目时是全部错题的题型,
     * 传了科目就是该科目下的题型。{@code status} 跟随列表的状态筛选:
     * 看「已掌握」时就该数已掌握的题,否则下拉里会出现一堆切过去就空的题型。
     */
    @Transactional(readOnly = true)
    public List<ReviewTypeCountVO> typeCounts(Long subjectId, String status) {
        String queueStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
        return reviewItemRepository.countGroupedByType(USER_ID, subjectId, queueStatus).stream()
                .map(row -> {
                    String type = String.valueOf(row[0]);
                    long count = ((Number) row[1]).longValue();
                    String label = QuestionType.find(type).map(QuestionType::getLabel).orElse(type);
                    return new ReviewTypeCountVO(type, label, count);
                })
                .toList();
    }

    /**
     * 错题练习池的题型分布(只数真能抽到的题)。
     *
     * <p>和 {@link #typeCounts} 的差别:这里额外要求题目是 PUBLISHED。
     * 错题本列表里可以出现待校对的题(以前发布过、后来改回待校对的),
     * 但它们进不了抽题池;自由练习页的「可练 N 题」若把它们算进去,
     * 用户就会看到一个比实际能抽到的更大的数字。
     */
    @Transactional(readOnly = true)
    public List<ReviewTypeCountVO> wrongPoolTypeCounts(Long subjectId) {
        return reviewItemRepository.countWrongPoolGroupedByType(USER_ID, subjectId).stream()
                .map(row -> {
                    String type = String.valueOf(row[0]);
                    long count = ((Number) row[1]).longValue();
                    String label = QuestionType.find(type).map(QuestionType::getLabel).orElse(type);
                    return new ReviewTypeCountVO(type, label, count);
                })
                .toList();
    }

    /** 手动标记已掌握:直接出队。已经是 MASTERED 再调不报错(幂等)。 */
    @Transactional
    public void markMastered(Long questionId) {
        ReviewItem item = requireItem(questionId);
        if (!STATUS_MASTERED.equals(item.getStatus())) {
            item.setStatus(STATUS_MASTERED);
            reviewItemRepository.save(item);
            log.info("复习条目手动标记已掌握 questionId={}", questionId);
        }
    }

    /** 移出错题本:连同这条排期记录一起删掉,以后不会再出现。 */
    @Transactional
    public void remove(Long questionId) {
        ReviewItem item = requireItem(questionId);
        reviewItemRepository.delete(item);
        log.info("复习条目已移出错题本 questionId={}", questionId);
    }

    private ReviewItem requireItem(Long questionId) {
        return reviewItemRepository.findByUserIdAndQuestionId(USER_ID, questionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.QUESTION_NOT_FOUND,
                        "这道题不在错题本里:questionId=" + questionId));
    }

    /**
     * 今日是否已打卡:当天做过题才算。
     * 用 question_count > 0 而不是「有记录」——删除练习记录后留下的全零行不算打卡。
     */
    private boolean checkedInToday(LocalDate today) {
        Object result = em.createNativeQuery(
                        "SELECT COUNT(*) FROM study_log WHERE log_date = ?1 AND question_count > 0")
                .setParameter(1, today)
                .getSingleResult();
        return ((Number) result).longValue() > 0;
    }

    /** 每日任务题量取自 user_setting(阶段 7 建立),未建表时用默认值。 */
    private int dailyQuestionQuota() {
        if (!tables.exists("user_setting")) {
            return DEFAULT_DAILY_QUESTION_COUNT;
        }
        try {
            Object result = em.createNativeQuery(
                            "SELECT daily_question_count FROM user_setting WHERE user_id = ?1")
                    .setParameter(1, USER_ID)
                    .getSingleResult();
            return ((Number) result).intValue();
        } catch (Exception e) {
            return DEFAULT_DAILY_QUESTION_COUNT;
        }
    }
}
