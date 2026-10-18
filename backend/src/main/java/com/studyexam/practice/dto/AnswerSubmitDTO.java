package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 提交单题作答。 */
@Schema(description = "提交作答请求")
public record AnswerSubmitDTO(
        @Schema(description = "题目 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long questionId,
        @Schema(description = "客观题所选项,如 B;主观题存自述文本", example = "B") String userAnswer,
        @Schema(description = "信心度:SURE 确定 / UNSURE 没把握(点了先看答案);答错由后端改写为 WRONG",
                example = "SURE") String confidence,
        @Schema(description = "本题用时(秒)", example = "23") Integer spentSeconds
) {
}
