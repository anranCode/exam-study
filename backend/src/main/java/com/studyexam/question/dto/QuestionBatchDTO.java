package com.studyexam.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 题目批量操作(批量入库等)的请求体。单次最多 200 题,与列表分页上限一致。
 */
@Schema(description = "题目批量操作")
public record QuestionBatchDTO(
        @Schema(description = "题目 ID 列表", example = "[1, 2, 3]")
        @NotEmpty(message = "请选择题目")
        @Size(max = 200, message = "一次最多 200 题")
        List<Long> ids
) {
}
