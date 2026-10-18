package com.studyexam.subject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Subject> findAllByOrderBySortOrderAscIdAsc();

    /** 单科目统计:题目数 / 已掌握数。到期复习数在 ReviewItemRepository 里查(该表属阶段 5)。 */
    @Query(value = """
            SELECT COUNT(*)                                  AS questionCount,
                   COALESCE(SUM(q.status = 'PUBLISHED'), 0)  AS publishedCount
            FROM question q
            WHERE q.subject_id = :subjectId
            """, nativeQuery = true)
    QuestionStats countQuestionStats(@Param("subjectId") Long subjectId);

    /**
     * 一次查出所有科目的题目数,避免在 Java 里循环查导致的 N+1。
     * 返回行结构:[subjectId, questionCount]。
     */
    @Query(value = """
            SELECT q.subject_id AS subjectId, COUNT(*) AS cnt
            FROM question q
            GROUP BY q.subject_id
            """, nativeQuery = true)
    List<SubjectQuestionCount> countQuestionsGroupedBySubject();

    /** 投影接口:Spring Data 按别名映射。 */
    interface QuestionStats {
        long getQuestionCount();

        long getPublishedCount();
    }

    interface SubjectQuestionCount {
        Long getSubjectId();

        long getCnt();
    }
}