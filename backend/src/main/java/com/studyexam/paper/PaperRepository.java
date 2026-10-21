package com.studyexam.paper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaperRepository extends JpaRepository<Paper, Long> {

    List<Paper> findBySubjectIdOrderByYearDescIdDesc(Long subjectId);

    /**
     * 该科目最近一份「像一份完整卷子」的真题试卷。
     *
     * <p>模拟考试的组卷模板从这里读:年份有值的优先(2026 的卷子比没写年份的新),
     * 年份相同或都为空时取 id 最大的 —— 即最近一次导入的那份。
     * 只认题量 ≥ 10 的试卷:有的条目是「只导了几道题」的半成品,拿它当模板
     * 会把模拟考试组得只有两三道题。
     */
    @Query(value = """
            SELECT p.id
            FROM paper p
            WHERE p.subject_id = :subjectId
              AND p.type = 'REAL'
              AND (SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = p.id) >= 10
            ORDER BY (p.year IS NULL), p.year DESC, p.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Long> findLatestPaperIdWithQuestions(@Param("subjectId") Long subjectId);
}