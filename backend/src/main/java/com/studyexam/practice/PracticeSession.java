package com.studyexam.practice;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 练习场次。一次刷题从创建到结束的记录。
 */
@Entity
@Table(name = "practice_session")
@Getter
@Setter
@NoArgsConstructor
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId = 1L;

    /** 为空表示跨科目。科目被删除时置空(SQL 里声明了 ON DELETE SET NULL)。 */
    private Long subjectId;

    /** MOCK 模式时有值。 */
    private Long paperId;

    /** 模拟考试的限时(分钟);普通练习为空。 */
    private Integer durationMin;

    /** 本场抽到的题目 ID(按出题顺序);用于交卷后按整卷统计。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_ids", columnDefinition = "json")
    private List<Long> questionIds;

    /** DAILY / RANDOM / CHAPTER / MOCK / REVIEW */
    @Column(nullable = false, length = 16)
    private String mode;

    /** ONGOING / FINISHED / ABANDONED */
    @Column(nullable = false, length = 16)
    private String status = "ONGOING";

    @Column(nullable = false)
    private Integer totalCount = 0;

    @Column(nullable = false)
    private Integer answeredCount = 0;

    /** 答对题数(仅客观题) */
    @Column(nullable = false)
    private Integer correctCount = 0;

    /** 本轮得分 = 客观题得分 + 主观题自评得分 */
    @Column(precision = 5, scale = 1)
    private BigDecimal score;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 实际用时。 */
    private Integer durationSeconds;
}