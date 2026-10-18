package com.studyexam.importer.dto;

import com.studyexam.importer.ImportIssue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "导入记录")
public record ImportLogVO(
        @Schema(description = "记录 ID") Long id,
        @Schema(description = "来源:PDF 识别 / JSON 导入", example = "PDF") String source,
        @Schema(description = "课程代码", example = "03709") String subjectCode,
        @Schema(description = "试卷名或文件名") String paperName,
        @Schema(description = "IMPORTED 全部入库 / PARTIAL 部分入库 / DUPLICATED 全部重复 / FAILED 未入库")
        String status,
        @Schema(description = "条目总数", example = "40") int total,
        @Schema(description = "成功入库数", example = "38") int imported,
        @Schema(description = "跳过数(校验失败 + 重复)", example = "2") int skipped,
        @Schema(description = "其中因题干重复跳过的条数") int duplicated,
        @Schema(description = "没入库的条目明细") List<ImportIssue> issues,
        @Schema(description = "识别阶段的提醒") List<String> warnings,
        @Schema(description = "导入时间") LocalDateTime createdAt
) {
}
