package com.studyexam.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnswerRecordRepository extends JpaRepository<AnswerRecord, Long> {

    List<AnswerRecord> findBySessionIdOrderByIdAsc(Long sessionId);

    /** 提交接口的幂等基础:同一场次同一题唯一。 */
    Optional<AnswerRecord> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    long countBySessionId(Long sessionId);

    boolean existsByQuestionId(Long questionId);

    /**
     * 本场次里「真正在复习」的题数:作答的题在本次练习开始前就已经在复习队列里。
     * 写入 study_log.review_done。
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM answer_record ar
            JOIN review_item ri ON ri.question_id = ar.question_id
            WHERE ar.session_id = :sessionId
              AND ri.created_at <= :sessionStartedAt
            """, nativeQuery = true)
    long countReviewDoneInSession(@Param("sessionId") Long sessionId,
                                  @Param("sessionStartedAt") LocalDateTime sessionStartedAt);
}