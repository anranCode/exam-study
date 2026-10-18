package com.studyexam.notify;

import com.studyexam.config.NotifyProperties;
import com.studyexam.review.ReviewService;
import com.studyexam.review.dto.DueSummaryVO;
import com.studyexam.setting.SettingService;
import com.studyexam.setting.UserSetting;
import com.studyexam.stats.StatsService;
import com.studyexam.stats.StudyLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Collectors;

/**
 * 定时提醒。每分钟醒一次,判断是否到了早间/晚间的提醒时间。
 *
 * <p>三条防打扰规则(见 docs/study-exam/backend/04-features.md 阶段 7):
 * <ul>
 *   <li>当天同类提醒只发一条 —— 靠 {@code reminder_log} 的唯一键;</li>
 *   <li>到期题数为 0 时不发早间提醒;</li>
 *   <li>当天已经做过题时不发晚间提醒。</li>
 * </ul>
 *
 * <p>发送失败只记日志,不抛出 —— 一个通道配错不该让整个调度停摆。
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final Long USER_ID = 1L;
    private static final long NEAR_MINUTES = 1;

    private final SettingService settingService;
    private final ReviewService reviewService;
    private final StatsService statsService;
    private final StudyLogRepository studyLogRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final NotifyService notifyService;
    private final NotifyProperties props;

    public ReminderScheduler(SettingService settingService,
                             ReviewService reviewService,
                             StatsService statsService,
                             StudyLogRepository studyLogRepository,
                             ReminderLogRepository reminderLogRepository,
                             NotifyService notifyService,
                             NotifyProperties props) {
        this.settingService = settingService;
        this.reviewService = reviewService;
        this.statsService = statsService;
        this.studyLogRepository = studyLogRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.notifyService = notifyService;
        this.props = props;
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        try {
            runOnce();
        } catch (Exception e) {
            log.warn("提醒任务异常:{}", e.getMessage());
        }
    }

    /** 抽成单独的方法,方便以后写测试直接调。 */
    void runOnce() {
        UserSetting setting = settingService.current();
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        LocalDate today = LocalDate.now();

        if (Boolean.TRUE.equals(setting.getReminderEnabled())
                && near(now, setting.getReminderTime())
                && !sentToday(today, ReminderLog.TYPE_MORNING)) {
            sendMorning(setting);
        }

        if (Boolean.TRUE.equals(setting.getEveningReminderEnabled())
                && near(now, setting.getEveningReminderTime())
                && !sentToday(today, ReminderLog.TYPE_EVENING)) {
            sendEvening(setting, today);
        }
    }

    private void sendMorning(UserSetting setting) {
        DueSummaryVO due = reviewService.dueSummary();
        if (due.totalDue() == 0) {
            log.debug("今日没有到期题,不打扰");
            return;
        }
        String detail = due.bySubject().stream()
                .map(s -> "《" + s.shortName() + "》" + s.dueCount() + " 题")
                .collect(Collectors.joining("、"));
        String body = "今日 " + due.totalDue() + " 题待复习"
                + (detail.isEmpty() ? "" : ",其中" + detail)
                + "\n" + props.getAppBaseUrl() + "/practice/daily";

        notifyService.sendAndLog(setting, ReminderLog.TYPE_MORNING, "自考 · 今日待复习", body);
    }

    private void sendEvening(UserSetting setting, LocalDate today) {
        if (studyLogRepository.countStudiedOn(USER_ID, today) > 0) {
            log.debug("今天已经做过题,不发晚间提醒");
            return;
        }
        int streak = statsService.overview().streakDays();
        String body = "今天还没打卡,做 5 题也算"
                + (streak > 0 ? " —— 已连续 " + streak + " 天" : "")
                + "\n" + props.getAppBaseUrl() + "/";

        notifyService.sendAndLog(setting, ReminderLog.TYPE_EVENING, "自考 · 今天还没打卡", body);
    }

    private boolean sentToday(LocalDate today, String type) {
        return reminderLogRepository.existsByUserIdAndLogDateAndType(USER_ID, today, type);
    }

    /** 当前时间是否落在目标时间的 ±1 分钟内(cron 每分钟跑一次,给一点容差)。 */
    private boolean near(LocalTime now, LocalTime target) {
        if (target == null) {
            return false;
        }
        return Math.abs(Duration.between(target, now).toMinutes()) <= NEAR_MINUTES;
    }
}
