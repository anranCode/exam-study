package com.studyexam.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 生成的答案/解析。
 *
 * <p>客观题的 {@code answer} 是选项字母 / 判断题 T、F、NG / 填空题词句(已有标准答案时原样返回);
 * 主观题的 {@code answer} 为空,完整答案在 {@code analysis} 的「【参考答案】」板块里。
 */
@Schema(description = "AI 生成的答案与解析")
public record AiAnalysisVO(
        @Schema(description = "客观题答案;主观题为空", example = "B") String answer,
        @Schema(description = "解析正文;主观题包含【参考答案】/【译文】/【解析】板块",
                example = "【参考答案】……\n【解析】……") String analysis
) {
}
