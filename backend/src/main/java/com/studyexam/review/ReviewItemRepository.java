package com.studyexam.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    Optional<ReviewItem> findByUserIdAndQuestionId(Long userId, Long questionId);

    /** 到期复习的题目 ID(按到期望时间升序,最该复习的排前面)。 */
    @Query(value = """
            SELECT ri.question_id
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND ri.status = 'LEARNING'
              AND ri.next_review_at <= :now
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND q.status = 'PUBLISHED'
            ORDER BY ri.next_review_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findDueQuestionIds(@Param("userId") Long userId,
                                  @Param("subjectId") Long subjectId,
                                  @Param("now") LocalDateTime now,
                                  @Param("limit") int limit);

    @Query("""
            SELECT COUNT(ri) FROM ReviewItem ri
            WHERE ri.userId = :userId AND ri.status = 'LEARNING' AND ri.nextReviewAt <= :now
            """)
    long countDue(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT COUNT(*)
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND ri.status = 'LEARNING'
              AND ri.next_review_at <= :now
              AND q.subject_id = :subjectId
            """, nativeQuery = true)
    long countDueBySubject(@Param("userId") Long userId,
                           @Param("subjectId") Long subjectId,
                           @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT COUNT(*)
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId AND ri.status = 'MASTERED' AND q.subject_id = :subjectId
            """, nativeQuery = true)
    long countMasteredBySubject(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

    long countByUserIdAndStatus(Long userId, String status);

    /**
     * 错题本列表的 id 分页。三个筛选都可选:传 null / 0 表示不过滤。
     *
     * <p>为什么先只查 id、再由 Service 装配:本次要带出科目名、章节名、题型中文名,
     * 而 {@code ReviewItem} 对 {@code question} 只有 id、没有 JPA 关联,写不出跨表 JPQL;
     * 硬拼一条大原生投影又会让字段名和 DTO 各写一遍。先取 id 再按 id 批量装配,
     * 与 {@code PracticeService} 组装练习题的写法一致。
     *
     * <p>排序按 {@code next_review_at} 升序 —— 最该复习的排最前面;同时间按 id 兜底,
     * 否则分页时同一批数据的顺序可能抖动。
     */
    @Query(value = """
            SELECT ri.id
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:status IS NULL OR ri.status = :status)
              AND (:dueOnly = FALSE OR ri.next_review_at <= :now)
            ORDER BY ri.next_review_at ASC, ri.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Long> findIdsForList(@Param("userId") Long userId,
                              @Param("subjectId") Long subjectId,
                              @Param("status") String status,
                              @Param("dueOnly") boolean dueOnly,
                              @Param("now") LocalDateTime now,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    /** 与 {@link #findIdsForList} 同一套条件,只取总数,供分页用。 */
    @Query(value = """
            SELECT COUNT(*)
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:status IS NULL OR ri.status = :status)
              AND (:dueOnly = FALSE OR ri.next_review_at <= :now)
            """, nativeQuery = true)
    long countForList(@Param("userId") Long userId,
                      @Param("subjectId") Long subjectId,
                      @Param("status") String status,
                      @Param("dueOnly") boolean dueOnly,
                      @Param("now") LocalDateTime now);

    /** 本轮新进队列的题数(创建时间落在本次练习窗口内)。 */
    @Query("""
            SELECT COUNT(ri) FROM ReviewItem ri
            WHERE ri.userId = :userId AND ri.lastReviewAt >= :from AND ri.createdAt >= :from
            """)
    long countCreatedSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    /** 本轮更新的已有条目数。 */
    @Query("""
            SELECT COUNT(ri) FROM ReviewItem ri
            WHERE ri.userId = :userId AND ri.lastReviewAt >= :from AND ri.createdAt < :from
            """)
    long countUpdatedSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}