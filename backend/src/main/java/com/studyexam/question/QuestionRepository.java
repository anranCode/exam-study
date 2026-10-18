package com.studyexam.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long>, QuestionRepositoryCustom {

    long countBySubjectId(Long subjectId);

    long countByChapterId(Long chapterId);

    /**
     * 导入去重用:取某科目下所有题目的 id 与题干,交给调用方建「归一化题干」索引。
     *
     * <p>只取这两列而不是整个实体,是为了别把解析、选项这些大字段也拉进内存。
     * 单用户场景下一个科目最多几千条,一次性加载足够;真到量级了再加归一化列和索引。
     */
    @Query("SELECT q.id, q.stem FROM Question q WHERE q.subjectId = :subjectId")
    List<Object[]> findIdAndStemBySubjectId(@Param("subjectId") Long subjectId);

    /**
     * 某科目下各题型「可练习(已发布)」的题数,给「自由练习」的题型下拉用。
     *
     * <p>只数 PUBLISHED:待校对的题抽题池里没有,列出来只会让用户选了却抽不到。
     * 返回行结构:[type, cnt]。
     */
    @Query(value = """
            SELECT q.type, COUNT(*)
            FROM question q
            WHERE q.status = 'PUBLISHED'
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
            GROUP BY q.type
            ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<Object[]> countPublishedGroupedByType(@Param("subjectId") Long subjectId);

    /**
     * 一次查出某科目下每个章节的题目数,避免在 Java 里循环查导致的 N+1。
     * 返回行结构:[chapterId, cnt]。
     */
    @Query(value = """
            SELECT q.chapter_id AS chapterId, COUNT(*) AS cnt
            FROM question q
            WHERE q.subject_id = :subjectId AND q.chapter_id IS NOT NULL
            GROUP BY q.chapter_id
            """, nativeQuery = true)
    List<ChapterQuestionCount> countQuestionsGroupedByChapter(@Param("subjectId") Long subjectId);

    interface ChapterQuestionCount {
        Long getChapterId();

        long getCnt();
    }

    // ── 组题用的抽取查询(SessionGenerator)────────────────────
    // 题量在万级以内,ORDER BY RAND() 性能可接受,不做优化(见 01-architecture.md 第三节)。
    // excludeIds 传空集合时请传哨兵值 List.of(-1L),避免 NOT IN () 语法错误。

    @Query(value = """
            SELECT * FROM question q
            WHERE q.status = 'PUBLISHED'
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:chapterId IS NULL OR q.chapter_id = :chapterId)
              AND (:typeFilter = FALSE OR q.type IN (:types))
              AND q.id NOT IN (:excludeIds)
            ORDER BY RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Question> pickRandom(@Param("subjectId") Long subjectId,
                              @Param("chapterId") Long chapterId,
                              @Param("typeFilter") boolean typeFilter,
                              @Param("types") List<String> types,
                              @Param("excludeIds") List<Long> excludeIds,
                              @Param("limit") int limit);

    /** 完全没做过的新题,用于补齐每日任务的差额。 */
    @Query(value = """
            SELECT * FROM question q
            WHERE q.status = 'PUBLISHED'
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:chapterId IS NULL OR q.chapter_id = :chapterId)
              AND q.id NOT IN (:excludeIds)
              AND NOT EXISTS (SELECT 1 FROM answer_record ar WHERE ar.question_id = q.id)
              AND NOT EXISTS (SELECT 1 FROM review_item ri WHERE ri.question_id = q.id)
            ORDER BY RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Question> pickNew(@Param("subjectId") Long subjectId,
                           @Param("chapterId") Long chapterId,
                           @Param("excludeIds") List<Long> excludeIds,
                           @Param("limit") int limit);

    /** 题库存量,用于「题库为空」的友好提示与 DAILY 配额的兜底。 */
    @Query(value = """
            SELECT COUNT(*) FROM question q
            WHERE q.status = 'PUBLISHED'
              AND (:subjectId IS NULL OR q.subject_id = :subjectId)
              AND (:chapterId IS NULL OR q.chapter_id = :chapterId)
            """, nativeQuery = true)
    long countAvailable(@Param("subjectId") Long subjectId, @Param("chapterId") Long chapterId);
}