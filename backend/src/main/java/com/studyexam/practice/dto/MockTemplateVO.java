package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟考试的组卷模板:从该科目最近一份有内容的真题试卷里读出来的题型结构。
 *
 * <p>{@code available} 是该题型在当前题库里**已发布**的题数 —— 待校对的题不进练习池,
 * 前端据此限制可选题量,免得用户填了 25 道却只抽到 3 道。
 */
@Schema(description = "模拟考试模板")
public record MockTemplateVO(
        @Schema(description = "科目 ID") Long subjectId,
        @Schema(description = "科目简称") String subjectShortName,
        @Schema(description = "科目全称") String subjectName,
        @Schema(description = "参考的试卷 ID;空表示该科目还没有可参考的真题") Long paperId,
        @Schema(description = "参考的试卷名") String paperName,
        @Schema(description = "参考试卷的年份") Integer year,
        @Schema(description = "试卷满分", example = "100") Integer totalScore,
        @Schema(description = "考试时长(分钟)", example = "150") Integer durationMin,
        @Schema(description = "题型结构") List<Section> sections
) {

    @Schema(description = "题型段落")
    public record Section(
            @Schema(description = "题型", example = "SINGLE_CHOICE") String type,
            @Schema(description = "题型中文名", example = "单项选择题") String typeLabel,
            @Schema(description = "真题里的题数", example = "25") int count,
            @Schema(description = "每题分值", example = "2.0") BigDecimal score,
            @Schema(description = "题库里已发布的可用题数", example = "228") int available
    ) {
    }
}
