package com.studyexam.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 错题本里的一条。见 docs/study-exam/backend/03-api-spec.md 第六节。
 *
 * <p>刻意**不带 answer / analysis**:错题本是「回去重做」的入口,不是答案册。
 * 要看正确答案就点「立即复习」进去做一遍——与 {@code PracticeQuestionVO} 的防作弊约定一致。
 */
@Schema(description = "错题本条目")
public record ReviewItemVO(
        @Schema(description = "题目 ID", example = "36") Long questionId,
        @Schema(description = "科目 ID", example = "1") Long subjectId,
        @Schema(description = "科目简称", example = "马原") String subjectShortName,
        @Schema(description = "科目颜色", example = "#E5484D") String subjectColor,
        @Schema(description = "章节 ID,未归章为空") Long chapterId,
        @Schema(description = "章节名") String chapterName,
        @Schema(description = "题型", example = "SINGLE_CHOICE") String type,
        @Schema(description = "题型中文名", example = "单项选择题") String typeLabel,
        @Schema(description = "题干") String stem,
        @Schema(description = "累计答错次数", example = "3") int wrongCount,
        @Schema(description = "连续答对次数;满 3 次自动移出错题本", example = "1") int correctStreak,
        @Schema(description = "SM-2 复习阶段,0 表示新错题", example = "2") int stage,
        @Schema(description = "当前间隔天数", example = "3") int intervalDays,
        @Schema(description = "难度系数 1.30—3.00", example = "2.50") BigDecimal easeFactor,
        @Schema(description = "掌握阶段:NOT_STARTED 未掌握 / LEARNING 复习中 / MASTERED 已掌握",
                example = "LEARNING") String masteryStage,
        @Schema(description = "掌握阶段中文名", example = "复习中") String masteryLabel,
        @Schema(description = "队列状态:LEARNING 在队列 / MASTERED 已掌握", example = "LEARNING") String status,
        @Schema(description = "上次复习时间") LocalDateTime lastReviewAt,
        @Schema(description = "下次复习时间", example = "2026-10-19 07:00:00") LocalDateTime nextReviewAt,
        @Schema(description = "此刻是否已到期") boolean due
) {

    public static final String STAGE_NOT_STARTED = "NOT_STARTED";
    public static final String STAGE_LEARNING = "LEARNING";
    public static final String STAGE_MASTERED = "MASTERED";

    /**
     * 掌握阶段的判定只放在这一处,前端不重复这套规则。
     *
     * <p>三档对应错题本页面要显示的中文:全新错题(还没答对过)= 未掌握;
     * 已经连续答对但还没到出队阶段 = 复习中;stage 推到顶被标记出队 = 已掌握。
     */
    public static String masteryStageOf(String status, int stage) {
        if ("MASTERED".equalsIgnoreCase(status)) {
            return STAGE_MASTERED;
        }
        return stage <= 0 ? STAGE_NOT_STARTED : STAGE_LEARNING;
    }

    public static String masteryLabelOf(String masteryStage) {
        return switch (masteryStage) {
            case STAGE_MASTERED -> "已掌握";
            case STAGE_LEARNING -> "复习中";
            default -> "未掌握";
        };
    }
}
