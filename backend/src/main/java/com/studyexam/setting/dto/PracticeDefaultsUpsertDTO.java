package com.studyexam.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 更新自由练习默认条件。作为 {@link SettingUpsertDTO#practiceDefaults()} 的一部分整体提交:
 * 对象为 null 表示不改;对象存在时,{@code subjectId} 为 null 表示清空默认科目,
 * {@code types} 为 null/空表示不限题型。
 */
@Schema(description = "更新自由练习默认条件")
public record PracticeDefaultsUpsertDTO(
        @Schema(description = "默认题源:LIBRARY / WRONG;null 表示不改", example = "LIBRARY")
        String source,
        @Schema(description = "题库源默认科目 ID;null 表示全部") Long librarySubjectId,
        @Schema(description = "题库源默认题型;null/空表示全部") List<String> libraryTypes,
        @Schema(description = "题库源默认题量 1—100", example = "10") Integer libraryCount,
        @Schema(description = "错题源默认科目 ID;null 表示全部") Long wrongSubjectId,
        @Schema(description = "错题源默认题型;null/空表示全部") List<String> wrongTypes,
        @Schema(description = "错题源默认题量 1—100", example = "10") Integer wrongCount
) {
}
