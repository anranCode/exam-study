package com.studyexam.setting;

import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.setting.dto.SettingUpsertDTO;
import com.studyexam.setting.dto.SettingVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

/**
 * 用户设置的读写。单用户应用,永远只操作 user_id = 1 的那一行。
 *
 * <p><b>密钥掩码</b>:{@code GET} 只回 {@code …} + 末 6 位的掩码;{@code PUT} 时如果前端把掩码原样传回来,
 * 视为「没改」——这样表单可以安全地展示已有配置而不回传明文。
 */
@Service
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);
    private static final Long USER_ID = 1L;
    private static final int MASK_TAIL = 6;
    private static final Set<String> CHANNELS = Set.of("WECOM", "SERVERCHAN");
    private static final Set<String> DARK_MODES = Set.of("AUTO", "LIGHT", "DARK");

    private final UserSettingRepository repository;

    public SettingService(UserSettingRepository repository) {
        this.repository = repository;
    }

    /** 当前设置;表里没有(理论上 V5 会插入)时按默认值建一行。 */
    @Transactional
    public UserSetting current() {
        return repository.findByUserId(USER_ID).orElseGet(() -> {
            UserSetting s = new UserSetting();
            s.setUserId(USER_ID);
            return repository.save(s);
        });
    }

    @Transactional(readOnly = true)
    public SettingVO get() {
        return toVO(current());
    }

    @Transactional
    public SettingVO update(SettingUpsertDTO dto) {
        UserSetting s = current();

        if (dto.dailyQuestionCount() != null) {
            int n = dto.dailyQuestionCount();
            if (n < 1 || n > 200) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID, "dailyQuestionCount: 取值 1—200");
            }
            s.setDailyQuestionCount(n);
        }
        if (dto.notifyChannel() != null) {
            s.setNotifyChannel(requireChannel(dto.notifyChannel()));
        }
        s.setNotifyWebhook(resolveSecret(dto.notifyWebhook(), s.getNotifyWebhook()));
        s.setNotifyKey(resolveSecret(dto.notifyKey(), s.getNotifyKey()));
        if (dto.reminderEnabled() != null) {
            s.setReminderEnabled(dto.reminderEnabled());
        }
        if (dto.reminderTime() != null) {
            s.setReminderTime(dto.reminderTime());
        }
        if (dto.eveningReminderEnabled() != null) {
            s.setEveningReminderEnabled(dto.eveningReminderEnabled());
        }
        if (dto.eveningReminderTime() != null) {
            s.setEveningReminderTime(dto.eveningReminderTime());
        }
        if (dto.darkMode() != null) {
            String mode = dto.darkMode().trim().toUpperCase(Locale.ROOT);
            if (!DARK_MODES.contains(mode)) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID, "darkMode: 只能是 AUTO / LIGHT / DARK");
            }
            s.setDarkMode(mode);
        }

        UserSetting saved = repository.save(s);
        log.info("设置已更新 channel={} reminder={} {} evening={} {}",
                saved.getNotifyChannel(), saved.getReminderEnabled(), saved.getReminderTime(),
                saved.getEveningReminderEnabled(), saved.getEveningReminderTime());
        return toVO(saved);
    }

    /** 密钥掩码:{@code …} + 末 6 位;空值返回 null。 */
    public static String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        String s = secret.trim();
        return s.length() <= MASK_TAIL ? "…" + s : "…" + s.substring(s.length() - MASK_TAIL);
    }

    /**
     * 密钥更新规则:没传 → 不动;传回当前掩码 → 不动;传空串 → 清空;其它 → 覆盖。
     */
    private String resolveSecret(String submitted, String current) {
        if (submitted == null) {
            return current;
        }
        String v = submitted.trim();
        if (v.equals(mask(current))) {
            return current;
        }
        return v.isEmpty() ? null : v;
    }

    private String requireChannel(String raw) {
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(c)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "notifyChannel: 只能是 WECOM 或 SERVERCHAN");
        }
        return c;
    }

    private SettingVO toVO(UserSetting s) {
        return new SettingVO(
                s.getDailyQuestionCount(),
                s.getNotifyChannel(),
                mask(s.getNotifyWebhook()),
                mask(s.getNotifyKey()),
                s.getNotifyWebhook() != null && !s.getNotifyWebhook().isBlank(),
                s.getNotifyKey() != null && !s.getNotifyKey().isBlank(),
                Boolean.TRUE.equals(s.getReminderEnabled()),
                s.getReminderTime(),
                Boolean.TRUE.equals(s.getEveningReminderEnabled()),
                s.getEveningReminderTime(),
                s.getDarkMode());
    }
}
