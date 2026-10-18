package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "练习记录列表项")
public record PracticeSessionListItemVO(
        @Schema(description = "场次 ID") Long id,
        @Schema(description = "组题模式") String mode,
        @Schema(description = "科目 ID") Long subjectId,
        @Schema(description = "科目简称") String subjectShortName,
        @Schema(description = "状态 ONGOING / FINISHED / ABANDONED") String status,
        @Schema(description = "本轮题数") int totalCount,
        @Schema(description = "已作答数") int answeredCount,
        @Schema(description = "答对数(仅客观题)") int correctCount,
        @Schema(description = "得分") BigDecimal score,
        @Schema(description = "满分") BigDecimal totalScore,
        @Schema(description = "正确率 0—1") Double accuracy,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间") LocalDateTime finishedAt,
        @Schema(description = "用时(秒)") Integer durationSeconds
) {
}
