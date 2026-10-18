package com.studyexam.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 试卷与题目的关联。{@code sectionName} 保留原始大题名(如「一、单项选择题」),
 * {@code sortOrder} 保留题目在原卷中的位置,回看试卷时能还原原始结构。
 */
@Entity
@Table(name = "paper_question")
@Getter
@Setter
@NoArgsConstructor
public class PaperQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paperId;

    @Column(nullable = false)
    private Long questionId;

    @Column(name = "section_name", length = 64)
    private String sectionName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}