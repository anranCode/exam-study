package com.studyexam.setting;

import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.Result;
import com.studyexam.setting.dto.SettingUpsertDTO;
import com.studyexam.setting.dto.SettingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "设置", description = "学习设置与提醒通道")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    @Operation(summary = "读取设置",
            description = "密钥类字段(notifyWebhook / notifyKey)只返回掩码,完整值不出服务端。")
    @ApiResultResponses
    public Result<SettingVO> get() {
        return Result.ok(settingService.get());
    }

    @PutMapping
    @Operation(summary = "更新设置",
            description = "字段按「传了就改」处理。密钥传回当前掩码视为未修改,传空串表示清空。")
    @ApiResultResponses
    public Result<SettingVO> update(@RequestBody SettingUpsertDTO dto) {
        return Result.ok(settingService.update(dto));
    }
}
