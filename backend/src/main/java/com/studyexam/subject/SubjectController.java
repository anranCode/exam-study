package com.studyexam.subject;

import com.studyexam.chapter.dto.ChapterVO;
import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.Result;
import com.studyexam.subject.dto.SubjectUpsertDTO;
import com.studyexam.subject.dto.SubjectVO;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/subjects")
@Tag(name = "科目", description = "科目与章节")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    @Operation(summary = "科目列表", description = "含题目数量、到期复习数量、已掌握数量。按 sortOrder 升序。")
    @ApiResultResponses
    public Result<List<SubjectVO>> list() {
        return Result.ok(subjectService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "科目详情")
    @ApiResultResponses
    public Result<SubjectVO> get(@PathVariable Long id) {
        return Result.ok(subjectService.get(id));
    }

    @PostMapping
    @Operation(summary = "新建科目")
    @ApiResultResponses
    public Result<SubjectVO> create(@Valid @RequestBody SubjectUpsertDTO dto) {
        return Result.ok(subjectService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改科目")
    @ApiResultResponses
    public Result<SubjectVO> update(@PathVariable Long id, @Valid @RequestBody SubjectUpsertDTO dto) {
        return Result.ok(subjectService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除科目", description = "科目下还有题目时返回 code = 2005,需先清空。")
    @ApiResultResponses
    public Result<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/chapters")
    @Operation(summary = "章节列表", description = "返回该科目下的扁平章节列表(已按 sortOrder 排序),树形结构由前端组装。")
    @ApiResultResponses
    public Result<List<ChapterVO>> chapters(@PathVariable Long id) {
        return Result.ok(subjectService.listChapters(id));
    }
}