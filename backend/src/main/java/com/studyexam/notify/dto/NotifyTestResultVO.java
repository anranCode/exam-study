package com.studyexam.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 测试提醒的发送结果。失败原因直接给到页面,不让用户面对「显示已开启却收不到」的状态。 */
@Schema(description = "测试提醒结果")
public record NotifyTestResultVO(
        @Schema(description = "是否发送成功") boolean success,
        @Schema(description = "成功提示或失败原因", example = "已发送,请查看微信") String message
) {
}
