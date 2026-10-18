package com.studyexam.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日到期复习汇总。今日页首屏靠它——见 docs/study-exam/backend/03-api-spec.md 第六节。
 */
@Schema(description = "到期复习汇总")
public record DueSummaryVO(
        @Schema(description = "统计日期", example = "2026-10-15") LocalDate date,
        @Schema(description = "到期复习总题数", example = "12") long totalDue,
        @Schema(description = "按科目拆分") List<SubjectDue> bySubject,
        @Schema(description = "今日新题配额(取用户设置的每日题量)", example = "10") int newQuestionQuota,
        @Schema(description = "今天是否已打卡") boolean checkedInToday
) {

    @Schema(description = "单个科目的到期数量")
    public record SubjectDue(
            @Schema(description = "科目 ID", example = "1") Long subjectId,
            @Schema(description = "科目简称", example = "马原") String shortName,
            @Schema(description = "科目颜色", example = "#E5484D") String color,
            @Schema(description = "到期题数", example = "5") long dueCount
    ) {
    }
}