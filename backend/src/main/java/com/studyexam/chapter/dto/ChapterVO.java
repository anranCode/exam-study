package com.studyexam.chapter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 章节。后端返回**扁平列表**(按 sortOrder 排好序),树形结构由前端组装,
 * 这样前端能自由决定是按树渲染还是平铺渲染。
 */
@Schema(description = "章节")
public record ChapterVO(
        @Schema(description = "章节 ID", example = "3") Long id,
        @Schema(description = "所属科目 ID", example = "1") Long subjectId,
        @Schema(description = "父章节 ID,顶级为空") Long parentId,
        @Schema(description = "章节名", example = "第一章 物质世界及其发展规律") String name,
        @Schema(description = "排序", example = "1") Integer sortOrder,
        @Schema(description = "该章节下的题目数量", example = "42") long questionCount
) {
}