package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 主观题自评提交。selfScore 为勾选到的要点数。 */
@Schema(description = "主观题自评")
public record SelfScoreDTO(
        @Schema(description = "勾选到的要点数", example = "3", requiredMode = Schema.RequiredMode.REQUIRED) Integer selfScore,
        @Schema(description = "要点总数", example = "5", requiredMode = Schema.RequiredMode.REQUIRED) Integer totalPoints
) {
}
