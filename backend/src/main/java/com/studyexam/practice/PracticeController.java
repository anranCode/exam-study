package com.studyexam.practice;

import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.PageResult;
import com.studyexam.common.Result;
import com.studyexam.practice.dto.AnswerResultVO;
import com.studyexam.practice.dto.AnswerSubmitDTO;
import com.studyexam.practice.dto.MockTemplateVO;
import com.studyexam.practice.dto.OngoingSessionVO;
import com.studyexam.practice.dto.PracticeSessionCreateDTO;
import com.studyexam.practice.dto.PracticeSessionDetailVO;
import com.studyexam.practice.dto.PracticeSessionListItemVO;
import com.studyexam.practice.dto.PracticeSessionVO;
import com.studyexam.practice.dto.SelfScoreDTO;
import com.studyexam.practice.dto.SessionSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/practice")
@Tag(name = "练习", description = "组题、作答、判分与练习记录")
public class PracticeController {

    private final PracticeService practiceService;
    private final MockExamService mockExamService;

    public PracticeController(PracticeService practiceService, MockExamService mockExamService) {
        this.practiceService = practiceService;
        this.mockExamService = mockExamService;
    }

    @GetMapping("/mock/template")
    @Operation(summary = "模拟考试组卷模板",
            description = "从该科目最近一份真题试卷读出题型结构(题型、题数、每题分值、时长),"
                    + "并给出题库里每种题型已发布的可练习题数。没有真题可参考时 sections 为空。")
    @ApiResultResponses
    public Result<MockTemplateVO> mockTemplate(
            @Parameter(description = "科目 ID") @RequestParam Long subjectId) {
        return Result.ok(mockExamService.template(subjectId));
    }

    @PostMapping("/sessions")
    @Operation(summary = "开始练习",
            description = "按 mode 组题并创建场次。响应中的题目**不含答案与解析**;"
                    + "若已有进行中的场次,会被置为放弃并在 ongoingSessionId 里返回其 ID。")
    @ApiResultResponses
    public Result<PracticeSessionVO> createSession(@Valid @RequestBody PracticeSessionCreateDTO dto) {
        return Result.ok(practiceService.createSession(dto));
    }

    @PostMapping("/sessions/{sessionId}/answers")
    @Operation(summary = "提交单题作答",
            description = "客观题即时判分并返回正确答案与解析;主观题返回 answerPoints 供自评。"
                    + "同一场次同一题重复提交返回已有记录(幂等)。答错一律记 WRONG。")
    @ApiResultResponses
    public Result<AnswerResultVO> submitAnswer(@PathVariable Long sessionId,
                                               @Valid @RequestBody AnswerSubmitDTO dto) {
        return Result.ok(practiceService.submitAnswer(sessionId, dto));
    }

    @PostMapping("/sessions/{sessionId}/answers/{questionId}/self-score")
    @Operation(summary = "主观题自评",
            description = "selfScore 为勾选到的要点数。自评比例决定该题在复习队列里的推进方式。")
    @ApiResultResponses
    public Result<AnswerResultVO> submitSelfScore(@PathVariable Long sessionId,
                                                  @PathVariable Long questionId,
                                                  @Valid @RequestBody SelfScoreDTO dto) {
        return Result.ok(practiceService.submitSelfScore(sessionId, questionId, dto));
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @Operation(summary = "结束练习",
            description = "汇总本轮得分、正确率、用时与错题,写入 practice_session 与当天 study_log。"
                    + "对已结束的场次重复调用返回同一份汇总。")
    @ApiResultResponses
    public Result<SessionSummaryVO> finish(@PathVariable Long sessionId) {
        return Result.ok(practiceService.finish(sessionId));
    }

    @GetMapping("/sessions")
    @Operation(summary = "练习记录分页")
    @ApiResultResponses
    public Result<PageResult<PracticeSessionListItemVO>> list(
            @Parameter(description = "页码,从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数,最大 200") @RequestParam(defaultValue = "20") int size) {
        return Result.ok(practiceService.list(page, size));
    }

    @GetMapping("/sessions/ongoing")
    @Operation(summary = "未完成的场次", description = "没有进行中的场次时返回 data = null。")
    @ApiResultResponses
    public Result<OngoingSessionVO> ongoing() {
        return Result.ok(practiceService.ongoing());
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "练习记录详情", description = "含逐题作答、正确答案与解析。")
    @ApiResultResponses
    public Result<PracticeSessionDetailVO> detail(@PathVariable Long id) {
        return Result.ok(practiceService.detail(id));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "删除练习记录",
            description = "同步回滚该次练习造成的复习队列变更,并从当天学习日志里扣除其贡献。")
    @ApiResultResponses
    public Result<Void> delete(@PathVariable Long id) {
        practiceService.delete(id);
        return Result.ok();
    }
}