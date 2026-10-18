package com.studyexam.practice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    List<PracticeSession> findByUserIdAndStatus(Long userId, String status);

    /** 最近一次未完成的场次(用于「继续上次练习」)。 */
    Optional<PracticeSession> findFirstByUserIdAndStatusOrderByStartedAtDesc(Long userId, String status);

    /** 最近一次已完成的场次(今日页「最近一次练习」)。 */
    Optional<PracticeSession> findFirstByUserIdAndStatusOrderByFinishedAtDesc(Long userId, String status);

    Page<PracticeSession> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndStatus(Long userId, String status);
}