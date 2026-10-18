package com.studyexam.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

/**
 * 设置读取结果。对应 GET /settings。
 *
 * <p>{@code notifyWebhook} / {@code notifyKey} **只返回掩码**(如 {@code …a1b2c3}),
 * 完整值永远不出服务端。
 */
@Schema(description = "用户设置")
public record SettingVO(
        @Schema(description = "每日任务题量", example = "10") int dailyQuestionCount,
        @Schema(description = "提醒通道:WECOM / SERVERCHAN", example = "WECOM") String notifyChannel,
        @Schema(description = "企业微信 Webhook(掩码)", example = "…a1b2c3") String notifyWebhook,
        @Schema(description = "Server酱 SendKey(掩码)", example = "…d4e5f6") String notifyKey,
        @Schema(description = "是否已配置企业微信 Webhook") boolean webhookConfigured,
        @Schema(description = "是否已配置 Server酱 SendKey") boolean keyConfigured,
        @Schema(description = "早间提醒开关") boolean reminderEnabled,
        @Schema(description = "早间提醒时间", example = "07:00") LocalTime reminderTime,
        @Schema(description = "晚间未打卡提醒开关") boolean eveningReminderEnabled,
        @Schema(description = "晚间提醒时间", example = "21:30") LocalTime eveningReminderTime,
        @Schema(description = "外观:AUTO / LIGHT / DARK", example = "AUTO") String darkMode
) {
}
