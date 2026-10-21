package com.studyexam.question;

import com.studyexam.ai.AiAnalysisService;
import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.common.PageResult;
import com.studyexam.common.Result;
import com.studyexam.question.dto.AiAnalysisRequest;
import com.studyexam.question.dto.AiAnalysisVO;
import com.studyexam.question.dto.ImportResult;
import com.studyexam.question.dto.QuestionBatchDTO;
import com.studyexam.question.dto.QuestionDetailVO;
import com.studyexam.question.dto.QuestionImportRequest;
import com.studyexam.question.dto.QuestionUpsertDTO;
import com.studyexam.question.dto.TypeCountVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
@Tag(name = "题库", description = "题目增删改查")
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionImportService importService;
    private final AiAnalysisService aiAnalysisService;

    public QuestionController(QuestionService questionService, QuestionImportService importService,
                              AiAnalysisService aiAnalysisService) {
        this.questionService = questionService;
        this.importService = importService;
        this.aiAnalysisService = aiAnalysisService;
    }

    @GetMapping
    @Operation(summary = "题目列表", description = "全部筛选参数可选。keyword 走题干全文索引,索引不可用时自动退回 LIKE。")
    @ApiResultResponses
    public Result<PageResult<QuestionDetailVO>> list(
            @Parameter(description = "科目 ID") @RequestParam(required = false) Long subjectId,
            @Parameter(description = "章节 ID") @RequestParam(required = false) Long chapterId,
            @Parameter(description = "题型") @RequestParam(required = false) String type,
            @Parameter(description = "题干关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "真题年份") @RequestParam(required = false) Integer sourceYear,
            @Parameter(description = "状态 DRAFT / PUBLISHED") @RequestParam(required = false) String status,
            @Parameter(description = "页码,从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数,最大 200") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序,如 createdAt,desc") @RequestParam(required = false) String sort) {
        QuestionQuery query = new QuestionQuery(subjectId, chapterId, type, keyword, sourceYear, status);
        return Result.ok(questionService.list(query, page, size, sort));
    }

    @GetMapping("/type-counts")
    @Operation(summary = "各题型的可练习题数",
            description = "只数「已发布」的题 —— 待校对的题不进抽题池,列出来只会让用户选了却抽不到。"
                    + "给「自由练习」选题型用。")
    @ApiResultResponses
    public Result<List<TypeCountVO>> typeCounts(
            @Parameter(description = "科目 ID,不传表示全部科目") @RequestParam(required = false) Long subjectId) {
        return Result.ok(questionService.typeCounts(subjectId));
    }

    @GetMapping("/random")
    @Operation(summary = "随机取题", description = "仅调试用;正式练习请走 /practice/sessions。")
    @ApiResultResponses
    public Result<List<QuestionDetailVO>> random(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "5") int count) {
        return Result.ok(questionService.random(subjectId, chapterId, count));
    }

    @GetMapping("/{id}")
    @Operation(summary = "题目详情", description = "含答案与解析,供题库管理与编辑页使用。")
    @ApiResultResponses
    public Result<QuestionDetailVO> get(@PathVariable Long id) {
        return Result.ok(questionService.get(id));
    }

    @PostMapping
    @Operation(summary = "新建题目",
            description = "客观题必须有 options 与 answer;主观题必须有 answerPoints 且不得有 answer。"
                    + "多选题答案会被规范化为去重升序(如 DBA → ABD)。")
    @ApiResultResponses
    public Result<QuestionDetailVO> create(@Valid @RequestBody QuestionUpsertDTO dto) {
        return Result.ok(questionService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改题目")
    @ApiResultResponses
    public Result<QuestionDetailVO> update(@PathVariable Long id, @Valid @RequestBody QuestionUpsertDTO dto) {
        return Result.ok(questionService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除题目", description = "级联清理该题的答题记录与复习队列条目。")
    @ApiResultResponses
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok();
    }

    @PostMapping("/batch-publish")
    @Operation(summary = "批量入库",
            description = "把勾选的待校对题改为已入库(PUBLISHED);已是已入库的忽略。"
                    + "入库后这些题会进入练习抽题池。返回实际变更的题数。")
    @ApiResultResponses
    public Result<Integer> batchPublish(@Valid @RequestBody QuestionBatchDTO dto) {
        return Result.ok(questionService.publish(dto.ids()));
    }

    @PostMapping("/ai-analysis/preview")
    @Operation(summary = "AI 生成解析(预览)",
            description = "根据主观题的参考答案要点生成一段简洁好记的解析,**不写库**。"
                    + "供编辑页预览:生成后填进表单,保存时随题目一起落库。")
    @ApiResultResponses
    public Result<AiAnalysisVO> previewAnalysis(@Valid @RequestBody AiAnalysisRequest dto) {
        return Result.ok(new AiAnalysisVO(
                aiAnalysisService.generate(dto.type(), dto.stem(), dto.answerPoints())));
    }

    @PostMapping("/{id}/ai-analysis")
    @Operation(summary = "AI 生成解析并保存",
            description = "按这道题已有的参考答案要点生成解析并写入 analysis。"
                    + "题库列表勾选多题时由前端逐题调用。")
    @ApiResultResponses
    public Result<AiAnalysisVO> generateAnalysis(@PathVariable Long id) {
        return Result.ok(new AiAnalysisVO(aiAnalysisService.generateAndSave(id)));
    }

    // ── 批量导入 ★ ─────────────────────────────────────────
    // 文档规定同一走 POST /questions/import,按 Content-Type 区分文件上传与 JSON body。

    @PostMapping(path = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "批量导入(JSON)",
            description = "逐条容错:失败条目跳过并在 errors 里给出序号与原因;失败超过一半整体回滚返回 4001。"
                    + "带 paper 字段时同时建试卷。格式规范见接口文档第五节。")
    @ApiResultResponses
    public Result<ImportResult> importJson(@RequestBody QuestionImportRequest request) {
        return Result.ok(importService.importQuestions(request));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "批量导入(JSON 文件)",
            description = "multipart/form-data 上传 .json 文件,字段名 file。校验策略与 JSON 入口完全一致。")
    @ApiResultResponses
    public Result<ImportResult> importFile(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR, "上传的文件是空的");
        }
        QuestionImportRequest request = importService.parseJson(file.getInputStream());
        return Result.ok(importService.importQuestions(request));
    }
}