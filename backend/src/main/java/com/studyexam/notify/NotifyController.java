package com.studyexam.notify;

import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.Result;
import com.studyexam.notify.dto.NotifyLogVO;
import com.studyexam.notify.dto.NotifyTestResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notify")
@Tag(name = "提醒", description = "微信提醒通道")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @PostMapping("/test")
    @Operation(summary = "发送测试提醒",
            description = "按当前通道配置立即发一条消息。**发送失败也返回 200**,"
                    + "result.success=false 时 message 就是失败原因,直接展示给用户。")
    @ApiResultResponses
    public Result<NotifyTestResultVO> test() {
        return Result.ok(notifyService.test());
    }

    @GetMapping("/logs")
    @Operation(summary = "提醒发送历史", description = "按发送时间倒序。用于排查漏发。")
    @ApiResultResponses
    public Result<List<NotifyLogVO>> logs(
            @Parameter(description = "最近多少天,1—90,默认 7") @RequestParam(defaultValue = "7") int days) {
        return Result.ok(notifyService.logs(days));
    }
}
