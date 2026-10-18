package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 未完成的场次。今日页用它显示「上次练习还剩 N 题未做完,继续?」。 */
@Schema(description = "未完成的场次")
public record OngoingSessionVO(
        @Schema(description = "场次 ID") Long sessionId,
        @Schema(description = "组题模式") String mode,
        @Schema(description = "已作答数") int answeredCount,
        @Schema(description = "本轮总题数") int totalCount,
        @Schema(description = "开始时间") LocalDateTime startedAt
) {
}
