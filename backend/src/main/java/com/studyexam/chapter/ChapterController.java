package com.studyexam.chapter;

import com.studyexam.chapter.dto.ChapterUpsertDTO;
import com.studyexam.chapter.dto.ChapterVO;
import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chapters")
@Tag(name = "章节")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "章节详情")
    @ApiResultResponses
    public Result<ChapterVO> get(@PathVariable Long id) {
        return Result.ok(chapterService.get(id));
    }

    @PostMapping
    @Operation(summary = "新建章节", description = "章节只支持两级;parentId 为空表示顶级章节。")
    @ApiResultResponses
    public Result<ChapterVO> create(@Valid @RequestBody ChapterUpsertDTO dto) {
        return Result.ok(chapterService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改章节",
            description = "parentId 字段缺省表示不修改父章节;显式传 null 表示升为顶级章节。")
    @ApiResultResponses
    public Result<ChapterVO> update(@PathVariable Long id, @Valid @RequestBody ChapterUpsertDTO dto) {
        return Result.ok(chapterService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除章节", description = "章节下还有题目或子章节时返回 code = 2006。")
    @ApiResultResponses
    public Result<Void> delete(@PathVariable Long id) {
        chapterService.delete(id);
        return Result.ok();
    }
}