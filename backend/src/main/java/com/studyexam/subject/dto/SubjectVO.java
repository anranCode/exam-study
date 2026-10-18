package com.studyexam.subject.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 科目列表 / 详情响应。字段名与 docs/study-exam/backend/03-api-spec.md 第二节示例一致。
 */
@Schema(description = "科目")
public record SubjectVO(
        @Schema(description = "科目 ID", example = "1") Long id,
        @Schema(description = "课程代码", example = "03709") String code,
        @Schema(description = "科目全称", example = "马克思主义基本原理概论") String name,
        @Schema(description = "简称", example = "马原") String shortName,
        @Schema(description = "标识色", example = "#E5484D") String color,
        @Schema(description = "考试日期", example = "2026-10-24") LocalDate examDate,
        @Schema(description = "考试场次", example = "D1_AM") String examSlot,
        @Schema(description = "满分", example = "100") Integer totalScore,
        @Schema(description = "及格分", example = "60") Integer passScore,
        @Schema(description = "排序", example = "1") Integer sortOrder,
        @Schema(description = "是否参与每日任务") Boolean enabled,
        @Schema(description = "备注") String remark,
        @Schema(description = "题目数量", example = "320") long questionCount,
        @Schema(description = "到期复习数量", example = "12") long dueReviewCount,
        @Schema(description = "已掌握数量", example = "205") long masteredCount
) {
}