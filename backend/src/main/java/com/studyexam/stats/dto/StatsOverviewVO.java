package com.studyexam.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统计总览。对应 docs/study-exam/backend/03-api-spec.md 第六节 GET /stats/overview。
 *
 * <p>正确率**只统计客观题**({@code answer_record.is_correct IS NOT NULL});主观题不进分母,
 * 否则自评尺度会污染这个数字。
 */
@Schema(description = "统计总览")
public record StatsOverviewVO(
        @Schema(description = "题库总题量", example = "320") long totalQuestions,
        @Schema(description = "已掌握题数", example = "205") long masteredCount,
        @Schema(description = "平均正确率(0—1,无数据时为 null)", example = "0.73") Double avgAccuracy,
        @Schema(description = "连续打卡天数", example = "6") int streakDays,
        @Schema(description = "今日作答题数", example = "12") long todayAnswered,
        @Schema(description = "今日答对题数", example = "9") long todayCorrect,
        @Schema(description = "累计答对题数(只含客观题)", example = "320") long totalCorrect,
        @Schema(description = "累计作答的客观题次数", example = "480") long totalAnswered
) {
}