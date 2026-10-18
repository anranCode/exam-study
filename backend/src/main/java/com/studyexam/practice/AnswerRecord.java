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

import java.time.LocalDateTime;

/**
 * 作答记录。同一场次同一题唯一(唯一索引),提交接口的幂等基础。
 */
@Entity
@Table(name = "answer_record")
@Getter
@Setter
@NoArgsConstructor
public class AnswerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long questionId;

    /** 客观题所选项(如 "B"),主观题存自述。 */
    @Column(length = 255)
    private String userAnswer;

    /** SURE 确定 / UNSURE 没把握 / WRONG 答错。答错的题一律记 WRONG,即使用户点的是「确认」。 */
    @Column(nullable = false, length = 16)
    private String confidence = "SURE";

    /** 客观题是否正确;主观题为空。 */
    @Column(columnDefinition = "TINYINT(1)")
    private Boolean isCorrect;

    /** 主观题自评:勾选到的要点数。 */
    private Integer selfScore;

    /** 主观题要点总数。 */
    private Integer totalPoints;

    @Column(nullable = false)
    private Integer spentSeconds = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime answeredAt;
}