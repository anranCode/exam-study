package com.studyexam.question.dto;

import com.studyexam.question.OptionItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目详情(含答案与解析)。用于题库管理与编辑页。
 *
 * <p>刷题接口**不得**使用本类型——练习中取题只返回 {@code PracticeQuestionVO}。
 */
@Schema(description = "题目详情")
public record QuestionDetailVO(
        @Schema(description = "题目 ID") Long id,
        @Schema(description = "科目 ID") Long subjectId,
        @Schema(description = "科目简称") String subjectShortName,
        @Schema(description = "科目颜色") String subjectColor,
        @Schema(description = "章节 ID") Long chapterId,
        @Schema(description = "章节名") String chapterName,
        @Schema(description = "题型") String type,
        @Schema(description = "题型中文名") String typeLabel,
        @Schema(description = "题干") String stem,
        @Schema(description = "选项") List<OptionItem> options,
        @Schema(description = "客观题正确答案") String answer,
        @Schema(description = "主观题参考答案要点") List<String> answerPoints,
        @Schema(description = "解析") String analysis,
        @Schema(description = "难度 1/2/3") Integer difficulty,
        @Schema(description = "来源") String source,
        @Schema(description = "真题年份") Integer sourceYear,
        @Schema(description = "分值") BigDecimal score,
        @Schema(description = "状态") String status,
        @Schema(description = "AI 精简解析(已缓存时)") String aiAnalysis,
        @Schema(description = "是否已生成过 AI 解析") boolean hasAiAnalysis,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {
}