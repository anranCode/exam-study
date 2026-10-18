package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "练习记录详情")
public record PracticeSessionDetailVO(
        @Schema(description = "场次 ID") Long id,
        @Schema(description = "组题模式") String mode,
        @Schema(description = "状态") String status,
        @Schema(description = "科目简称") String subjectShortName,
        @Schema(description = "本轮题数") int totalCount,
        @Schema(description = "已作答数") int answeredCount,
        @Schema(description = "答对数") int correctCount,
        @Schema(description = "得分") BigDecimal score,
        @Schema(description = "满分") BigDecimal totalScore,
        @Schema(description = "正确率") Double accuracy,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间") LocalDateTime finishedAt,
        @Schema(description = "用时(秒)") Integer durationSeconds,
        @Schema(description = "逐题作答") List<SessionAnswerVO> answers
) {
}
