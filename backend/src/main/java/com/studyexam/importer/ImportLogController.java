package com.studyexam.importer;

import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.PageResult;
import com.studyexam.common.Result;
import com.studyexam.importer.dto.ImportLogCreateDTO;
import com.studyexam.importer.dto.ImportLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/import")
@Tag(name = "导入记录", description = "每次导入漏掉了哪些题,留档备查")
public class ImportLogController {

    private final ImportLogService importLogService;

    public ImportLogController(ImportLogService importLogService) {
        this.importLogService = importLogService;
    }

    @PostMapping("/logs")
    @Operation(summary = "记录一次导入结果",
            description = "导入结束后由页面提交:条目总数、成功数、失败明细(含题干)与识别阶段的提醒。"
                    + "后续人工补录时从这里查「还差哪几道」。")
    @ApiResultResponses
    public Result<ImportLogVO> create(@RequestBody ImportLogCreateDTO dto) {
        return Result.ok(importLogService.create(dto));
    }

    @GetMapping("/logs")
    @Operation(summary = "导入记录列表", description = "按导入时间倒序。")
    @ApiResultResponses
    public Result<PageResult<ImportLogVO>> list(
            @Parameter(description = "页码,从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数,最大 100") @RequestParam(defaultValue = "20") int size) {
        return Result.ok(importLogService.list(page, size));
    }

    @DeleteMapping("/logs/{id}")
    @Operation(summary = "删除一条导入记录",
            description = "补录完成后用它清理。记录不存在时同样返回成功(删除幂等)。")
    @ApiResultResponses
    public Result<Void> delete(@Parameter(description = "记录 ID") @PathVariable Long id) {
        importLogService.delete(id);
        return Result.ok();
    }
}
