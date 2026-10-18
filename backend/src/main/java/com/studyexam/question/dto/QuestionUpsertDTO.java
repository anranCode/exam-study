package com.studyexam.question.dto;

import com.studyexam.question.OptionItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新建 / 修改题目的请求体。字段名与 docs/study-exam/backend/03-api-spec.md 第三节一致。
 *
 * <p>题型相关的规则(客观题必须有 options+answer、主观题必须有 answerPoints 且无 answer)
 * 依赖 type 的取值,无法用声明式注解表达,由 {@code QuestionValidator} 手写校验。
 */
@Schema(description = "题目新建/修改请求")
public record QuestionUpsertDTO(

        @Schema(description = "科目 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "科目不能为空")
        Long subjectId,

        @Schema(description = "章节 ID,可为空表示未归章")
        Long chapterId,

        @Schema(description = "题型,见 02-data-model.md 取值表", example = "SINGLE_CHOICE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "题型不能为空")
        String type,

        @Schema(description = "题干", example = "马克思主义哲学的基本问题是（  ）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "题干不能为空")
        @Size(min = 1, max = 2000, message = "题干长度必须在 1—2000 字之间")
        String stem,

        @Schema(description = "选项数组,仅客观题")
        List<OptionItem> options,

        @Schema(description = "客观题正确答案", example = "B")
        String answer,

        @Schema(description = "主观题参考答案要点,仅主观题")
        List<String> answerPoints,

        @Schema(description = "解析")
        String analysis,

        @Schema(description = "难度:1 简单 / 2 中等 / 3 困难", example = "1")
        @Min(value = 1, message = "难度只能是 1、2、3")
        @Max(value = 3, message = "难度只能是 1、2、3")
        Integer difficulty,

        @Schema(description = "来源", example = "2024年10月真题")
        @Size(max = 64, message = "来源最长 64 个字符")
        String source,

        @Schema(description = "真题年份", example = "2024")
        @Min(value = 1970, message = "年份不合法")
        @Max(value = 2999, message = "年份不合法")
        Integer sourceYear,

        @Schema(description = "本题分值", example = "2.0")
        @NotNull(message = "分值不能为空")
        @jakarta.validation.constraints.DecimalMin(value = "0.5", message = "分值必须大于 0")
        @jakarta.validation.constraints.DecimalMax(value = "100.0", message = "分值不能超过 100")
        BigDecimal score,

        @Schema(description = "状态:DRAFT 待校对 / PUBLISHED 已入库", example = "PUBLISHED")
        String status
) {
}