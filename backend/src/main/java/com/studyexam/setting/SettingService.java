package com.studyexam.setting;

import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.question.QuestionType;
import com.studyexam.setting.dto.PracticeDefaultsUpsertDTO;
import com.studyexam.setting.dto.PracticeDefaultsVO;
import com.studyexam.setting.dto.SettingUpsertDTO;
import com.studyexam.setting.dto.SettingVO;
import com.studyexam.subject.SubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final Set<String> PRACTICE_SOURCES = Set.of("LIBRARY", "WRONG");
    /** 自由练习单场题量上限,与 SessionGenerator / 前端题量选择器一致。 */
    private static final int MAX_PRACTICE_COUNT = 100;

    private final UserSettingRepository repository;
    private final SubjectRepository subjectRepository;

    public SettingService(UserSettingRepository repository, SubjectRepository subjectRepository) {
        this.repository = repository;
        this.subjectRepository = subjectRepository;
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
        if (dto.practiceDefaults() != null) {
            applyPracticeDefaults(s, dto.practiceDefaults());
        }

        UserSetting saved = repository.save(s);
        log.info("设置已更新 channel={} reminder={} {} evening={} {} source={}",
                saved.getNotifyChannel(), saved.getReminderEnabled(), saved.getReminderTime(),
                saved.getEveningReminderEnabled(), saved.getEveningReminderTime(),
                saved.getPracticeSource());
        return toVO(saved);
    }

    /**
     * 应用自由练习默认条件。
     *
     * <p>{@code subjectId} / {@code types} 传 null 表示清空(全部科目 / 全部题型);
     * {@code source} 与两个 {@code count} 传 null 表示沿用原值。
     */
    private void applyPracticeDefaults(UserSetting s, PracticeDefaultsUpsertDTO d) {
        if (d.source() != null) {
            s.setPracticeSource(requirePracticeSource(d.source()));
        }
        s.setLibrarySubjectId(requireSubject(d.librarySubjectId(), "librarySubjectId"));
        s.setLibraryTypes(joinTypes(d.libraryTypes()));
        if (d.libraryCount() != null) {
            s.setLibraryCount(requirePracticeCount("libraryCount", d.libraryCount()));
        }
        s.setWrongSubjectId(requireSubject(d.wrongSubjectId(), "wrongSubjectId"));
        s.setWrongTypes(joinTypes(d.wrongTypes()));
        if (d.wrongCount() != null) {
            s.setWrongCount(requirePracticeCount("wrongCount", d.wrongCount()));
        }
    }

    private String requirePracticeSource(String raw) {
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (!PRACTICE_SOURCES.contains(v)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "practiceDefaults.source: 只能是 LIBRARY 或 WRONG");
        }
        return v;
    }

    private int requirePracticeCount(String field, int count) {
        if (count < 1 || count > MAX_PRACTICE_COUNT) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, field + ": 取值 1—" + MAX_PRACTICE_COUNT);
        }
        return count;
    }

    private Long requireSubject(Long id, String field) {
        if (id == null) {
            return null;
        }
        if (!subjectRepository.existsById(id)) {
            throw BusinessException.of(ErrorCode.SUBJECT_NOT_FOUND, field + ": 科目不存在:id=" + id);
        }
        return id;
    }

    /** 题型规范化为大写、去重后按逗号拼接;空列表落库为 null(表示全部)。 */
    private String joinTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : types) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String t = raw.trim().toUpperCase(Locale.ROOT);
            QuestionType.find(t).orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID,
                    "未知题型:\"" + raw + "\""));
            normalized.add(t);
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private List<String> splitTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
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
                s.getDarkMode(),
                new PracticeDefaultsVO(
                        s.getPracticeSource(),
                        s.getLibrarySubjectId(),
                        splitTypes(s.getLibraryTypes()),
                        s.getLibraryCount() == null ? 10 : s.getLibraryCount(),
                        s.getWrongSubjectId(),
                        splitTypes(s.getWrongTypes()),
                        s.getWrongCount() == null ? 10 : s.getWrongCount()));
    }
}
