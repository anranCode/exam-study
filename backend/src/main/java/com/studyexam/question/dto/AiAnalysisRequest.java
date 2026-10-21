package com.studyexam.question.dto;

import com.studyexam.question.OptionItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 生成答案/解析的请求。
 *
 * <p>编辑页新建的题还没有 ID,所以按当前表单内容生成:科目、题型、题干、选项、已有答案、
 * 参考答案要点都带上。科目用来取该科目的专属提示词;客观题已有答案时会让模型与之一致。
 */
@Schema(description = "AI 生成答案/解析请求")
public record AiAnalysisRequest(
        @Schema(description = "科目 ID", example = "1")
        @NotNull(message = "科目不能为空") Long subjectId,
        @Schema(description = "题型", example = "SHORT_ANSWER")
        @NotBlank(message = "题型不能为空") String type,
        @Schema(description = "题干", example = "简述实践与认识的辩证关系")
        @NotBlank(message = "题干不能为空")
        @Size(max = 2000, message = "题干不能超过 2000 字") String stem,
        @Schema(description = "客观题选项;主观题为空") List<OptionItem> options,
        @Schema(description = "客观题已有标准答案;为空时由 AI 判断") String answer,
        @Schema(description = "主观题参考答案要点;客观题为空") List<String> answerPoints
) {
}
