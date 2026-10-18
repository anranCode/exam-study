package com.studyexam.importer;

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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导入记录。一次导入(一份 PDF / 一份 JSON)一行。
 *
 * <p>存下来的目的不是统计,而是「回头补录」:哪些题没进去、题干是什么、为什么被拒。
 * 识别阶段的提醒也一起存 —— 整页没认出来时,题目根本不会出现在导入结果里,
 * 只看 {@code issues} 是发现不了的。
 */
@Entity
@Table(name = "import_log")
@Getter
@Setter
@NoArgsConstructor
public class ImportLog {

    /** 全部条目都入库了。 */
    public static final String STATUS_IMPORTED = "IMPORTED";
    /** 有一部分没进去(校验失败或重复)。 */
    public static final String STATUS_PARTIAL = "PARTIAL";
    /** 一条都没入库,而且原因全是「题库里已有」。 */
    public static final String STATUS_DUPLICATED = "DUPLICATED";
    /** 一条都没入库。 */
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PDF 识别 / JSON 导入。 */
    @Column(nullable = false, length = 16)
    private String source;

    @Column(nullable = false, length = 32)
    private String subjectCode = "";

    @Column(length = 191)
    private String paperName;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false)
    private Integer total = 0;

    @Column(nullable = false)
    private Integer imported = 0;

    @Column(nullable = false)
    private Integer skipped = 0;

    @Column(nullable = false)
    private Integer duplicated = 0;

    /** 没入库的条目;重复的只计数不列明细(题库里本来就有,不需要补录)。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<ImportIssue> issues;

    /** 识别阶段的提醒,原样保留。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> warnings;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
