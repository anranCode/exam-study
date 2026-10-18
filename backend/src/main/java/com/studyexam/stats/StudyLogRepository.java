package com.studyexam.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {

    Optional<StudyLog> findByUserIdAndLogDate(Long userId, LocalDate logDate);

    boolean existsByUserIdAndLogDate(Long userId, LocalDate logDate);

    /**
     * 某天是否真做过题。晚间提醒据此判断「今天还没打卡」——
     * 用 question_count > 0 而不是「有记录」,与打卡口径一致(删练习留下的全零行不算)。
     */
    @Query(value = """
            SELECT COUNT(*) FROM study_log
            WHERE user_id = :userId AND log_date = :date AND question_count > 0
            """, nativeQuery = true)
    long countStudiedOn(@Param("userId") Long userId, @Param("date") LocalDate date);
}
