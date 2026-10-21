package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** 创建场次后的响应。 */
@Schema(description = "练习场次")
public record PracticeSessionVO(
        @Schema(description = "场次 ID", example = "88") Long sessionId,
        @Schema(description = "组题模式", example = "DAILY") String mode,
        @Schema(description = "本轮题数", example = "10") int totalCount,
        @Schema(description = "本轮满分(各题分值之和)") BigDecimal totalScore,
        @Schema(description = "创建时已存在且被置为放弃的场次 ID,供前端提示继续上次;没有则为空")
        Long ongoingSessionId,
        @Schema(description = "题目列表(不含答案)") List<PracticeQuestionVO> questions,
        @Schema(description = "模拟考试限时(分钟);普通练习为空") Integer durationMin
) {
}
