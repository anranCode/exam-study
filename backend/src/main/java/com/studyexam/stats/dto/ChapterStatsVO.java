package com.studyexam.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 章节掌握度,用于找薄弱章节。对应 GET /stats/chapters。
 *
 * <p>{@code chapterId} 为空表示「未归章」的题(导入时章节名没匹配上)。
 */
@Schema(description = "章节统计")
public record ChapterStatsVO(
        @Schema(description = "章节 ID,未归章为空") Long chapterId,
        @Schema(description = "章节名", example = "第一章 物质世界及其发展规律") String chapterName,
        @Schema(description = "该章节题数", example = "20") long questionCount,
        @Schema(description = "已作答的客观题次数", example = "35") long answeredCount,
        @Schema(description = "答对次数", example = "24") long correctCount,
        @Schema(description = "正确率(0—1,无作答时为 null)", example = "0.69") Double accuracy,
        @Schema(description = "已掌握题数", example = "5") long masteredCount,
        @Schema(description = "掌握度(已掌握 / 该章节题数)", example = "0.25") Double masteryRate
) {
}
