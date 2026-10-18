package com.studyexam.ai;

import com.studyexam.ai.dto.ParsePdfResultVO;
import com.studyexam.ai.pdf.ExamPaperParser;
import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.common.Result;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * AI 与 PDF 识别。
 *
 * <p>PDF 识别不直接写库:先返回结构化题目,由用户在前端预览、修正,再调用
 * {@code POST /questions/import} 入库——与批量导入的既定流程保持一致。
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "PDF 试卷识别与 AI 辅导")
public class AiController {

    private final ExamPaperParser examPaperParser;
    private final AiClient aiClient;
    private final AiDiagnostics aiDiagnostics;
    private final SubjectRepository subjectRepository;

    public AiController(ExamPaperParser examPaperParser, AiClient aiClient,
                        AiDiagnostics aiDiagnostics, SubjectRepository subjectRepository) {
        this.examPaperParser = examPaperParser;
        this.aiClient = aiClient;
        this.aiDiagnostics = aiDiagnostics;
        this.subjectRepository = subjectRepository;
    }

    @GetMapping("/health")
    @Operation(summary = "AI 配置自检",
            description = "返回当前生效的模型与是否配置了密钥(**不返回密钥本身**),用于排查「调用不通」时到底是哪一环的问题。")
    @ApiResultResponses
    public Result<Map<String, Object>> health() {
        return Result.ok(aiClient.describeConfig());
    }

    @GetMapping("/diagnose")
    @Operation(summary = "AI 连通性自检",
            description = "用最小请求依次打阿里云官方的几个入口,返回上游的原始状态码与响应体。"
                    + "用于区分「密钥无效 / 入口不对 / 模型名不存在」这三种常见故障。")
    @ApiResultResponses
    public Result<Map<String, Object>> diagnose() {
        return Result.ok(aiDiagnostics.diagnose());
    }

    @PostMapping("/parse-pdf")
    @Operation(summary = "识别 PDF 试卷",
            description = "上传试卷 PDF(扫描件也可以),逐页渲染后交给视觉模型识别成题目 JSON。"
                    + "识别不出版面答案的题目也会照常录入,标为 DRAFT 提醒核对。结果不写库。")
    @ApiResultResponses
    public Result<ParsePdfResultVO> parsePdf(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "课程代码,必填,如 15040", required = true)
            @RequestParam String subjectCode,
            @Parameter(description = "试卷名,缺省用文件名") @RequestParam(required = false) String paperName,
            @Parameter(description = "年份,缺省从试卷名里推断") @RequestParam(required = false) Integer year,
            @Parameter(description = "卷面没有答案时是否让模型代答(不确定的题仍会留空并标 DRAFT)")
            @RequestParam(defaultValue = "true") boolean generateAnswers) throws IOException {

        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR, "请选择要识别的 PDF 文件");
        }
        // 先确认科目存在:识别不写库,但结果最终要导进这个科目。等导到一半才发现科目不存在
        // 就白花一次识别的 Token,所以在这里就拦下来。
        String code = subjectCode == null ? "" : subjectCode.trim();
        Subject subject = subjectRepository.findByCode(code)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SUBJECT_NOT_FOUND,
                        "课程代码「" + code + "」在系统里不存在,请先去科目管理里建这门课"));

        String name = paperName;
        if (name == null || name.isBlank()) {
            String original = file.getOriginalFilename();
            name = original == null ? null : original.replaceAll("\\.pdf$", "");
        }
        ExamPaperParser.ParseResult result = examPaperParser.parse(
                file.getBytes(), code, subject.getName(), name, year, generateAnswers);

        return Result.ok(new ParsePdfResultVO(
                result.request().subjectCode(),
                result.request().paper(),
                result.request().items(),
                result.totalPages(), result.parsedPages(),
                result.calls(), result.tokensIn(), result.tokensOut(),
                result.durationMs(), result.warnings()));
    }
}