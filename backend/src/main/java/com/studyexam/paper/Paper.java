package com.studyexam.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 试卷。一套完整真题或模拟卷,与题目通过 paper_question 关联。
 */
@Entity
@Table(name = "paper")
@Getter
@Setter
@NoArgsConstructor
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long subjectId;

    /** 如「2024年10月真题」。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** REAL 真题 / MOCK 模拟。 */
    @Column(nullable = false, length = 16)
    private String type = "REAL";

    private Integer year;

    @Column(nullable = false)
    private Integer totalScore = 100;

    /** 考试时长(分钟)。 */
    @Column(nullable = false)
    private Integer durationMin = 150;

    @Column(length = 255)
    private String remark;

    /** 由 Hibernate 在插入/更新时写入,不依赖数据库默认值——否则 insert 后实体里读不到时间。 */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}