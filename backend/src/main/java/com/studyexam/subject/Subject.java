package com.studyexam.subject;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 科目。字段名与 docs/study-exam/backend/02-data-model.md 的 subject 表一一对应,
 * 不允许自行改名。驼峰 → 下划线的转换由 Spring Boot 默认命名策略完成。
 */
@Entity
@Table(name = "subject")
@Getter
@Setter
@NoArgsConstructor
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 课程代码,如 03709 / 13000。唯一。 */
    @Column(nullable = false, length = 16, unique = true)
    private String code;

    /** 科目全称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 简称,界面显示用,如「马原」。 */
    @Column(nullable = false, length = 32)
    private String shortName;

    /** 标识色,如 #E5484D。 */
    @Column(nullable = false, length = 16)
    private String color = "#2F6FED";

    /** 考试日期。 */
    private LocalDate examDate;

    /** 考试场次:D1_AM / D1_PM / D2_AM / D2_PM。 */
    @Column(length = 16)
    private String examSlot;

    @Column(nullable = false)
    private Integer totalScore = 100;

    @Column(nullable = false)
    private Integer passScore = 60;

    /** 列表排序,越小越前。 */
    @Column(nullable = false)
    private Integer sortOrder = 0;

    /** 是否参与每日任务。 */
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 255)
    private String remark;

    /** 由 Hibernate 在插入/更新时写入,不依赖数据库默认值——否则 insert 后实体里读不到时间。 */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}