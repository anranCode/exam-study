package com.studyexam.paper;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperQuestionRepository extends JpaRepository<PaperQuestion, Long> {

    List<PaperQuestion> findByPaperIdOrderBySortOrderAscIdAsc(Long paperId);

    void deleteByPaperId(Long paperId);
}