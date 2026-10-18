package com.studyexam.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 提醒发送日志。唯一键 (user_id, log_date, type) 保证「当天同类提醒只发一条」。
 */
@Entity
@Table(name = "reminder_log")
@Getter
@Setter
@NoArgsConstructor
public class ReminderLog {

    public static final String TYPE_MORNING = "MORNING";
    public static final String TYPE_EVENING = "EVENING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate logDate;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private Boolean success = true;

    @Column(length = 255)
    private String failReason;
}
