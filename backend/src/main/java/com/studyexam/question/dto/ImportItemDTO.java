package com.studyexam.question.dto;

import com.studyexam.question.OptionItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导入文件里的单条题目。字段与 docs/study-exam/backend/03-api-spec.md 第五节一致。
 *
 * <p>用 {@code chapterName} 而不是 chapterId:导入文件由人工/工具(PDF 转换)生成,
 * 写章节名比写数据库 ID 稳定得多;找不到章节时 chapterId 留空并在结果里提示。
 */
@Schema(description = "导入条目")
public record ImportItemDTO(
        @Schema(description = "大题名,如「一、单项选择题」", example = "一、单项选择题") String sectionName,
        @Schema(description = "题型", example = "SINGLE_CHOICE") String type,
        @Schema(description = "章节名,找不到则不归档", example = "第一章 物质世界及其发展规律") String chapterName,
        @Schema(description = "题干") String stem,
        @Schema(description = "选项(客观题)") List<OptionItem> options,
        @Schema(description = "客观题答案", example = "B") String answer,
        @Schema(description = "主观题参考答案要点") List<String> answerPoints,
        @Schema(description = "解析") String analysis,
        @Schema(description = "真题年份;缺省时用试卷的 year", example = "2024") Integer sourceYear,
        @Schema(description = "分值,缺省 2.0", example = "2.0") BigDecimal score,
        @Schema(description = "难度 1/2/3,缺省 2", example = "1") Integer difficulty,
        @Schema(description = "状态 PUBLISHED 已入库 / DRAFT 待校对;缺省 PUBLISHED。"
                + "PDF 识别里由模型代答的题目会带 DRAFT,提醒人工核对答案", example = "DRAFT") String status
) {
}