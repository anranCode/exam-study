package com.studyexam.question.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 导入请求体。结构与 docs/study-exam/backend/03-api-spec.md 第五节完全一致。
 *
 * <p>对未知字段宽容(忽略而不是报错):PDF 转换或人工整理的文件经常多带几个备注字段,
 * 不该因此让整个文件进不来。真正致命的格式问题(items 缺失、非 JSON)才返回 4001。
 */
@Schema(description = "题库导入请求")
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionImportRequest(

        @Schema(description = "课程代码,整份文件属于这一个科目", example = "03709",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String subjectCode,

        @Schema(description = "试卷信息;不带则只入库题目、不建试卷")
        ImportPaperDTO paper,

        @Schema(description = "题目条目列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ImportItemDTO> items
) {
}