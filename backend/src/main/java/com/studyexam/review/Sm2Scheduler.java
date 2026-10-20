package com.studyexam.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * SM-2 复习排期的简化实现。规则来源:docs/study-exam/backend/01-architecture.md 第三节
 * 与 04-features.md 阶段 5(两者一致)。
 *
 * <table>
 *   <tr><th>作答情况(confidence)</th><th>处理</th></tr>
 *   <tr><td>WRONG</td><td>stage = 0,interval = 1,wrong_count++,
 *       correct_streak = 0,ease_factor -= 0.2(下限 1.30)</td></tr>
 *   <tr><td>UNSURE</td><td>stage 不变,interval = 1,next_review_at = 今天 + 1 天,
 *       correct_streak = 0(没把握不算「连对」)</td></tr>
 *   <tr><td>SURE</td><td>correct_streak++,stage++,
 *       interval = (stage == 1 ? 1 : round(interval × ease_factor));
 *       correct_streak ≥ 3 时 status = MASTERED,自动移出错题本</td></tr>
 * </table>
 *
 * 间隔上限 60 天,ease_factor 上下限 1.30—3.00。
 *
 * <p>「连续答对三次即出队」是本项目的产品规则(比原 SM-2 的 stage ≥ 4 更早出队):
 * 一道错题连着三次答对,说明已经记牢,继续占用每日复习额度只会浪费时间。
 * 出队用的是 MASTERED 状态而不是删行 —— 用户仍可在错题本「已掌握」筛选里看到它;
 * 之后再答错,{@link #apply} 会把它打回 LEARNING 重新入队。
 *
 * <p>纯函数式实现,不依赖 Spring 容器,便于单元测试——这是「算错了会影响学习效果」的逻辑。
 */
public class Sm2Scheduler {

    public static final int MAX_INTERVAL_DAYS = 60;
    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");
    public static final BigDecimal MAX_EASE = new BigDecimal("3.00");
    public static final BigDecimal EASE_STEP = new BigDecimal("0.20");
    /** 连续答对达到该次数即视为已掌握,自动移出错题本。 */
    public static final int MASTER_STREAK = 3;

    private Sm2Scheduler() {
    }

    /** 新进入复习队列的题:stage 0,间隔 1 天,难度系数 2.50,立即到期。 */
    public static ReviewItem newItem(Long questionId, LocalDateTime now) {
        ReviewItem item = new ReviewItem();
        item.setUserId(1L);
        item.setQuestionId(questionId);
        item.setStage(0);
        item.setIntervalDays(1);
        item.setEaseFactor(new BigDecimal("2.50"));
        item.setWrongCount(0);
        item.setCorrectStreak(0);
        item.setStatus("LEARNING");
        item.setLastReviewAt(now);
        item.setNextReviewAt(now);
        return item;
    }

    /**
     * 按本次作答推进排期。入参会被就地修改。
     *
     * @param item        该题的复习条目(新建或已存在)
     * @param confidence  SURE / UNSURE / WRONG
     * @param now         作答时间
     */
    public static void apply(ReviewItem item, String confidence, LocalDateTime now) {
        String c = confidence == null ? "SURE" : confidence.trim().toUpperCase();

        switch (c) {
            case "WRONG" -> {
                item.setStage(0);
                item.setIntervalDays(1);
                item.setWrongCount(item.getWrongCount() + 1);
                item.setCorrectStreak(0);
                item.setEaseFactor(clampEase(item.getEaseFactor().subtract(EASE_STEP)));
                item.setStatus("LEARNING");
            }
            case "UNSURE" -> {
                // 没把握:阶段不动,明天再来一次——「蒙对的题」必须回到队列,
                // 连对计数同时清零(没把握的答对不算「连续答对」)
                item.setIntervalDays(1);
                item.setCorrectStreak(0);
                item.setStatus("LEARNING");
            }
            case "SURE" -> {
                int stage = item.getStage() + 1;
                item.setCorrectStreak(item.getCorrectStreak() + 1);
                item.setStage(stage);
                int interval = stage == 1 ? 1
                        : item.getIntervalDays() == 0 ? 1
                        : item.getEaseFactor().multiply(BigDecimal.valueOf(item.getIntervalDays()))
                            .setScale(0, RoundingMode.HALF_UP).intValue();
                item.setIntervalDays(Math.min(interval, MAX_INTERVAL_DAYS));
                // 连对满 MASTER_STREAK 次:标记已掌握,自动退出错题本
                item.setStatus(item.getCorrectStreak() >= MASTER_STREAK ? "MASTERED" : "LEARNING");
            }
            default -> throw new IllegalArgumentException("未知的 confidence:" + confidence);
        }

        item.setLastReviewAt(now);
        item.setNextReviewAt(nextReviewAt(now, item.getIntervalDays()));
    }

    /**
     * 下次复习时间 = 作答日 + 间隔天数的当天 07:00。
     * 用固定时刻而不是「now + N 天」,让每天的任务边界稳定(和提醒时间对齐)。
     */
    public static LocalDateTime nextReviewAt(LocalDateTime now, int intervalDays) {
        int days = Math.max(1, Math.min(intervalDays, MAX_INTERVAL_DAYS));
        return LocalDate.from(now).plusDays(days).atTime(LocalTime.of(7, 0));
    }

    public static BigDecimal clampEase(BigDecimal ease) {
        if (ease.compareTo(MIN_EASE) < 0) {
            return MIN_EASE;
        }
        if (ease.compareTo(MAX_EASE) > 0) {
            return MAX_EASE;
        }
        return ease;
    }
}