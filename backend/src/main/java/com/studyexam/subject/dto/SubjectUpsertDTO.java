package com.studyexam.subject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新建 / 修改科目的请求体。
 */
@Schema(description = "科目新建/修改请求")
public record SubjectUpsertDTO(

        @Schema(description = "课程代码", example = "03709", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "课程代码不能为空")
        @Size(max = 16, message = "课程代码最长 16 个字符")
        String code,

        @Schema(description = "科目全称", example = "马克思主义基本原理概论", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "科目全称不能为空")
        @Size(max = 128, message = "科目全称最长 128 个字符")
        String name,

        @Schema(description = "简称,界面显示用", example = "马原", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "简称不能为空")
        @Size(max = 32, message = "简称最长 32 个字符")
        String shortName,

        @Schema(description = "标识色,#RRGGBB", example = "#E5484D")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "标识色必须是 #RRGGBB 格式")
        String color,

        @Schema(description = "考试日期", example = "2026-10-24")
        LocalDate examDate,

        @Schema(description = "考试场次:D1_AM / D1_PM / D2_AM / D2_PM", example = "D1_AM")
        @Pattern(regexp = "^(D1_AM|D1_PM|D2_AM|D2_PM)?$",
                message = "考试场次只能是 D1_AM / D1_PM / D2_AM / D2_PM 之一")
        String examSlot,

        @Schema(description = "满分", example = "100")
        @Min(value = 1, message = "满分必须大于 0")
        @Max(value = 1000, message = "满分不能超过 1000")
        Integer totalScore,

        @Schema(description = "及格分", example = "60")
        @Min(value = 0, message = "及格分不能为负")
        @Max(value = 1000, message = "及格分不能超过 1000")
        Integer passScore,

        @Schema(description = "列表排序,越小越前", example = "1")
        Integer sortOrder,

        @Schema(description = "是否参与每日任务", example = "true")
        Boolean enabled,

        @Schema(description = "备注")
        @Size(max = 255, message = "备注最长 255 个字符")
        String remark,

        @Schema(description = "AI 生成答案/解析时的科目专属提示词,可空",
                example = "答案一律用英文,词汇不超过四级;写作题 120—150 词")
        @Size(max = 2000, message = "AI 提示词最长 2000 个字符")
        String aiPrompt
) {
}