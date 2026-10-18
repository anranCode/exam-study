package com.studyexam.ai;

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

import java.time.LocalDateTime;

/**
 * AI 调用日志。每次真实调用(无论成败)写一行,用于核算 TokenPlan 用量。
 */
@Entity
@Table(name = "ai_call_log")
@Getter
@Setter
@NoArgsConstructor
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId = 1L;

    /** EXPLAIN 解析 / VARIANT 变式题 / MNEMONIC 口诀 / DIGEST 串讲 / PARSE_PDF 试卷识别 */
    @Column(nullable = false, length = 32)
    private String scene;

    private Long questionId;

    private Long subjectId;

    @Column(nullable = false, length = 32)
    private String model;

    @Column(nullable = false)
    private Integer tokensIn = 0;

    @Column(nullable = false)
    private Integer tokensOut = 0;

    @Column(nullable = false)
    private Integer durationMs = 0;

    @Column(nullable = false)
    private Boolean success = true;

    @Column(length = 255)
    private String failReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}