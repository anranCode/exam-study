package com.studyexam.question;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目——核心表。
 *
 * <p>{@code options} 与 {@code answerPoints} 存 MySQL JSON 列。不同题型的选项结构差异大
 * (单选 4 项、多选 5 项、判断 2 项、主观题无选项),拆独立选项表会让查询和录入都变复杂,
 * 因此按 docs/design/tech-stack.md 的决定用 JSON 类型。
 */
@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long subjectId;

    /** 可为空,导入时未归章。 */
    private Long chapterId;

    /** 题型,存枚举名字符串,如 SINGLE_CHOICE。 */
    @Column(nullable = false, length = 32)
    private String type;

    /** 题干(纯文本,公式用 $...$)。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    /** 客观题选项数组。主观题为空。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<OptionItem> options;

    /** 客观题正确答案,如 "A" / "ABD" / "T"。主观题为空。 */
    @Column(length = 255)
    private String answer;

    /** 主观题参考答案要点。客观题为空。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_points", columnDefinition = "json")
    private List<String> answerPoints;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    /** 1 简单 / 2 中等 / 3 困难。DDL 里是 TINYINT(1 字节),故实体映射为 Byte,否则 validate 会报类型不符。 */
    @Column(nullable = false)
    private Byte difficulty = 2;

    /** 来源,如「2024年10月真题」。 */
    @Column(length = 64)
    private String source;

    /** 真题年份,便于筛选。 */
    private Integer sourceYear;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal score = new BigDecimal("2.0");

    /** DRAFT 待校对 / PUBLISHED 已入库。 */
    @Column(nullable = false, length = 16)
    private String status = "PUBLISHED";

    /** AI 老师精简解析,按需生成后缓存。 */
    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;

    @Column(name = "ai_analysis_at")
    private LocalDateTime aiAnalysisAt;

    /** 由 Hibernate 在插入/更新时写入,不依赖数据库默认值——否则 insert 后实体里读不到时间。 */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}