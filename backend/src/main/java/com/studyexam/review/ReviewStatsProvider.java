package com.studyexam.review;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 复习队列统计。科目列表的「到期复习」「已掌握」与今日页的到期总数都从这里取。
 *
 * <p>阶段 5 之前本类返回 0 占位;阶段 4 建好 {@code review_item} 表后改为真实统计,
 * 调用方(SubjectService / ReviewService)无需改动——这正是当初把它抽出来的原因。
 */
@Component
public class ReviewStatsProvider {

    private static final Long USER_ID = 1L;

    private final ReviewItemRepository reviewItemRepository;

    public ReviewStatsProvider(ReviewItemRepository reviewItemRepository) {
        this.reviewItemRepository = reviewItemRepository;
    }

    /** 某科目下到期该复习的题数。 */
    @Transactional(readOnly = true)
    public long dueCount(Long subjectId) {
        return reviewItemRepository.countDueBySubject(USER_ID, subjectId, LocalDateTime.now());
    }

    /** 某科目下已掌握的题数。 */
    @Transactional(readOnly = true)
    public long masteredCount(Long subjectId) {
        return reviewItemRepository.countMasteredBySubject(USER_ID, subjectId);
    }

    /** 全部科目合计的到期复习数(今日页用)。 */
    @Transactional(readOnly = true)
    public long totalDueCount() {
        return reviewItemRepository.countDue(USER_ID, LocalDateTime.now());
    }

    /** 在队列里的总题数(错题本条数)。 */
    @Transactional(readOnly = true)
    public long learningCount() {
        return reviewItemRepository.countByUserIdAndStatus(USER_ID, "LEARNING");
    }
}