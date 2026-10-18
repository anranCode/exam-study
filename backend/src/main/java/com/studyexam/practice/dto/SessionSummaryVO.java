package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** 结束练习后的本轮汇总。 */
@Schema(description = "本轮小结")
public record SessionSummaryVO(
        @Schema(description = "场次 ID") Long sessionId,
        @Schema(description = "组题模式") String mode,
        @Schema(description = "本轮题数") int totalCount,
        @Schema(description = "已作答数") int answeredCount,
        @Schema(description = "已作答的客观题数(正确率分母)") int objectiveAnsweredCount,
        @Schema(description = "答对数(仅客观题)") int correctCount,
        @Schema(description = "本轮得分(客观题得分 + 主观题自评得分)") BigDecimal score,
        @Schema(description = "本轮满分") BigDecimal totalScore,
        @Schema(description = "正确率 0—1;没有客观题作答时为 null") Double accuracy,
        @Schema(description = "用时(秒)") int durationSeconds,
        @Schema(description = "本轮新进复习队列的题数") int reviewCreated,
        @Schema(description = "本轮更新的复习条目数") int reviewUpdated,
        @Schema(description = "本轮错题(客观题答错 + 主观题未答全要点)") List<WrongQuestion> wrongQuestions
) {

    @Schema(description = "错题摘要")
    public record WrongQuestion(
            @Schema(description = "题目 ID") Long questionId,
            @Schema(description = "题干") String stem,
            @Schema(description = "题型") String type,
            @Schema(description = "题型中文名") String typeLabel,
            @Schema(description = "科目简称") String subjectShortName,
            @Schema(description = "我的作答") String userAnswer,
            @Schema(description = "正确答案") String correctAnswer,
            @Schema(description = "是否来自复习队列") boolean isReview
    ) {
    }
}
