package com.studyexam.notify;

import com.studyexam.config.NotifyProperties;
import com.studyexam.notify.dto.NotifyLogVO;
import com.studyexam.notify.dto.NotifyTestResultVO;
import com.studyexam.setting.SettingService;
import com.studyexam.setting.UserSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 提醒发送与日志。
 *
 * <p>一条原则:**失败原因必须能直接展示给用户**。所以这里不往外抛异常,而是把原因塞进返回值
 * (测试接口)或 {@code reminder_log.fail_reason}(定时提醒),页面据此显示。
 *
 * <p>密钥保护:失败原因里如果出现 Webhook/SendKey 原文,一律替换成 {@code ***} 再落库。
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);
    private static final Long USER_ID = 1L;

    private final SettingService settingService;
    private final ReminderLogRepository logRepository;
    private final NotifyProperties props;
    private final List<NotifySender> senders;

    public NotifyService(SettingService settingService,
                         ReminderLogRepository logRepository,
                         NotifyProperties props,
                         List<NotifySender> senders) {
        this.settingService = settingService;
        this.logRepository = logRepository;
        this.props = props;
        this.senders = senders;
    }

    /** 设置页点「发送测试提醒」:立即发一条,并把结果原样告诉页面。 */
    public NotifyTestResultVO test() {
        UserSetting setting = settingService.current();
        String body = "如果你看到这条,说明提醒通道已经配好了。\n" + props.getAppBaseUrl() + "/";
        return send(setting, "自考学习中心 · 测试提醒", body);
    }

    /** 发送但不写日志(测试用)。 */
    public NotifyTestResultVO send(UserSetting setting, String title, String body) {
        try {
            dispatch(setting, title, body);
            return new NotifyTestResultVO(true, "已发送,请查看微信");
        } catch (Exception e) {
            String reason = readable(e);
            log.warn("提醒发送失败:{}", reason);
            return new NotifyTestResultVO(false, reason);
        }
    }

    /** 定时提醒用:发完无论成败都记一条 {@code reminder_log}(唯一键保证当天同类只发一次)。 */
    @Transactional
    public NotifyTestResultVO sendAndLog(UserSetting setting, String type, String title, String body) {
        NotifyTestResultVO result = send(setting, title, body);
        ReminderLog entry = new ReminderLog();
        entry.setUserId(USER_ID);
        entry.setLogDate(LocalDate.now());
        entry.setType(type);
        entry.setSentAt(LocalDateTime.now());
        entry.setSuccess(result.success());
        entry.setFailReason(result.success() ? null : trim(result.message(), 255));
        logRepository.save(entry);
        return result;
    }

    @Transactional(readOnly = true)
    public List<NotifyLogVO> logs(int days) {
        int n = Math.max(1, Math.min(days, 90));
        LocalDate from = LocalDate.now().minusDays(n - 1L);
        return logRepository.findByUserIdAndLogDateGreaterThanEqualOrderBySentAtDesc(USER_ID, from)
                .stream().map(this::toVO).toList();
    }

    // ── 内部 ────────────────────────────────────────────────

    private void dispatch(UserSetting setting, String title, String body) throws Exception {
        String channel = setting.getNotifyChannel() == null
                ? "WECOM" : setting.getNotifyChannel().trim().toUpperCase(Locale.ROOT);
        NotifySender sender = senders.stream()
                .filter(s -> s.channel().equals(channel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知的提醒通道:" + channel));

        String target;
        if ("SERVERCHAN".equals(channel)) {
            target = firstNonBlank(setting.getNotifyKey(), props.getServerchanKey());
            if (target == null) {
                throw new IllegalStateException("还没填 Server酱 SendKey,去设置页补上");
            }
        } else {
            target = firstNonBlank(setting.getNotifyWebhook(), props.getWecomWebhook());
            if (target == null) {
                throw new IllegalStateException("还没填企业微信 Webhook,去设置页补上");
            }
        }

        try {
            sender.send(target, title, body);
        } catch (Exception e) {
            String reason = readable(e);
            // 失败原因里可能夹着带 key 的地址,抹掉再往外给
            throw new IllegalStateException(reason.replace(target, "***"));
        }
    }

    private NotifyLogVO toVO(ReminderLog entry) {
        return new NotifyLogVO(
                entry.getId(),
                entry.getLogDate(),
                entry.getType(),
                ReminderLog.TYPE_EVENING.equals(entry.getType()) ? "晚间未打卡" : "早间提醒",
                entry.getSentAt(),
                Boolean.TRUE.equals(entry.getSuccess()),
                entry.getFailReason());
    }

    private String readable(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max - 1) + "…" : text;
    }
}
