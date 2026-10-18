package com.studyexam.paper;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperRepository extends JpaRepository<Paper, Long> {

    List<Paper> findBySubjectIdOrderByYearDescIdDesc(Long subjectId);
}