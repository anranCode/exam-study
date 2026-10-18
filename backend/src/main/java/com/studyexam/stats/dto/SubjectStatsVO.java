package com.studyexam.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单个科目的正确率与掌握度。对应 GET /stats/subjects。
 *
 * <p>正确率**只算客观题**(主观题不判分,进分母会污染口径);掌握度 = 已掌握题数 / 该科目总题量。
 */
@Schema(description = "科目统计")
public record SubjectStatsVO(
        @Schema(description = "科目 ID", example = "1") Long subjectId,
        @Schema(description = "课程代码", example = "03709") String code,
        @Schema(description = "科目名称", example = "马克思主义基本原理概论") String name,
        @Schema(description = "科目简称", example = "马原") String shortName,
        @Schema(description = "科目颜色", example = "#E5484D") String color,
        @Schema(description = "题库总题量", example = "48") long questionCount,
        @Schema(description = "已作答的客观题次数", example = "120") long answeredCount,
        @Schema(description = "其中答对的次数", example = "88") long correctCount,
        @Schema(description = "正确率(0—1,无作答时为 null)", example = "0.73") Double accuracy,
        @Schema(description = "仍在复习队列里的题数", example = "32") long reviewCount,
        @Schema(description = "已掌握的题数", example = "12") long masteredCount,
        @Schema(description = "掌握度(已掌握 / 总题量)", example = "0.25") Double masteryRate
) {
}
