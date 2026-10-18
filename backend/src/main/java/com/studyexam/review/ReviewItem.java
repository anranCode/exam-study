package com.studyexam.review;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 复习队列(错题本 + 遗忘曲线排期)。每题一行,由 {@link Sm2Scheduler} 按作答信心度推进。
 */
@Entity
@Table(name = "review_item")
@Getter
@Setter
@NoArgsConstructor
public class ReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId = 1L;

    @Column(nullable = false)
    private Long questionId;

    /** SM-2 复习阶段,0 表示新错题。 */
    @Column(nullable = false)
    private Integer stage = 0;

    @Column(nullable = false)
    private Integer intervalDays = 1;

    /** 难度系数,上下限 1.30—3.00。 */
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor = new BigDecimal("2.50");

    /** 累计答错次数。 */
    @Column(nullable = false)
    private Integer wrongCount = 0;

    /** 连续答对次数。 */
    @Column(nullable = false)
    private Integer correctStreak = 0;

    private LocalDateTime lastReviewAt;

    /** 下次该复习的时间,查询核心字段。 */
    @Column(nullable = false)
    private LocalDateTime nextReviewAt;

    /** LEARNING 在队列 / MASTERED 已掌握。 */
    @Column(nullable = false, length = 16)
    private String status = "LEARNING";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}