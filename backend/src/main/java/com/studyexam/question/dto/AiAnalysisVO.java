package com.studyexam.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 生成的主观题解析。 */
@Schema(description = "AI 生成的解析")
public record AiAnalysisVO(
        @Schema(description = "解析正文(简洁好记,可直接作为题目解析)", example = "考点:实践与认识……")
        String analysis
) {
}
