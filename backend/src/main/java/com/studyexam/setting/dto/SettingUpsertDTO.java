package com.studyexam.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

/**
 * 更新设置。对应 PUT /settings。
 *
 * <p>字段都按「传了就改,没传不动」处理,前端可以只提交改动的那几项。
 *
 * <p><b>密钥</b>:{@code notifyWebhook} / {@code notifyKey} 传回当前掩码时视为「未修改」;
 * 传空串表示清空;传新值就覆盖。
 */
@Schema(description = "更新设置")
public record SettingUpsertDTO(
        @Schema(description = "每日任务题量 1—200", example = "10") Integer dailyQuestionCount,
        @Schema(description = "提醒通道:WECOM / SERVERCHAN", example = "WECOM") String notifyChannel,
        @Schema(description = "企业微信 Webhook;等于当前掩码表示不改") String notifyWebhook,
        @Schema(description = "Server酱 SendKey;等于当前掩码表示不改") String notifyKey,
        @Schema(description = "早间提醒开关") Boolean reminderEnabled,
        @Schema(description = "早间提醒时间,HH:mm", example = "07:00") LocalTime reminderTime,
        @Schema(description = "晚间未打卡提醒开关") Boolean eveningReminderEnabled,
        @Schema(description = "晚间提醒时间,HH:mm", example = "21:30") LocalTime eveningReminderTime,
        @Schema(description = "外观:AUTO / LIGHT / DARK", example = "AUTO") String darkMode
) {
}
