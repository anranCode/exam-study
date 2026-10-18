package com.studyexam.question;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 题目选项。存于 question.options JSON 列,结构为 {@code [{"key":"A","text":"…"}]}。
 */
@Schema(description = "题目选项")
public record OptionItem(
        @Schema(description = "选项字母", example = "A") String key,
        @Schema(description = "选项内容") String text
) {
}