package com.studyexam.importer.dto;

import com.studyexam.importer.ImportIssue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "新增导入记录(导入完成后由前端提交)")
public record ImportLogCreateDTO(
        @Schema(description = "来源:PDF 识别 / JSON 导入", example = "PDF") String source,
        @Schema(description = "课程代码", example = "03709") String subjectCode,
        @Schema(description = "试卷名或文件名", example = "2024年10月真题") String paperName,
        @Schema(description = "条目总数", example = "40") Integer total,
        @Schema(description = "成功入库数", example = "38") Integer imported,
        @Schema(description = "跳过数(校验失败 + 重复)", example = "2") Integer skipped,
        @Schema(description = "其中因题干重复跳过的条数", example = "0") Integer duplicated,
        @Schema(description = "没入库的条目明细") List<ImportIssue> issues,
        @Schema(description = "识别阶段的提醒") List<String> warnings
) {
}
