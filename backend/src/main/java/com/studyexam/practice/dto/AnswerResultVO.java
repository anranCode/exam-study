package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交作答后的判定结果。客观题给对错与正确答案;主观题 isCorrect 为空、
 * 改返回 answerPoints 供前端逐条勾选自评。
 */
@Schema(description = "作答判定结果")
public record AnswerResultVO(
        @Schema(description = "题目 ID") Long questionId,
        @Schema(description = "是否正确;主观题为空") Boolean isCorrect,
        @Schema(description = "客观题正确答案") String correctAnswer,
        @Schema(description = "主观题参考答案要点") List<String> answerPoints,
        @Schema(description = "解析") String analysis,
        @Schema(description = "下次复习时间") LocalDateTime nextReviewAt,
        @Schema(description = "本题是否来自复习队列") boolean isReview,
        @Schema(description = "主观题已自评得分(未自评时为空)") Integer selfScore,
        @Schema(description = "主观题要点总数") Integer totalPoints,
        @Schema(description = "该题累计答错次数(错题本排序依据)", example = "3") int wrongCount,
        @Schema(description = "该题当前连续答对次数;满 3 次自动移出错题本", example = "1") int correctStreak,
        @Schema(description = "本次作答是否让该题移出了错题本(连对满 3 次)", example = "false")
        boolean mastered,
        @Schema(description = "本轮进度") AnswerProgressVO progress
) {
}
