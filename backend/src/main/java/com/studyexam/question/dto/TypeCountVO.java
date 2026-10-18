package com.studyexam.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 某题型「可练习」的题数,给「自由练习」的题型下拉用。
 *
 * <p>只数已发布的题:待校对的题不进抽题池,列出来只会让用户选了却抽不到。
 */
@Schema(description = "题型可练习题数")
public record TypeCountVO(
        @Schema(description = "题型", example = "SINGLE_CHOICE") String type,
        @Schema(description = "题型中文名", example = "单项选择题") String typeLabel,
        @Schema(description = "可练习题数", example = "32") long count
) {
}
