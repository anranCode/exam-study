package com.studyexam.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一条提醒发送记录。对应 GET /notify/logs。 */
@Schema(description = "提醒日志")
public record NotifyLogVO(
        @Schema(description = "日志 ID", example = "12") Long id,
        @Schema(description = "日期", example = "2026-10-18") LocalDate date,
        @Schema(description = "类型:MORNING 早间 / EVENING 晚间", example = "MORNING") String type,
        @Schema(description = "类型中文名", example = "早间提醒") String typeLabel,
        @Schema(description = "发送时间", example = "2026-10-18 07:00:03") LocalDateTime sentAt,
        @Schema(description = "是否成功") boolean success,
        @Schema(description = "失败原因(成功时为空)") String failReason
) {
}
