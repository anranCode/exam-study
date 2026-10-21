package com.studyexam.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 自由练习的默认条件。GET /settings 返回。
 *
 * <p>「题库」「错题」两个题源各有一份科目 / 题型 / 题量,进入自由练习时自动带入,
 * 用户不用每次重选。{@code types} 为空表示不限题型,{@code subjectId} 为空表示全部科目。
 */
@Schema(description = "自由练习默认条件")
public record PracticeDefaultsVO(
        @Schema(description = "默认题源:LIBRARY 题库 / WRONG 错题", example = "LIBRARY")
        String source,
        @Schema(description = "题库源默认科目 ID,null=全部") Long librarySubjectId,
        @Schema(description = "题库源默认题型;空数组=全部", example = "[\"SINGLE_CHOICE\"]")
        List<String> libraryTypes,
        @Schema(description = "题库源默认题量", example = "10") int libraryCount,
        @Schema(description = "错题源默认科目 ID,null=全部") Long wrongSubjectId,
        @Schema(description = "错题源默认题型;空数组=全部", example = "[\"SHORT_ANSWER\"]")
        List<String> wrongTypes,
        @Schema(description = "错题源默认题量", example = "10") int wrongCount
) {
}
