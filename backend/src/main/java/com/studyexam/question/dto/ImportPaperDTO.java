package com.studyexam.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 导入文件里的试卷信息。带了它才会建试卷与 paper_question 关联。
 */
@Schema(description = "导入的试卷信息")
public record ImportPaperDTO(
        @Schema(description = "试卷名", example = "2024年10月真题") String name,
        @Schema(description = "REAL 真题 / MOCK 模拟,缺省 REAL", example = "REAL") String type,
        @Schema(description = "年份", example = "2024") Integer year,
        @Schema(description = "总分,缺省 100", example = "100") Integer totalScore,
        @Schema(description = "考试时长(分钟),缺省 150", example = "150") Integer durationMin,
        @Schema(description = "备注") String remark
) {
}