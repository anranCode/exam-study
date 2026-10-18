package com.studyexam.chapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findBySubjectIdOrderBySortOrderAscIdAsc(Long subjectId);

    List<Chapter> findBySubjectIdAndName(Long subjectId, String name);

    boolean existsByParentId(Long parentId);

    /** 该章节及子章节下的题目总数——删除章节前的守卫条件。 */
    @Query(value = """
            SELECT COUNT(*)
            FROM question q
            WHERE q.chapter_id = :chapterId
               OR q.chapter_id IN (SELECT c.id FROM chapter c WHERE c.parent_id = :chapterId)
            """, nativeQuery = true)
    long countQuestionsUnderChapter(@Param("chapterId") Long chapterId);
}