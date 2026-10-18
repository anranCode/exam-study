package com.studyexam.question;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 题目列表的筛选条件。参数名与 docs/study-exam/backend/03-api-spec.md 第三节一致。
 */
@Schema(description = "题目查询条件")
public record QuestionQuery(
        @Schema(description = "科目 ID") Long subjectId,
        @Schema(description = "章节 ID") Long chapterId,
        @Schema(description = "题型") String type,
        @Schema(description = "题干关键字") String keyword,
        @Schema(description = "真题年份") Integer sourceYear,
        @Schema(description = "状态:DRAFT / PUBLISHED") String status
) {
}