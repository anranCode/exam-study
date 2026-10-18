package com.studyexam.notify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    boolean existsByUserIdAndLogDateAndType(Long userId, LocalDate logDate, String type);

    List<ReminderLog> findByUserIdAndLogDateGreaterThanEqualOrderBySentAtDesc(Long userId, LocalDate from);
}
