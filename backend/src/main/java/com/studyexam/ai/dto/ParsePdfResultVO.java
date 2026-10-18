package com.studyexam.ai.dto;

import com.studyexam.question.dto.ImportItemDTO;
import com.studyexam.question.dto.ImportPaperDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * PDF 试卷识别结果。
 *
 * <p>返回的结构与 {@code POST /questions/import} 的请求体一致(subjectCode / paper / items),
 * 前端可以原样喂给导入接口;识别本身**不写库**,先让用户核对。
 */
@Schema(description = "PDF 试卷识别结果")
public record ParsePdfResultVO(
        @Schema(description = "课程代码", example = "13000") String subjectCode,
        @Schema(description = "试卷信息(可直接用于导入)") ImportPaperDTO paper,
        @Schema(description = "识别出的题目") List<ImportItemDTO> items,
        @Schema(description = "PDF 总页数", example = "7") int totalPages,
        @Schema(description = "本次识别的页数", example = "7") int parsedPages,
        @Schema(description = "调用模型的次数", example = "7") int calls,
        @Schema(description = "输入 token 合计(TokenPlan 用量)", example = "12800") int tokensIn,
        @Schema(description = "输出 token 合计", example = "9600") int tokensOut,
        @Schema(description = "总耗时(毫秒)", example = "82000") long durationMs,
        @Schema(description = "识别过程中的提醒:缺页、题干被截断、某条结构无法解析等") List<String> warnings
) {
}