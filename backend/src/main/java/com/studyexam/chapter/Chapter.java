package com.studyexam.chapter;

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
 * 章节。支持两级:顶级章节 parent_id 为空。
 */
@Entity
@Table(name = "chapter")
@Getter
@Setter
@NoArgsConstructor
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long subjectId;

    /** 父章节,支持两级;顶级为空。 */
    private Long parentId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    /** 由 Hibernate 在插入/更新时写入,不依赖数据库默认值——否则 insert 后实体里读不到时间。 */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}