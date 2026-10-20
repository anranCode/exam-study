package com.studyexam.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    Optional<ReviewItem> findByUserIdAndQuestionId(Long userId, Long questionId);

    /**
     * 到期复习的题目 ID。
     *
     * <p>排序先看答错次数:错得最多的排最前面(错题本复习要优先攻克反复错的题),
     * 同错次再按到期望时间升序。题型筛选可选,传 typeFilter = true 时只取 types 里的题型
     * —— 错题本支持「按科目 + 题型」挑着复习。
     */
    @Query(value = """
            SELECT ri.question_id
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND ri.status = 'LEARNING'
              AND ri.next_review_at <= :now
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:typeFilter = FALSE OR q.type IN (:types))
              AND q.status = 'PUBLISHED'
            ORDER BY ri.wrong_count DESC, ri.next_review_at ASC, ri.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findDueQuestionIds(@Param("userId") Long userId,
                                  @Param("subjectId") Long subjectId,
                                  @Param("typeFilter") boolean typeFilter,
                                  @Param("types") List<String> types,
                                  @Param("now") LocalDateTime now,
                                  @Param("limit") int limit);

    @Query("""
            SELECT COUNT(ri) FROM ReviewItem ri
            WHERE ri.userId = :userId AND ri.status = 'LEARNING' AND ri.nextReviewAt <= :now
            """)
    long countDue(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 错题练习池:从还没掌握的错题里**随机**抽题,不看到期时间。
     *
     * <p>和 {@link #findDueQuestionIds} 的区别只在「到不到期」这一条:
     * 到期复习是每天该做的事(按排期走),错题练习是想练就练(随机抽)。
     * 只看 {@code status = 'LEARNING'}:已经连对三次出队的题不该再反复占用练习额度。
     *
     * <p>排序用 RAND() 而不是错题数:这里要的是「随机抽」,
     * 按错题数排会让每次练习都是那几道最难的题,反而练不到别的。
     * 题量在万级以内,RAND() 可接受(与 QuestionRepository 的抽题保持一致)。
     */
    @Query(value = """
            SELECT ri.question_id
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND ri.status = 'LEARNING'
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:typeFilter = FALSE OR q.type IN (:types))
              AND q.status = 'PUBLISHED'
            ORDER BY RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findRandomWrongQuestionIds(@Param("userId") Long userId,
                                          @Param("subjectId") Long subjectId,
                                          @Param("typeFilter") boolean typeFilter,
                                          @Param("types") List<String> types,
                                          @Param("limit") int limit);

    /**
     * 错题练习池的存量,给前端提示「这个范围下还能练几题」。
     * 条件与 {@link #findRandomWrongQuestionIds} 完全一致,否则算出来的数和实际抽到的对不上。
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND ri.status = 'LEARNING'
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:typeFilter = FALSE OR q.type IN (:types))
              AND q.status = 'PUBLISHED'
            """, nativeQuery = true)
    long countRandomWrong(@Param("userId") Long userId,
                          @Param("subjectId") Long subjectId,
                          @Param("typeFilter") boolean typeFilter,
                          @Param("types") List<String> types);

    /**
     * 错题练习池按题型分布,给「错题练习」的题型下拉用。
     *
     * <p>与 {@link #countGroupedByType} 的区别:这里只数**能练**的题
     * ({@code q.status = 'PUBLISHED'}),并且固定只数 LEARNING。
     * 下拉里列出的每种题型必须真能抽到题,否则用户选了却是空场次。
     */
    @Query(value = """
            SELECT q.type AS type, COUNT(*) AS cnt
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND ri.status = 'LEARNING'
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND q.status = 'PUBLISHED'
            GROUP BY q.type
            ORDER BY COUNT(*) DESC, q.type ASC
            """, nativeQuery = true)
    List<Object[]> countWrongPoolGroupedByType(@Param("userId") Long userId,
                                              @Param("subjectId") Long subjectId);

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
     * <p>排序:{@code wrong_count DESC, next_review_at ASC, id ASC} ——
     * 错误次数多的排最前面(反复错的题才最该先练),同错次再按到期先后;最后按 id 兜底,
     * 否则分页时同一批数据的顺序可能抖动。
     */
    @Query(value = """
            SELECT ri.id
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:type IS NULL OR q.type = :type)
              AND (:status IS NULL OR ri.status = :status)
              AND (:dueOnly = FALSE OR ri.next_review_at <= :now)
            ORDER BY ri.wrong_count DESC, ri.next_review_at ASC, ri.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Long> findIdsForList(@Param("userId") Long userId,
                              @Param("subjectId") Long subjectId,
                              @Param("type") String type,
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
              AND (:type IS NULL OR q.type = :type)
              AND (:status IS NULL OR ri.status = :status)
              AND (:dueOnly = FALSE OR ri.next_review_at <= :now)
            """, nativeQuery = true)
    long countForList(@Param("userId") Long userId,
                      @Param("subjectId") Long subjectId,
                      @Param("type") String type,
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

    /**
     * 错题本题型分布。返回行结构:[type, cnt],按题数降序。
     *
     * <p>只数指定队列状态的条目 —— 前端下拉要显示的是「还能练哪些题型」,
     * 已掌握的不该占位。
     */
    @Query(value = """
            SELECT q.type AS type, COUNT(*) AS cnt
            FROM review_item ri
            JOIN question q ON q.id = ri.question_id
            WHERE ri.user_id = :userId
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:status IS NULL OR ri.status = :status)
            GROUP BY q.type
            ORDER BY COUNT(*) DESC, q.type ASC
            """, nativeQuery = true)
    List<Object[]> countGroupedByType(@Param("userId") Long userId,
                                      @Param("subjectId") Long subjectId,
                                      @Param("status") String status);
}
