package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 创建练习场次。见 docs/study-exam/backend/03-api-spec.md 第四节。
 */
@Schema(description = "创建练习场次请求")
public record PracticeSessionCreateDTO(
        @Schema(description = "组题模式:DAILY 每日任务 / RANDOM 随机 / CHAPTER 章节 / MOCK 模拟考试 / REVIEW 错题复习",
                example = "DAILY", requiredMode = Schema.RequiredMode.REQUIRED) String mode,
        @Schema(description = "科目 ID,为空表示跨科目") Long subjectId,
        @Schema(description = "章节 ID,CHAPTER 模式必填") Long chapterId,
        @Schema(description = "题量;为空时 DAILY 用每日题量设置,其余默认 10", example = "10") Integer count,
        @Schema(description = "试卷 ID,MOCK 模式必填") Long paperId,
        @Schema(description = "指定题目:非空时忽略组题模式,就按给定的这几道出题(错题本里"
                + "「立即复习」某一道走这里)", example = "[36]") List<Long> questionIds,
        @Schema(description = "题型筛选:只抽这几种题型(如 [\"SINGLE_CHOICE\",\"MULTIPLE_CHOICE\"])。"
                + "空表示不限。RANDOM / CHAPTER 生效", example = "[\"SINGLE_CHOICE\"]") List<String> types
) {
}
