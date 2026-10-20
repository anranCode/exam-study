package com.studyexam.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 错题本里某题型的错题数,给「按题型复习」的下拉用。
 *
 * <p>只数还在队列里(LEARNING)的题:已掌握的不该出现在「还能练什么」的选项里。
 */
@Schema(description = "错题本题型分布")
public record ReviewTypeCountVO(
        @Schema(description = "题型", example = "SINGLE_CHOICE") String type,
        @Schema(description = "题型中文名", example = "单项选择题") String typeLabel,
        @Schema(description = "该题型的错题数", example = "12") long count
) {
}
