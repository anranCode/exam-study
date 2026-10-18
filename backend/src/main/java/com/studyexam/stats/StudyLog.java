package com.studyexam.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习日志(每日汇总)。打卡与统计的数据源,每用户每天一行。
 */
@Entity
@Table(name = "study_log")
@Getter
@Setter
@NoArgsConstructor
public class StudyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId = 1L;

    /** 统计日期。 */
    @Column(nullable = false)
    private LocalDate logDate;

    /** 当日练习场次数。 */
    @Column(nullable = false)
    private Integer sessionCount = 0;

    /** 当日作答题数。 */
    @Column(nullable = false)
    private Integer questionCount = 0;

    /** 当日答对题数(仅客观题)。 */
    @Column(nullable = false)
    private Integer correctCount = 0;

    /** 当日学习时长(秒)。 */
    @Column(nullable = false)
    private Integer studySeconds = 0;

    /** 当日完成的复习题数。 */
    @Column(nullable = false)
    private Integer reviewDone = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}