package com.studyexam.chapter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Optional;

/**
 * 新建 / 修改章节的请求体。
 *
 * <p>{@code parentId} 用 {@link Optional} 包装,以便区分两种语义:
 * <ul>
 *   <li>字段缺失 → {@code Optional.empty()},修改时视为「不改动父章节」;</li>
 *   <li>显式传 {@code null} → {@code Optional.of(null)},修改时视为「清空父章节,升为顶级」。</li>
 * </ul>
 */
@Schema(description = "章节新建/修改请求")
public record ChapterUpsertDTO(

        @Schema(description = "所属科目 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "所属科目不能为空")
        Long subjectId,

        @Schema(description = "章节名", example = "第一章 物质世界及其发展规律",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "章节名不能为空")
        @Size(max = 128, message = "章节名最长 128 个字符")
        String name,

        @Schema(description = "父章节 ID;缺省表示不修改,显式 null 表示升为顶级")
        Optional<Long> parentId,

        @Schema(description = "排序,越小越前", example = "1")
        Integer sortOrder
) {
}