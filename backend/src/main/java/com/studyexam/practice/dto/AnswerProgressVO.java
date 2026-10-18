package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "本轮答题进度")
public record AnswerProgressVO(
        @Schema(description = "已作答数", example = "4") int answered,
        @Schema(description = "已答对数(仅客观题)", example = "3") int correct,
        @Schema(description = "本轮总题数", example = "10") int total
) {
}
