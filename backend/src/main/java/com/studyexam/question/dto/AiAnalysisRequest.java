package com.studyexam.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 预览 AI 生成的解析(不写库)。供题目编辑页使用:新建的题还没有 ID,
 * 先按当前表单里的题型 / 题干 / 要点生成,确认后随表单一起保存。
 */
@Schema(description = "AI 生成解析预览请求")
public record AiAnalysisRequest(
        @Schema(description = "题型,必须是主观题", example = "SHORT_ANSWER")
        @NotBlank(message = "题型不能为空") String type,
        @Schema(description = "题干", example = "简述实践与认识的辩证关系")
        @NotBlank(message = "题干不能为空")
        @Size(max = 2000, message = "题干不能超过 2000 字") String stem,
        @Schema(description = "参考答案要点", example = "[\"实践是认识的基础\", \"认识反作用于实践\"]")
        @NotEmpty(message = "参考答案要点不能为空") List<String> answerPoints
) {
}
