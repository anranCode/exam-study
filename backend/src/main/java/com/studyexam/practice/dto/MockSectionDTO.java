package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 模拟考试的一个题型段落。
 *
 * <p>自考试卷的结构是「每种题型若干道、每题固定分值」,所以按题型给数量就能组出一份
 * 结构接近真题的卷子;分值直接用题库里每道题自己的 score,不在这里重复定义。
 */
@Schema(description = "模拟考试的题型段落")
public record MockSectionDTO(
        @Schema(description = "题型,如 SINGLE_CHOICE", example = "SINGLE_CHOICE") String type,
        @Schema(description = "该题型抽多少道", example = "25") Integer count
) {
}
