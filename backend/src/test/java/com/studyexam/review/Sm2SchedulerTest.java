package com.studyexam.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SM-2 排期的单元测试。断言基准照 docs/study-exam/backend/04-features.md 阶段 5 的推演表写:
 * 初始 interval = 1、ease_factor = 2.5 时——
 * 第 1 次 SURE → stage 1、interval 1;第 2 次 → stage 2、interval 3;第 3 次 → stage 3、interval 8;
 * 第 4 次 → stage 4、MASTERED。
 */
class Sm2SchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 20, 30);

    private ReviewItem fresh() {
        return Sm2Scheduler.newItem(501L, NOW);
    }

    @Test
    @DisplayName("首次答错:stage 归零、间隔回到 1 天、答错计数 +1、连续答对清零、难度系数下调")
    void firstWrong() {
        ReviewItem item = fresh();
        item.setStage(3);
        item.setIntervalDays(8);
        item.setCorrectStreak(3);

        Sm2Scheduler.apply(item, "WRONG", NOW);

        assertThat(item.getStage()).isZero();
        assertThat(item.getIntervalDays()).isEqualTo(1);
        assertThat(item.getWrongCount()).isEqualTo(1);
        assertThat(item.getCorrectStreak()).isZero();
        assertThat(item.getEaseFactor()).isEqualByComparingTo("2.30");
        assertThat(item.getStatus()).isEqualTo("LEARNING");
        assertThat(item.getNextReviewAt()).isEqualTo(LocalDate.of(2026, 10, 16).atTime(7, 0));
    }

    @Test
    @DisplayName("答对但没把握:阶段不动、间隔回到 1 天,明天再来——蒙对的题必须回到队列")
    void unsureKeepsStageButResetsInterval() {
        ReviewItem item = fresh();
        item.setStage(2);
        item.setIntervalDays(3);
        item.setEaseFactor(new BigDecimal("2.50"));

        Sm2Scheduler.apply(item, "UNSURE", NOW);

        assertThat(item.getStage()).isEqualTo(2);
        assertThat(item.getIntervalDays()).isEqualTo(1);
        assertThat(item.getEaseFactor()).isEqualByComparingTo("2.50");
        assertThat(item.getNextReviewAt()).isEqualTo(LocalDate.of(2026, 10, 16).atTime(7, 0));
    }

    @Test
    @DisplayName("连续答对:阶段与间隔按 1 → 3 → 8 推进(第 4 次标记已掌握)")
    void consecutiveSureAdvancesStages() {
        ReviewItem item = fresh();

        // 第 1 次:stage 1,interval 1
        Sm2Scheduler.apply(item, "SURE", NOW);
        assertThat(item.getStage()).isEqualTo(1);
        assertThat(item.getIntervalDays()).isEqualTo(1);
        assertThat(item.getCorrectStreak()).isEqualTo(1);

        // 第 2 次:stage 2,round(1 × 2.5) = 3
        Sm2Scheduler.apply(item, "SURE", NOW);
        assertThat(item.getStage()).isEqualTo(2);
        assertThat(item.getIntervalDays()).isEqualTo(3);

        // 第 3 次:stage 3,round(3 × 2.5) = 8
        Sm2Scheduler.apply(item, "SURE", NOW);
        assertThat(item.getStage()).isEqualTo(3);
        assertThat(item.getIntervalDays()).isEqualTo(8);

        // 第 4 次:stage 4 → MASTERED,移出复习队列
        Sm2Scheduler.apply(item, "SURE", NOW);
        assertThat(item.getStage()).isEqualTo(4);
        assertThat(item.getStatus()).isEqualTo("MASTERED");
        assertThat(item.getCorrectStreak()).isEqualTo(4);
    }

    @Test
    @DisplayName("阶段推进后再答错:回到 LEARNING,阶段归零")
    void masteredThenWrongReturnsToQueue() {
        ReviewItem item = fresh();
        for (int i = 0; i < 4; i++) {
            Sm2Scheduler.apply(item, "SURE", NOW);
        }
        assertThat(item.getStatus()).isEqualTo("MASTERED");

        Sm2Scheduler.apply(item, "WRONG", NOW);

        assertThat(item.getStatus()).isEqualTo("LEARNING");
        assertThat(item.getStage()).isZero();
        assertThat(item.getIntervalDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("难度系数下限 1.30:连续答错也不会低于下限")
    void easeFactorFloor() {
        ReviewItem item = fresh();
        for (int i = 0; i < 10; i++) {
            Sm2Scheduler.apply(item, "WRONG", NOW);
        }
        assertThat(item.getEaseFactor()).isEqualByComparingTo("1.30");
        assertThat(item.getWrongCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("难度系数上限 3.00:clamp 两个方向都不越界")
    void easeFactorCeiling() {
        assertThat(Sm2Scheduler.clampEase(new BigDecimal("4.00"))).isEqualByComparingTo("3.00");
        assertThat(Sm2Scheduler.clampEase(new BigDecimal("0.50"))).isEqualByComparingTo("1.30");
    }

    @Test
    @DisplayName("间隔上限 60 天:长间隔推进后被截断")
    void intervalCappedAtSixty() {
        ReviewItem item = fresh();
        item.setEaseFactor(new BigDecimal("3.00"));
        for (int i = 0; i < 12; i++) {
            Sm2Scheduler.apply(item, "SURE", NOW);
        }
        assertThat(item.getIntervalDays()).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("next_review_at 落在作答日 + N 天的早上 7 点")
    void nextReviewAtIsMorningOfTargetDay() {
        assertThat(Sm2Scheduler.nextReviewAt(NOW, 3))
                .isEqualTo(LocalDateTime.of(2026, 10, 18, 7, 0));
        // 间隔为 0 或负数时按 1 天处理
        assertThat(Sm2Scheduler.nextReviewAt(NOW, 0))
                .isEqualTo(LocalDateTime.of(2026, 10, 16, 7, 0));
    }

    @Test
    @DisplayName("未知信心度直接报错,不静默算错排期")
    void unknownConfidenceRejected() {
        ReviewItem item = fresh();
        assertThatThrownBy(() -> Sm2Scheduler.apply(item, "MAYBE", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}