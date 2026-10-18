package com.studyexam.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 趋势图上的一个点。对应 GET /stats/trend。
 *
 * <p>没有数据的日期也会返回(数量为 0),前端不用自己补点,折线不会断。
 */
@Schema(description = "单日趋势")
public record TrendPointVO(
        @Schema(description = "日期", example = "2026-10-18") LocalDate date,
        @Schema(description = "作答的客观题数", example = "12") long answered,
        @Schema(description = "答对数", example = "9") long correct,
        @Schema(description = "当日正确率(0—1,无作答时为 null)", example = "0.75") Double accuracy
) {
}
