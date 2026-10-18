package com.studyexam.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 导入结果。响应结构与 docs/study-exam/backend/03-api-spec.md 第三节一致。
 *
 * <p>{@code duplicates} 是在文档结构上新增的:题干与库里(或本批前面)已有的题目重复,
 * 这类条目**不算失败**——重复导同一份试卷是常事,不该触发「半数回滚」,
 * 但用户要能一眼看出「为什么只导进来这么几条」,所以单独列出来而不是混进 errors。
 */
@Schema(description = "导入结果")
public record ImportResult(
        @Schema(description = "文件里的条目总数", example = "40") int total,
        @Schema(description = "成功入库数", example = "38") int imported,
        @Schema(description = "跳过数(校验失败 + 重复)", example = "2") int skipped,
        @Schema(description = "因章节名没匹配上而未归档的条目数", example = "3") int unchaptered,
        @Schema(description = "同时创建的试卷 ID;未带 paper 字段或一条都没入库时为空", example = "5") Long paperId,
        @Schema(description = "因校验失败被跳过的条目及原因") List<ImportError> errors,
        @Schema(description = "因题干重复被跳过的条目及原因") List<ImportError> duplicates
) {

    @Schema(description = "导入错误条目")
    public record ImportError(
            @Schema(description = "条目序号,从 1 开始,与用户文件里的第 N 条对应", example = "12") int index,
            @Schema(description = "失败原因", example = "单项选择题缺少 answer 字段") String reason
    ) {
    }
}